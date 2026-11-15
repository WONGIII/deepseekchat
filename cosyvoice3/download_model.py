import concurrent.futures
import os
import shutil
import sys
import time

import requests

REPO_ID = "FunAudioLLM/Fun-CosyVoice3-0.5B-2512"
BASES = [
    f"https://huggingface.co/{REPO_ID}/resolve/main",
    f"https://hf-mirror.com/{REPO_ID}/resolve/main",
]
OUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "repo",
    "pretrained_models",
    "Fun-CosyVoice3-0.5B",
)
FILES = [
    "CosyVoice-BlankEN/config.json",
    "CosyVoice-BlankEN/generation_config.json",
    "CosyVoice-BlankEN/merges.txt",
    "CosyVoice-BlankEN/model.safetensors",
    "CosyVoice-BlankEN/tokenizer_config.json",
    "CosyVoice-BlankEN/vocab.json",
    "campplus.onnx",
    "config.json",
    "configuration.json",
    "cosyvoice3.yaml",
    "flow.pt",
    "hift.pt",
    "llm.pt",
    "llm.rl.pt",
    "speech_tokenizer_v3.onnx",
]
THREADS = 8
MIN_PARALLEL_SIZE = 32 * 1024 * 1024


def head_size(path):
    for base in BASES:
        url = f"{base}/{path}"
        try:
            r = requests.head(url, allow_redirects=True, timeout=30)
            if r.ok and r.headers.get("content-length"):
                return int(r.headers["content-length"]), url
        except Exception as exc:
            print(f"  HEAD failed {url}: {exc}")
    return 0, f"{BASES[0]}/{path}"


def download_whole(path, size):
    dest = os.path.join(OUT_DIR, path)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    for base in BASES:
        url = f"{base}/{path}"
        try:
            with requests.get(url, stream=True, timeout=120, allow_redirects=True) as r:
                r.raise_for_status()
                with open(dest + ".part", "wb") as f:
                    for chunk in r.iter_content(2 * 1024 * 1024):
                        f.write(chunk)
            if size and os.path.getsize(dest + ".part") != size:
                raise RuntimeError(f"size mismatch {os.path.getsize(dest + '.part')} != {size}")
            os.replace(dest + ".part", dest)
            return
        except Exception as exc:
            print(f"  download failed {url}: {exc}")
    raise RuntimeError(f"failed to download {path}")


def download_part(url, start, end, part_path):
    headers = {"Range": f"bytes={start}-{end}"}
    with requests.get(url, headers=headers, stream=True, timeout=120, allow_redirects=True) as r:
        if r.status_code != 206:
            raise RuntimeError(f"range request returned {r.status_code}")
        with open(part_path, "wb") as f:
            for chunk in r.iter_content(2 * 1024 * 1024):
                f.write(chunk)
    expected = end - start + 1
    if os.path.getsize(part_path) != expected:
        raise RuntimeError(f"part size mismatch: {os.path.getsize(part_path)} != {expected}")


def download_parallel(path, size, url):
    dest = os.path.join(OUT_DIR, path)
    parts_dir = dest + ".parts"
    shutil.rmtree(parts_dir, ignore_errors=True)
    os.makedirs(parts_dir, exist_ok=True)
    chunk = max(2 * 1024 * 1024, size // THREADS + 1)
    ranges = []
    start = 0
    index = 0
    while start < size:
        end = min(size - 1, start + chunk - 1)
        ranges.append((index, start, end))
        index += 1
        start = end + 1
    started = time.time()
    done_bytes = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=THREADS) as pool:
        futures = {
            pool.submit(
                download_part,
                url,
                start,
                end,
                os.path.join(parts_dir, f"{index:03d}.part"),
            ): (index, start, end)
            for index, start, end in ranges
        }
        for future in concurrent.futures.as_completed(futures):
            index, start, end = futures[future]
            try:
                future.result()
            except Exception as exc:
                for pending in futures:
                    pending.cancel()
                raise RuntimeError(f"part {index} failed: {exc}") from exc
            done_bytes += end - start + 1
            elapsed = time.time() - started
            speed = done_bytes / elapsed / 1024 / 1024 if elapsed else 0
            print(f"  {done_bytes / 1024 / 1024:.0f}/{size / 1024 / 1024:.0f} MB  {speed:.1f} MB/s", flush=True)
    with open(dest + ".merging", "wb") as out:
        for index, _, _ in ranges:
            with open(os.path.join(parts_dir, f"{index:03d}.part"), "rb") as part:
                shutil.copyfileobj(part, out, 2 * 1024 * 1024)
    if os.path.getsize(dest + ".merging") != size:
        raise RuntimeError("merged size mismatch")
    os.replace(dest + ".merging", dest)
    shutil.rmtree(parts_dir, ignore_errors=True)


def api_sizes():
    try:
        r = requests.get(
            f"https://huggingface.co/api/models/{REPO_ID}?blobs=true", timeout=30
        )
        r.raise_for_status()
        data = r.json()
        return {
            item["rfilename"]: item.get("size")
            or (item.get("lfs") or {}).get("size")
            or 0
            for item in data.get("siblings", [])
        }
    except Exception as exc:
        print(f"API size lookup failed: {exc}")
        return {}


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    sizes = api_sizes()
    total = 0
    for path in FILES:
        dest = os.path.join(OUT_DIR, path)
        if os.path.exists(dest):
            print(f"skip existing {path}")
            continue
        size = sizes.get(path) or 0
        url = f"{BASES[0]}/{path}"
        if size == 0:
            size, url = head_size(path)
        if size == 0:
            print(f"  size unknown, single connection download {path}")
            download_whole(path, 0)
            continue
        total += size
        print(f"download {path} ({size / 1024 / 1024:.1f} MB)", flush=True)
        if size < MIN_PARALLEL_SIZE:
            download_whole(path, size)
        else:
            try:
                download_parallel(path, size, url)
            except Exception as exc:
                print(f"  parallel failed: {exc}; retry single connection", flush=True)
                download_whole(path, size)
    print(f"done, total {total / 1024 / 1024 / 1024:.2f} GB")
    print("model dir:", OUT_DIR)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("ERROR:", exc, file=sys.stderr)
        sys.exit(1)
