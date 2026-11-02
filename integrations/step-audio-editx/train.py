"""Prepare paired takes, fine-tune a small EditX adapter, and merge it for vLLM inference."""
import argparse
import json
import math
import os
import shutil
import sys
import tempfile
from pathlib import Path

from service import PRESETS


def read_rows(path):
    with path.open(encoding="utf-8") as stream:
        for line_num, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
                if not isinstance(row, dict):
                    raise ValueError("expected an object")
                yield row
            except ValueError as exc:
                raise ValueError(f"{path.name} line {line_num}: invalid JSON object") from exc


def prepare(args):
    import torch
    import torchaudio
    from tokenizer import StepAudioTokenizer

    tokenizer = StepAudioTokenizer(str(args.tokenizer.resolve(strict=True)), model_source="local")

    def tokens(filename):
        samples, rate = torchaudio.load(args.data.parent / filename)
        if not 0 < samples.shape[-1] / rate <= 30 or not torch.isfinite(samples).all() or samples.abs().max() < .001:
            raise ValueError(f"Invalid or silent training clip: {filename}")
        codes, _, _ = tokenizer.wav2token(samples.mean(dim=0, keepdim=True), rate)
        if not codes:
            raise ValueError(f"No audio tokens extracted from {filename}")
        return codes

    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        prepared = Path(tmp) / "tokens.jsonl"
        with prepared.open("w", encoding="utf-8") as out, torch.inference_mode():
            for pair in read_rows(args.data):
                text = pair["transcript"].strip()
                kind, value = PRESETS[pair["preset"]]
                if not text or (args.data.parent / pair["source"]).samefile(args.data.parent / pair["target"]):
                    raise ValueError("Each pair needs two distinct takes of the same words and a transcript.")
                example = dict(source_audio=str((args.data.parent / pair["source"]).resolve()), source_text=text,
                               target_text=text, source_vq02vq06=tokens(pair["source"]), target_vq02vq06=tokens(pair["target"]),
                               task_type="edit", edit_type=kind, edit_info=value)
                out.write(json.dumps(example, ensure_ascii=False) + "\n")
        if not prepared.stat().st_size:
            raise ValueError("No training pairs")
        os.link(prepared, args.output)  # publish a complete file, never replace an existing index


def encode(example, formatter, tokenizer, max_length):
    messages = formatter.process_item_raw(example)
    prompt = tokenizer.apply_chat_template(messages["prompt"], tokenize=True, add_generation_prompt=True)
    target = example["target_vq02vq06"]
    if not target or any(type(t) is not int or t < 65536 or t >= len(tokenizer) for t in target):
        raise ValueError("Invalid target audio token sequence")
    # EditX ends an assistant turn with <|EOT|> (3). Its Llama tokenizer's </s> is a different token (2).
    end_turn = tokenizer.convert_tokens_to_ids("<|EOT|>")
    if end_turn != 3:
        raise ValueError("Incompatible EditX tokenizer: unexpected end-of-turn token")
    sequence = prompt + target + [end_turn]
    if len(sequence) > max_length:
        raise ValueError(f"Pair requires {len(sequence)} tokens; shorten both clips or increase --max-length.")
    return {"input_ids": sequence, "labels": [-100] * len(prompt) + sequence[len(prompt):]}


def train(args):
    import torch
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig, Trainer, TrainingArguments
    from dataset.edit_sftdataset import EditSFTDataset

    if not torch.cuda.is_available() or not torch.cuda.is_bf16_supported(including_emulation=False):
        raise RuntimeError("Training requires a BF16-capable NVIDIA GPU")
    base = str(args.model.resolve(strict=True))
    tokenizer = AutoTokenizer.from_pretrained(base, trust_remote_code=True, local_files_only=True)
    formatter = EditSFTDataset([], processing_class=tokenizer, lazy=True)
    dataset = [encode(row, formatter, tokenizer, args.max_length) for row in read_rows(args.data)]
    if not dataset:
        raise ValueError("Training index contains no examples")
    model = AutoModelForCausalLM.from_pretrained(
        base, trust_remote_code=True, local_files_only=True, dtype=torch.bfloat16, device_map={"": 0},
        quantization_config=BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                                              bnb_4bit_use_double_quant=True, bnb_4bit_compute_dtype=torch.bfloat16))
    model.config.use_cache = False
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=False)
    model = get_peft_model(model, LoraConfig(r=8, lora_alpha=16, lora_dropout=.05,
                                             target_modules=["q_proj", "v_proj"], task_type="CAUSAL_LM"))
    model.print_trainable_parameters()
    options = TrainingArguments(output_dir=str(args.output), per_device_train_batch_size=1, gradient_accumulation_steps=4,
                                num_train_epochs=1, max_steps=args.steps, learning_rate=1e-4, bf16=True, report_to="none",
                                logging_steps=1, logging_nan_inf_filter=False, save_strategy="no", remove_unused_columns=False, seed=42)
    # Each batch contains exactly one variable-length example, so it requires no padding.
    trainer = Trainer(model=model, args=options, train_dataset=dataset)
    result = trainer.train()
    if not math.isfinite(result.training_loss) or result.training_loss <= 0:
        raise RuntimeError("Training did not produce a finite positive loss; adapter was not published.")
    trainer.save_model(str(args.output))
    print(f"Peak allocated GPU memory: {torch.cuda.max_memory_allocated() / 2**30:.2f} GiB")


def merge(args):
    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM

    base = args.model.resolve(strict=True)
    model = AutoModelForCausalLM.from_pretrained(base, trust_remote_code=True, local_files_only=True, dtype=torch.bfloat16)
    merged = PeftModel.from_pretrained(model, str(args.adapter.resolve(strict=True))).merge_and_unload(safe_merge=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as tmp:
        dest = Path(tmp) / "merged"
        # Preserve the tokenizer, custom model code, config and vocoder required by the inference service.
        shutil.copytree(base, dest, ignore=lambda path, names: {n for n in names if n in {".git", ".cache"}
                        or (Path(path) == base and (n.endswith(".safetensors") or n.endswith(".safetensors.index.json")))})
        merged.save_pretrained(dest, safe_serialization=True)
        dest.rename(args.output)


def main():
    root = Path(__file__).resolve().parents[2] / ".voice-tools"
    source = Path(os.environ.get("EDITX_SOURCE", root / "Step-Audio-EditX")).resolve()
    sys.path[:0] = [str(source), str(source / "src")]
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("prepare", "train", "merge"):
        cmd = commands.add_parser(name)
        cmd.add_argument("--output", type=Path, required=True)
        if name != "merge":
            cmd.add_argument("--data", type=Path, required=True)
        if name != "prepare":
            cmd.add_argument("--model", type=Path, default=root / "models/Step-Audio-EditX")
        if name == "prepare":
            cmd.add_argument("--tokenizer", type=Path, default=root / "models/Step-Audio-Tokenizer")
        elif name == "train":
            cmd.add_argument("--max-length", type=int, default=768)
            cmd.add_argument("--steps", type=int, default=-1, help="Use 1 for the first GPU memory check.")
        else:
            cmd.add_argument("--adapter", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output path.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    {"prepare": prepare, "train": train, "merge": merge}[args.command](args)


if __name__ == "__main__":
    main()
