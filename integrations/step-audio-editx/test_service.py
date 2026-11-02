import io
import os
import shutil
import tempfile
import threading
import time
import unittest
import wave
from pathlib import Path
from fastapi.testclient import TestClient

from service import CoachError, PRESETS, check_wav, create_app
from train import encode, read_rows


def recording(seconds=.1, loud=True):
    out = io.BytesIO()
    with wave.open(out, "wb") as wav:
        wav.setparams((1, 2, 16000, 0, "NONE", ""))
        wav.writeframes((b"\x40\x10\xc0\xef" if loud else b"\0" * 4) * int(8000 * seconds))
    return out.getvalue()


def submit(client, data=None, text="Hello", style="More expressive", iterations=1):
    return client.post("/v1/edit", files={"audio": ("../../x.wav", data if data is not None else recording(), "audio/wav")},
                       data={"transcript": text, "preset": style, "iterations": iterations})


def wait_job(client, job, seconds=5):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        response = client.get("/v1/jobs/" + job).json()
        if response["status"] in {"succeeded", "failed"}:
            return response
        time.sleep(.02)
    raise AssertionError("Timed out waiting for job")


class EditorTests(unittest.TestCase):
    def test_queue_is_serial_and_each_job_has_its_own_files(self):
        release, entered = threading.Event(), threading.Event()
        calls = []

        def backend(src, dest, text, preset):
            calls.append((src, dest, text, preset))
            entered.set()
            release.wait(4)
            shutil.copyfile(src, dest)

        with TestClient(create_app(lambda: backend)) as client:
            self.assertTrue(client.get("/v1/health").json()["ready"])
            a = submit(client, iterations=2).json()["jobId"]
            self.assertTrue(entered.wait(2))
            b = submit(client, style="Warmer").json()["jobId"]
            self.assertEqual(client.get("/v1/jobs/" + b).json()["status"], "queued")
            self.assertEqual(client.get("/v1/jobs/" + b + "/audio").status_code, 409)
            release.set()
            for key in (a, b):
                self.assertEqual(wait_job(client, key)["status"], "succeeded")
                self.assertEqual(client.get("/v1/jobs/" + key + "/audio").content, recording())
            self.assertNotEqual(calls[0][1].parent, calls[2][1].parent)
            self.assertEqual(calls[1][0], calls[0][1])
        self.assertFalse(calls[0][1].parent.exists())

    def test_bad_input_is_rejected_before_inference(self):
        with TestClient(create_app(lambda: None)) as client:
            for kwargs in (dict(data=b""), dict(data=b"garbage"), dict(data=recording(31)),
                           dict(data=recording(loud=False)), dict(text="  "), dict(style="invented"), dict(iterations=3)):
                self.assertEqual(submit(client, **kwargs).status_code, 422)
            self.assertEqual(client.get("/v1/jobs/unknown").status_code, 404)

    def test_load_failure_and_inference_errors(self):
        def no_gpu():
            raise CoachError("UNSUPPORTED_HARDWARE", "No CUDA GPU")

        with TestClient(create_app(no_gpu)) as client:
            self.assertFalse(client.get("/v1/health").json()["ready"])
            self.assertEqual(client.get("/v1/ready").json()["detail"]["code"], "UNSUPPORTED_HARDWARE")
            self.assertEqual(submit(client).status_code, 503)

        def oom(*unused):
            raise RuntimeError("CUDA out of memory")
        with TestClient(create_app(lambda: oom)) as client:
            job = submit(client).json()["jobId"]
            self.assertEqual(wait_job(client, job)["error"]["code"], "INSUFFICIENT_GPU_MEMORY")
            self.assertEqual(client.get("/v1/jobs/" + job + "/audio").status_code, 409)

    def test_all_presets_match_pinned_upstream_choices(self):
        self.assertEqual(list(PRESETS.values()), [("style", "vivid"), ("style", "authority"), ("style", "warm"),
                                                 ("style", "story"), ("emotion", "excited"), ("style", "chat"), ("style", "serious")])

    @unittest.skipUnless(os.environ.get("EDITX_GPU_SAMPLE"), "Set EDITX_GPU_SAMPLE to exercise the installed GPU and weights")
    def test_real_gpu(self):
        path = Path(os.environ["EDITX_GPU_SAMPLE"])
        with TestClient(create_app()) as client:
            ready = client.get("/v1/ready")
            self.assertEqual(ready.status_code, 200, ready.text)
            job = submit(client, path.read_bytes(), text=os.environ["EDITX_GPU_TRANSCRIPT"]).json()["jobId"]
            self.assertEqual(wait_job(client, job, 600)["status"], "succeeded")
            with tempfile.TemporaryDirectory() as folder:
                result = Path(folder) / "generated.wav"
                result.write_bytes(client.get("/v1/jobs/" + job + "/audio").content)
                self.assertGreater(check_wav(result, 91, "INVALID_OUTPUT"), 0)


class TrainingDataTests(unittest.TestCase):
    def test_only_completion_contributes_to_loss(self):
        class Tokenizer:
            eos_token_id = 2
            def convert_tokens_to_ids(self, token):
                assert token == "<|EOT|>"
                return 3
            def __len__(self): return 74752
            def apply_chat_template(self, messages, **options):
                assert options["add_generation_prompt"]
                return [9, 10]
        class Formatter:
            def process_item_raw(self, example): return {"prompt": []}
        row = {"target_vq02vq06": [65536, 67000]}
        data = encode(row, Formatter(), Tokenizer(), 5)
        self.assertEqual(data["labels"], [-100, -100, 65536, 67000, 3])
        self.assertEqual(data["input_ids"], [9, 10, 65536, 67000, 3])
        with self.assertRaisesRegex(ValueError, "shorten"):
            encode(row, Formatter(), Tokenizer(), 4)

    def test_invalid_manifest_has_a_line_number(self):
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "pairs.jsonl"
            source.write_text('{}\nnot JSON')
            with self.assertRaisesRegex(ValueError, "line 2"):
                list(read_rows(source))


if __name__ == "__main__":
    unittest.main()
