# Voice Impact Coach

WaveStudio analyzes one saved take at a time. Choose the clearest microphone when several captured the same speaker. The English analysis uses iamspeaker, local Whisper.cpp and FFmpeg. It works independently of the NVIDIA editor. Run the commands below from the WaveStudio project root.

## Start speech analysis

On the machine where this feature was developed, the English model, CPU executable and patched iamspeaker checkout are already installed under `.voice-tools/`. Start the service in one terminal and the desktop application in another.

```bash
./integrations/iamspeaker/start.sh
```

```bash
./gradlew run
```

For a fresh Linux installation, install Git, curl, CMake, a C/C++ compiler, FFmpeg, Node 22.13+ and pnpm (upstream pins 11.6.0). Run the setup command once. It builds a static Whisper CLI and verifies the 142 MiB English base model by SHA-256. JDK 25 is required for WaveStudio.

```bash
./integrations/setup.sh speaker
```

Check the service with `curl -f http://127.0.0.1:3000/api/sessions`. Stop it with Ctrl+C in its terminal. The service uses local speech recognition and heuristic pronunciation flags. Slide generation, Ollama and Piper voice downloads are unnecessary for this workflow.

**Docker option.** With Docker Compose installed, use the same patched upstream application, its Dockerfile and a loopback-only port. The native service and container use the same default port. The Docker setup has its own data volume.

```bash
./integrations/setup.sh speaker-docker
docker compose -f integrations/iamspeaker/compose.yaml up --build -d
docker compose -f integrations/iamspeaker/compose.yaml down
```

## Interpret and compare a recording

1. Open the folder containing your recordings. Click **Voice Impact Coach…** in the Recordings panel and select a file.
2. Click **Analyze delivery**. The report measures WPM over the complete selection, counts silences of at least 0.6 seconds at the upstream −30 dB threshold, and shows filler counts, pitch range and energy range. Pitch is withheld when voiced evidence is sparse. Word timings and pronunciation flags are Whisper estimates. The practice prompts are based on these measurements.
3. To edit up to 30 seconds, analyze the entire short recording or enable **Analyze a section**, set its start and length, and analyze again. This transcribes the exact excerpt. In **Transcript · editable**, correct any recognition errors before sending it to EditX.
4. Choose a preset and one or two passes. **Generate enhanced reference** processes the clip locally. The second pass, if selected, takes the first pass as input. A failed job can be retried with another preset or shorter clip.
5. **Compare recordings** shows both transcripts, the measured changes and the enhanced report. Any word difference after ignoring case and punctuation is flagged. Recognition can make mistakes in either take. Listen to check the words, the speaker's identity and which phrasing you prefer.
6. Use WaveStudio's selected output device for **Play original**, **Play reference**, **Pause** and **Replay**. **Export WAV…** saves a copy to a new filename. Closing the dialog stops local polling and preview playback. A submitted backend job may finish after the dialog closes.

Pitch range is the 10th-to-90th percentile span in semitones. Energy range uses the 25th and 90th percentile frame energies. Recordings made with different mic gains, background noise or automatic gain control can give different measurements.

## Start Step-Audio-EditX on the GPU computer

Use 64-bit x86 Linux, Python 3.12, uv, FFmpeg with shared libraries for TorchCodec, and an NVIDIA driver compatible with the pinned CUDA-enabled PyTorch 2.9.1 and vLLM wheel. This profile requires native BF16 support. Upstream describes 12 GB as marginal and 16 GB as safer for inference. Training memory depends on clip length, precision and the parameters updated. [Step-Audio-EditX hardware and installation](https://github.com/stepfun-ai/Step-Audio-EditX/tree/a652e87052c109e26f616d60971376ff47a829d4#model-usage)

```bash
./integrations/setup.sh editx
./integrations/step-audio-editx/start.sh
```

Setup explicitly downloads the full EditX checkpoint and its separate audio tokenizer. The Java app never downloads weights. The service loads both models once, accepts up to four queued/running requests, and executes one GPU job at a time. Stop it with Ctrl+C.

```bash
curl -s http://127.0.0.1:7861/v1/health
curl -f http://127.0.0.1:7861/v1/ready
```

A model-loading failure leaves the service available for diagnostics. The readiness endpoint returns HTTP 503 with an error code. Correct the setup, restart the service and click **Check EditX** in WaveStudio.

**Lower-memory inference.** Install the publisher's AWQ weights and point the editor to that specific checkpoint. The memory fraction controls vLLM's share. The tokenizer and vocoder need additional memory.

```bash
./integrations/setup.sh editx-awq
EDITX_MODEL="$PWD/.voice-tools/models/Step-Audio-EditX-AWQ-4bit" \
EDITX_QUANTIZATION=awq EDITX_GPU_FRACTION=0.40 ./integrations/step-audio-editx/start.sh
```

**GPU on another computer.** Keep the service bound to its loopback address and forward the port from the desktop over authenticated SSH. Substitute the actual SSH account and host.

```bash
ssh -N -L 127.0.0.1:7861:127.0.0.1:7861 user@gpu-host
```

## Fine-tune on one 20 GB GPU

The pretrained model can generate references immediately. To teach it your preferred delivery, record pairs of short takes from the same speaker saying the same words. One take supplies the original delivery and the other demonstrates the chosen style. Reserve separate pairs for listening comparisons after training. Different microphones capturing a single performance do not provide a change in delivery.

The included supervised runner uses a 4-bit NF4 base, rank-8 LoRA on the attention query and value projections, BF16 computation, batch size one and four accumulation steps. The default sequence limit is 768 tokens, including both clips and the instruction. Start with clips of 3–5 seconds. Long examples produce a length error before training. This is a single-GPU starting profile. Actual peak memory and training quality still need measurement on your card.

1. Run `./integrations/setup.sh editx` on the GPU machine. Stop the running EditX server before training to release its GPU allocation.
2. Create `training/pairs.jsonl` with one object per line. Audio paths are relative to the manifest. Use one of the seven exact UI preset names. For example, put the two audio files next to the manifest and use this line.

```json
{"source":"take-plain.wav","target":"take-expressive.wav","transcript":"We can start with one small step.","preset":"More expressive"}
```

3. Extract audio tokens, then run one optimization step to check memory. Each stage takes a new output path. The preparation and training commands run in separate processes so the audio tokenizer releases its GPU memory before training begins.

```bash
gpu_python="$PWD/.voice-tools/Step-Audio-EditX/.venv/bin/python"
trainer="$PWD/integrations/step-audio-editx/train.py"
"$gpu_python" "$trainer" prepare --data training/pairs.jsonl --output training/tokens.jsonl
"$gpu_python" "$trainer" train --data training/tokens.jsonl --output training/smoke-adapter --steps 1
```

4. The runner prints peak allocated PyTorch GPU memory. For an out-of-memory error, use shorter paired clips and regenerate the token index. Once the smoke step succeeds, train an adapter for one epoch, merge it with the full-precision base on CPU, and start the editor with the merged directory. Merging needs host RAM for the base model and disk space for a separate checkpoint including the vocoder.

```bash
"$gpu_python" "$trainer" train --data training/tokens.jsonl --output training/delivery-adapter
"$gpu_python" "$trainer" merge --adapter training/delivery-adapter --output training/merged-editx
EDITX_MODEL="$PWD/training/merged-editx" ./integrations/step-audio-editx/start.sh
```

Only target audio tokens and the end-of-turn token contribute to the training loss. The data preparer reuses the upstream tokenizer, dataset prompt formatting and model chat template. The training path starts from the pinned standard checkpoint. The AWQ checkpoint is for inference. [Upstream training formats](https://github.com/stepfun-ai/Step-Audio-EditX/blob/a652e87052c109e26f616d60971376ff47a829d4/scripts/ReadMe.md)

## Configuration and storage

Copy `coach.properties.example` to `coach.properties` in the directory where WaveStudio runs to change service addresses, FFmpeg path, upload size, clip limit, polling, timeout and output directory. For an installed distribution, `JAVA_OPTS=-Dwave.coach.config=/absolute/path/to/coach.properties` selects another file. Defaults analyze the full file up to a 50 MiB prepared upload (about 27 minutes of 16 kHz mono PCM). For larger files, raise both `upload.mb` and the iamspeaker process's `MAX_UPLOAD_MB`, and allow a longer processing timeout.

The startup scripts accept `WHISPER_BIN`, `WHISPER_MODEL_PATH`, `EDITX_SOURCE`, `EDITX_MODEL`, `EDITX_TOKENIZER`, `EDITX_TEMP_DIR`, `EDITX_GPU_FRACTION` and `LOG_LEVEL` for their respective services. Use `warn` for iamspeaker and `warning` for uvicorn. The Java client accepts loopback HTTP addresses and bypasses system HTTP proxies.

- WaveStudio always reads the original file. The coach's temporary mono PCM copies belong to the dialog and are removed on close. The 16 kHz analysis copy and the 48 kHz editing copy are converted with FFmpeg.
- Each successful enhancement is published as `.voice-coach/<unique-id>/AI-enhanced.wav` beside the source. Its `comparison.json` includes the selection, transcripts, word timings, service identifiers, preset and measurements. Remove that result directory to delete the reference. The original recording can be managed separately in its library folder.
- iamspeaker retains its uploaded audio and transcripts under `.voice-tools/iam-data` (Docker uses `iam-docker-data`). Its local session dashboard supports deletion. The API also accepts `DELETE /api/sessions/{sessionId}` and removes the session's audio and database entries. Use the session ID from the comparison report or local session list.
- EditX keeps temporary files in a private per-process directory. Input and intermediate audio are removed at job completion. Finished jobs expire after one hour on subsequent API access, and graceful shutdown removes the whole temporary directory. Use recordings you own or have permission to edit.

## Verification

```bash
./gradlew build
./gradlew audioIntegrationTest uiTest
```

To exercise real transcription through both the Java service and the Swing dialog, start iamspeaker and supply a speech WAV. Whisper.cpp includes a public-domain sample. If using the preinstalled runtime without a Whisper source checkout, substitute another short WAV containing clear speech.

```bash
WAVE_SPEECH_SAMPLE="$PWD/.voice-tools/whisper.cpp/samples/jfk.wav" ./gradlew test uiTest --rerun-tasks
```

To test the Python wrapper on CPU, use a separate environment. These tests inject an editor only in the test harness.

```bash
python3.12 -m venv /tmp/wave-editor-tests
/tmp/wave-editor-tests/bin/pip install fastapi==0.128.0 python-multipart==0.0.21 httpx==0.28.1
(cd integrations/step-audio-editx && /tmp/wave-editor-tests/bin/python -m unittest -v test_service)
```

The optional real GPU test uses the installed GPU environment, a short mono PCM WAV and its matching transcript. Stop the existing GPU server first to free memory.

```bash
gpu_python="$PWD/.voice-tools/Step-Audio-EditX/.venv/bin/python"
(cd integrations/step-audio-editx && \
 EDITX_GPU_SAMPLE=/absolute/path/to/clip.wav EDITX_GPU_TRANSCRIPT="Words in the clip." \
 "$gpu_python" -m unittest -v test_service.EditorTests.test_real_gpu)
```

The CPU speech path, desktop workflow and local audio regressions have been exercised. GPU inference, adapter training, merged-checkpoint playback and peak VRAM need a real compatible GPU. The optional Compose file has been configuration-checked.

## Integration contracts and source

The Java code is in `src/main/java/studio/wave/coach/`, with the Swing dialog in `ui/coach/`. `iamspeaker/voice-only.patch` adds a voice-only session marker, saves the transcript alongside the upload, and returns transcript and prosody from the analysis endpoint. It also enables timestamp decoding in Whisper. The client creates a neutral session, posts multipart audio with script version 0 and no transitions, polls the job to success and retrieves its typed result.

The local FastAPI adapter exposes `GET /v1/health`, `GET /v1/ready`, `POST /v1/edit`, `GET /v1/jobs/{id}` and `GET /v1/jobs/{id}/audio`. The edit request takes multipart fields `audio`, `transcript`, `preset` and `iterations`. The worker validates each preset against the installed upstream configuration and calls the resident `StepAudioTTS.edit` with the uploaded audio and corrected transcript. Startup and inference failures are visible through structured responses.

Setup pins the upstream commits and model revisions. WaveStudio's source distribution contains the adapters and patch. Model weights and upstream installations stay in the ignored local tools directory. iamspeaker uses MIT and the EditX source uses Apache-2.0. Review the separately downloaded model and component terms before redistributing a bundled runtime. [iamspeaker source](https://github.com/spark798/iamspeaker/tree/5be74a21457ae2c556058bde57084af8ce5d5079), [EditX source](https://github.com/stepfun-ai/Step-Audio-EditX/tree/a652e87052c109e26f616d60971376ff47a829d4), [model weights](https://huggingface.co/stepfun-ai/Step-Audio-EditX/tree/5fe2f8a05c2353301ad47d3c1747b262115da138), [audio tokenizer](https://huggingface.co/stepfun-ai/Step-Audio-Tokenizer/tree/af7e5a3ec06175a7facae9d4100073d6e4dbb36c).
