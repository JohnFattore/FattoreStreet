import argparse
import os
from datetime import datetime
from kokoro import KPipeline
import soundfile as sf

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output", "audio")

def main():
    parser = argparse.ArgumentParser(description="Generate speech from text using Kokoro-82M")
    parser.add_argument("text", help="Text to convert to speech")
    parser.add_argument("--voice", default="af_heart", help="Voice ID (default: af_heart)")
    parser.add_argument("--lang", default="a", help="Language code: a=American, b=British, e=Spanish, f=French (default: a)")
    args = parser.parse_args()

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print(f"Loading Kokoro pipeline (lang={args.lang})...")
    pipeline = KPipeline(lang_code=args.lang, device="cpu")

    print(f"Generating speech (voice={args.voice})...")
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    for i, (gs, ps, audio) in enumerate(pipeline(args.text, voice=args.voice)):
        filename = os.path.join(OUTPUT_DIR, f"{timestamp}_{i}.wav")
        sf.write(filename, audio, 24000)
        print(f"Saved: {filename}")

if __name__ == "__main__":
    main()
