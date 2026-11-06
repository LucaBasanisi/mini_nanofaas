#!/usr/bin/env python3
"""
Script per impacchettare il progetto con Repomix per NotebookLM.
Genera due viste utili per NotebookLM:
1. docs/notebookllm/codebase_full.md: codice completo + documentazione del repo.
2. docs/notebookllm/codebase_compressed.md: struttura scheletrica (classi, metodi, firme) con --compress.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = PROJECT_ROOT / "docs" / "notebookllm"

def check_dependencies():
    if not shutil.which("npx"):
        print("Errore: 'npx' non trovato sul sistema. Assicurati che Node.js / npm siano installati.", file=sys.stderr)
        sys.exit(1)

def run_repomix():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("🚀 Generazione documentazione per NotebookLM tramite Repomix...")

    # 1. Full bundle (sorgente + doc essenziale)
    full_output = OUTPUT_DIR / "codebase_full.md"
    cmd_full = [
        "npx", "--yes", "repomix",
        "--config", str(PROJECT_ROOT / "repomix.config.json"),
        "--output", str(full_output)
    ]
    print(f"📦 Creazione snapshot completo -> {full_output.relative_to(PROJECT_ROOT)}")
    subprocess.run(cmd_full, cwd=PROJECT_ROOT, check=True)

    # 2. Compressed bundle (albero e firme sintetiche, ottimo per flashcard architetturali senza riempire il context)
    compressed_output = OUTPUT_DIR / "codebase_compressed.md"
    cmd_compressed = [
        "npx", "--yes", "repomix",
        "--config", str(PROJECT_ROOT / "repomix.config.json"),
        "--compress",
        "--output", str(compressed_output)
    ]
    print(f"🗜️  Creazione snapshot compresso (firme e struttura) -> {compressed_output.relative_to(PROJECT_ROOT)}")
    subprocess.run(cmd_compressed, cwd=PROJECT_ROOT, check=True)

    print("\n✅ Fatto! I file pronti per NotebookLM si trovano in:")
    print(f"   📁 {OUTPUT_DIR}/")
    for f in OUTPUT_DIR.glob("*.md"):
        size_kb = f.stat().st_size / 1024
        print(f"   - {f.name} ({size_kb:.1f} KB)")

if __name__ == "__main__":
    check_dependencies()
    run_repomix()
