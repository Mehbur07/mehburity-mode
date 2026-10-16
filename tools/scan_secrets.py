"""Fails when a file that git would commit contains something that looks like a secret.

Usage:  python tools/scan_secrets.py          (scans tracked + new, non-ignored files)
Runs automatically from the pre-commit hook in .githooks/.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

PATTERNS = {
    "Google / Gemini API key": re.compile(r"AIza[0-9A-Za-z_\-]{35}"),
    "GitHub token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})"),
    "OpenAI / Anthropic style key": re.compile(r"\bsk-(?:ant-)?[A-Za-z0-9_\-]{20,}"),
    "Groq key": re.compile(r"\bgsk_[A-Za-z0-9]{20,}"),
    "Telegram bot token": re.compile(r"\b\d{8,10}:AA[A-Za-z0-9_\-]{33}\b"),
    "Private key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |)PRIVATE KEY-----"),
    "Hard-coded credential": re.compile(
        r"(?i)\b(?:api[_-]?key|secret|token|password)\b\s*[:=]\s*[\"'][^\"'\s]{16,}[\"']"),
}

# Files that must never be committed, whatever they contain.
FORBIDDEN_NAMES = {"mehburity-client.json", ".env", "secrets.properties"}

BINARY_SUFFIXES = {".png", ".jpg", ".ogg", ".jar", ".zip", ".ico", ".class"}


def candidate_files():
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT, capture_output=True, check=True).stdout
    return [ROOT / p for p in out.decode("utf-8").split("\0") if p]


def main() -> int:
    problems = []
    for path in candidate_files():
        rel = path.relative_to(ROOT).as_posix()
        if path.name in FORBIDDEN_NAMES:
            problems.append(f"{rel}: this file must never be committed")
            continue
        if path.suffix.lower() in BINARY_SUFFIXES or not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for line_no, line in enumerate(text.splitlines(), 1):
            for name, pattern in PATTERNS.items():
                if pattern.search(line):
                    problems.append(f"{rel}:{line_no}: possible {name}")

    if problems:
        print("Secret scan FAILED:")
        for p in problems:
            print("  " + p)
        return 1
    print("Secret scan passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
