"""Loopback FastAPI adapter for the pinned Step-Audio-EditX checkout."""
import asyncio
import math
import os
import shutil
import sys
import tempfile
import time
import wave
from array import array
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse

PRESETS = {
    "More expressive": ("style", "vivid"),
    "More authoritative": ("style", "authority"),
    "Warmer": ("style", "warm"),
    "Better storyteller": ("style", "story"),
    "More energetic": ("emotion", "excited"),
    "More conversational": ("style", "chat"),
    "More composed": ("style", "serious"),
}
MAX_BYTES = 8 * 1024 * 1024


class CoachError(Exception):
    def __init__(self, code, message):
        self.code = code
        super().__init__(message)


def error_data(exc, default):
    if isinstance(exc, CoachError):
        return {"code": exc.code, "message": str(exc)}
    if "out of memory" in str(exc).lower() or "OutOfMemory" in type(exc).__name__:
        return {"code": "INSUFFICIENT_GPU_MEMORY", "message": "Free GPU memory or use the AWQ profile and a shorter clip."}
    # Upstream errors can include the prompt. Keep recordings and transcripts out of diagnostics.
    return {"code": default, "message": f"{type(exc).__name__}. Check local dependencies and model paths."}


def check_wav(path, limit, code):
    try:
        with wave.open(str(path), "rb") as src:
            frames, rate = src.getnframes(), src.getframerate()
            seconds = frames / rate
            if src.getnchannels() != 1 or src.getsampwidth() != 2 or not 8000 <= rate <= 96000 or not 0 < seconds <= limit:
                raise ValueError()
            pcm = array("h")
            pcm.frombytes(src.readframes(frames))
            if len(pcm) != frames or max(map(abs, pcm), default=0) < 32:
                raise ValueError()
            return seconds
    except (wave.Error, EOFError, ValueError, ZeroDivisionError, OSError) as exc:
        raise CoachError(code, f"Expected audible mono 16-bit PCM WAV up to {limit:g} seconds.") from exc


def load_editor():
    import torch
    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported(including_emulation=False):
        raise CoachError("UNSUPPORTED_HARDWARE", "This profile requires a CUDA GPU with bfloat16 support.")
    tools = Path(__file__).resolve().parents[2] / ".voice-tools"
    source = Path(os.getenv("EDITX_SOURCE", tools / "Step-Audio-EditX")).resolve(strict=True)
    model_dir = Path(os.getenv("EDITX_MODEL", tools / "models/Step-Audio-EditX")).resolve(strict=True)
    tokenizer_dir = Path(os.getenv("EDITX_TOKENIZER", tools / "models/Step-Audio-Tokenizer")).resolve(strict=True)
    sys.path.insert(0, str(source))
    from config.edit_config import get_supported_edit_types
    from tokenizer import StepAudioTokenizer
    from tts import StepAudioTTS
    import soundfile as sf
    supported = get_supported_edit_types()
    if any(value not in supported.get(kind, []) for kind, value in PRESETS.values()):
        raise CoachError("MODEL_NOT_LOADED", "Installed EditX code has incompatible presets.")
    quant = os.environ.get("EDITX_QUANTIZATION") or None
    if quant not in (None, "awq"):
        raise ValueError("Unknown quantization profile")
    tokenizer = StepAudioTokenizer(str(tokenizer_dir), model_source="local")
    model = StepAudioTTS(str(model_dir), tokenizer, model_source="local", quantization=quant,
                         dtype="float16" if quant else "bfloat16", max_num_seqs=1, max_model_len=8192,
                         gpu_memory_utilization=float(os.getenv("EDITX_GPU_FRACTION", "0.5")),
                         enforce_eager=True, cosyvoice_dtype="bfloat16", cosyvoice_cuda_graph=False)

    def edit(input_path, output_path, text, preset):
        kind, value = PRESETS[preset]
        with torch.inference_mode():
            waveform, rate = model.edit(prompt_wav_path=str(input_path), prompt_text=text,
                                        edit_type=kind, edit_info=value)
            samples = waveform.detach().float().cpu().reshape(-1).numpy()
            import numpy as np
            if not np.isfinite(samples).all():
                raise CoachError("INVALID_OUTPUT", "Model returned non-finite audio.")
            sf.write(output_path, samples, rate, subtype="PCM_16")
    return edit


def create_app(loader=load_editor):
    jobs = {}
    pending = set()
    state = {"ready": False, "error": {"code": "MODEL_LOADING", "message": "Loading EditX…"}}

    @asynccontextmanager
    async def lifespan(app):
        with tempfile.TemporaryDirectory(prefix="wave-editx-", dir=os.getenv("EDITX_TEMP_DIR")) as tmp, \
                ThreadPoolExecutor(max_workers=1, thread_name_prefix="editx") as worker:
            app.state.root = Path(tmp)
            app.state.worker = worker
            try:
                app.state.edit = await asyncio.get_running_loop().run_in_executor(worker, loader)
                state.update(ready=True, error=None)
            except Exception as exc:
                state.update(ready=False, error=error_data(exc, "MODEL_NOT_LOADED"))
            yield
            if pending:
                await asyncio.gather(*pending, return_exceptions=True)
        jobs.clear()

    app = FastAPI(lifespan=lifespan)

    @app.middleware("http")
    async def size_guard(request: Request, call_next):
        if request.method == "POST" and request.url.path == "/v1/edit":
            length = request.headers.get("content-length", "")
            if not length.isdecimal() or len(length) > 12 or int(length) > MAX_BYTES + 65536:
                return JSONResponse({"detail": "Missing or oversized Content-Length"}, status_code=413)
        return await call_next(request)

    @app.get("/v1/health")
    async def health():
        return state

    @app.get("/v1/ready")
    async def ready():
        if not state["ready"]:
            raise HTTPException(503, state["error"])
        return {"ready": True}

    def expire():
        # Sweep on access. Closing the service also removes every job.
        for key, job in list(jobs.items()):
            if job.get("expires", math.inf) < time.monotonic():
                shutil.rmtree(app.state.root / key)
                del jobs[key]

    def run_edit(folder, text, preset, iterations, seconds):
        source = folder / "input.wav"
        for index in range(iterations):
            output = folder / ("reference.wav" if index == iterations - 1 else "pass1.wav")
            app.state.edit(source, output, text, preset)
            duration = check_wav(output, seconds * 3 + 1, "INVALID_OUTPUT")
            if duration < seconds * .25:
                raise CoachError("INVALID_OUTPUT", "Generated reference is implausibly short.")
            if index + 1 < iterations and duration > 30:
                raise CoachError("INVALID_OUTPUT", "First pass exceeds the 30-second limit for a second pass.")
            source = output

    # Operations in the event loop own the job table. Only inference touches the GPU executor.
    gpu = asyncio.Lock()

    async def process(key, text, preset, iterations, seconds):
        job, folder = jobs[key], app.state.root / key
        try:
            async with gpu:
                job["status"] = "running"
                await asyncio.get_running_loop().run_in_executor(app.state.worker, run_edit, folder, text, preset, iterations, seconds)
                job["status"] = "succeeded"
        except Exception as exc:
            job.update(status="failed", error=error_data(exc, "INFERENCE_FAILED"))
        finally:
            (folder / "input.wav").unlink(missing_ok=True)
            (folder / "pass1.wav").unlink(missing_ok=True)
            job["expires"] = time.monotonic() + 3600

    @app.post("/v1/edit", status_code=202)
    async def submit(audio: UploadFile = File(), transcript: str = Form(), preset: str = Form(), iterations: int = Form(1)):
        await ready()
        expire()
        if preset not in PRESETS or iterations not in (1, 2) or not 0 < len(transcript.strip()) <= 4096:
            raise HTTPException(422, "Invalid preset, iterations or transcript.")
        if len(pending) >= 4:
            raise HTTPException(429, "GPU queue is full. Retry later.")
        key = uuid4().hex
        folder = app.state.root / key
        folder.mkdir(mode=0o700)
        try:
            # Multipart parser has already spooled the bounded upload. Ignore the supplied filename.
            with (folder / "input.wav").open("xb") as dest:
                shutil.copyfileobj(audio.file, dest)
            if (folder / "input.wav").stat().st_size > MAX_BYTES:
                raise HTTPException(413, "Audio exceeds the upload limit.")
            seconds = check_wav(folder / "input.wav", 30, "INVALID_AUDIO")
        except (CoachError, HTTPException, OSError) as exc:
            shutil.rmtree(folder)
            if isinstance(exc, CoachError):
                raise HTTPException(422, error_data(exc, "INVALID_AUDIO")) from exc
            raise
        jobs[key] = {"status": "queued"}
        task = asyncio.create_task(process(key, transcript.strip(), preset, iterations, seconds))
        pending.add(task)
        task.add_done_callback(pending.discard)
        return {"jobId": key, "status": "queued"}

    @app.get("/v1/jobs/{key}")
    async def status(key: str):
        expire()
        if key not in jobs:
            raise HTTPException(404, "Unknown or expired job.")
        return {k: v for k, v in jobs[key].items() if k != "expires"}

    @app.get("/v1/jobs/{key}/audio")
    async def audio_file(key: str):
        if (await status(key))["status"] != "succeeded":
            raise HTTPException(409, "Audio is not available.")
        return FileResponse(app.state.root / key / "reference.wav", media_type="audio/wav", filename="AI-enhanced.wav")
    return app


app = create_app()
