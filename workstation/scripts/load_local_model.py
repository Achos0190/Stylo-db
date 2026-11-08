"""Load the local coding model into LM Studio with tuned settings.

Uses the official LM Studio Python SDK (lmstudio>=1.5), so settings that `lms load`
cannot express (flash attention, KV-cache quantisation, batch size) are applied.

Usage:
    workstation/.venv/Scripts/python.exe workstation/scripts/load_local_model.py [--ctx 65536] [--kv q8_0]
"""

from __future__ import annotations

import argparse

import lmstudio as lms

DEFAULT_MODEL = "qwen3.8-27b"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--ctx", type=int, default=65536, help="context length in tokens")
    ap.add_argument("--kv", default="q8_0", help="KV cache type: f16, q8_0, q4_0")
    ap.add_argument("--batch", type=int, default=512, help="eval batch size (smaller = less VRAM for prefill)")
    ap.add_argument("--gpu", default="max", help='GPU offload ratio ("max" or 0..1)')
    ap.add_argument("--keep-loaded", action="store_true", help="unload other models first")
    args = ap.parse_args()

    ratio = 1.0 if args.gpu == "max" else float(args.gpu)
    config = {
        "contextLength": args.ctx,
        "gpu": {"ratio": ratio},
        "flashAttention": True,
        "evalBatchSize": args.batch,
        "llamaKCacheQuantizationType": args.kv,
        "llamaVCacheQuantizationType": args.kv,
    }

    with lms.Client() as client:
        for m in client.llm.list_loaded():
            if args.keep_loaded or m.identifier == args.model:
                print(f"unloading {m.identifier}")
                m.unload()
        model = client.llm.load_new_instance(args.model, args.model, config=config, ttl=None)
        print(f"loaded {model.identifier} with {config}")


if __name__ == "__main__":
    main()
