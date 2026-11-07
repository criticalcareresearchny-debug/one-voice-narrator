#!/usr/bin/env python3
"""One Voice cloud worker: one authenticated GitHub Actions request -> one MP3.

Security: this runner is intended for controlled personal use, not a public TTS API.
Do not submit confidential scripts through the public repository's workflow inputs.
"""
from __future__ import annotations
import os
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro_onnx import Kokoro
from imageio_ffmpeg import get_ffmpeg_exe

VOICES = {
    "af_heart": "en-us", "af_bella": "en-us",
    "am_michael": "en-us", "am_fenrir": "en-us",
    "bf_emma": "en-gb", "bm_george": "en-gb",
}
MAX_SCRIPT_CHARS = 50000
SECTION_CHARS = 230
OUT_DIR = Path("cloud-output")


def checked_settings():
    raw = os.environ.get("ONEVOICE_TEXT", "")
    text = raw.strip()
    if not text:
        raise ValueError("No narration text was provided.")
    if len(text) > MAX_SCRIPT_CHARS:
        raise ValueError(
            f"Cloud input is limited to {MAX_SCRIPT_CHARS:,} characters per job. "
            "Split longer work into several jobs."
        )
    voice = os.environ.get("ONEVOICE_VOICE", "af_heart")
    if voice not in VOICES:
        raise ValueError("Unsupported English narrator.")
    speed = float(os.environ.get("ONEVOICE_SPEED", "1.0"))
    if not np.isfinite(speed) or not (0.7 <= speed <= 1.5):
        raise ValueError("Speed must be between 0.70 and 1.50.")
    raw_name = os.environ.get("ONEVOICE_FILENAME", "onevoice-narration")[:100]
    name = re.sub(r"[^A-Za-z0-9._-]", "-", raw_name).strip(".-_") or "onevoice-narration"
    return text, voice, speed, name


def segments(text: str, maximum: int = SECTION_CHARS):
    """Keep pronunciation chunks small and preserve paragraph pauses."""
    result: list[tuple[str, float]] = []
    for paragraph in re.split(r"\n\s*\n|\n", text):
        paragraph = re.sub(r"\s+", " ", paragraph).strip()
        if not paragraph:
            continue
        sentences = re.split(r"(?<=[.!?])\s+", paragraph)
        current = ""

        def push(segment: str, pause: float = 0.15):
            if segment:
                result.append((segment, pause))

        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
            if len(sentence) <= maximum:
                if current and len(current) + len(sentence) + 1 > maximum:
                    push(current)
                    current = ""
                current = (current + " " + sentence).strip()
                continue
            push(current)
            current = ""
            words = sentence.split()
            group = ""
            for word in words:
                while len(word) > maximum:
                    push(group)
                    group = ""
                    push(word[:maximum])
                    word = word[maximum:]
                if group and len(group) + len(word) + 1 > maximum:
                    push(group)
                    group = ""
                group = (group + " " + word).strip()
            push(group)
        push(current)
        if result:
            line, _ = result[-1]
            result[-1] = (line, 0.38)
    if result:
        line, _ = result[-1]
        result[-1] = (line, 0)
    return result


def run():
    text, voice, speed, filename = checked_settings()
    chunks = segments(text)
    if not chunks:
        raise ValueError("No spoken words found.")
    out = OUT_DIR / f"{filename}.mp3"
    temp = OUT_DIR / "narration-temp.wav"
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # Quantized model and voices downloaded independently by the workflow.
    tts = Kokoro("models/kokoro-v1.0.int8.onnx", "models/voices-v1.0.bin")
    sample_rate = None
    with sf.SoundFile(
        str(temp), mode="w", samplerate=24000, channels=1,
        subtype="PCM_16", format="WAV"
    ) as wav:
        for index, (chunk, pause) in enumerate(chunks, start=1):
            samples, rate = tts.create(chunk, voice=voice, speed=speed, lang=VOICES[voice])
            if rate != 24000:
                raise RuntimeError(f"Unexpected Kokoro sample rate: {rate}")
            samples = np.asarray(samples, dtype=np.float32).reshape(-1)
            if samples.size == 0:
                raise RuntimeError("Speech engine generated an empty segment.")
            wav.write(samples)
            silence_frames = int(24000 * pause)
            if silence_frames:
                wav.write(np.zeros(silence_frames, dtype=np.float32))
            print(f"Completed {index}/{len(chunks)} sections.", flush=True)

    subprocess.run(
        [get_ffmpeg_exe(), "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
         "-i", str(temp), "-codec:a", "libmp3lame", "-b:a", "96k", str(out)],
        check=True,
        timeout=240,
    )
    temp.unlink(missing_ok=True)
    if not out.is_file() or out.stat().st_size < 1000:
        raise RuntimeError("The MP3 encoder produced an empty file.")
    print(f"MP3 created successfully ({out.stat().st_size} bytes)", flush=True)
    # Never print or upload the script itself.


if __name__ == "__main__":
    try:
        run()
    except Exception as exc:
        print(f"One Voice cloud job failed: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
