#!/usr/bin/env python3
"""
Codificación AVIF a 10 bits (opcional).

Pillow sólo codifica AVIF a 8 bits, y a calidad 60 eso deja escalones visibles en el
degradado. avifenc (libavif) codifica a 10 bits y los reduce a la mitad con el mismo peso.

    python avif10.py estado      # ¿hay avifenc/avifdec utilizables?
    python avif10.py instalar    # descarga los binarios oficiales de libavif desde GitHub

Los binarios se guardan en ~/.cache/fotos-estudio-degradado/libavif (fuera de la skill)
y se verifica su huella SHA-256 antes de extraerlos. Sin avifenc la skill sigue
funcionando: codifica a 8 bits y sube la calidad por pasos si aparece banding.

Este archivo sólo usa la biblioteca estándar, así que funciona aunque falten las demás
dependencias; imagen.py y entorno.py toman de aquí la búsqueda de avifenc.
"""
from __future__ import annotations

import hashlib
import io
import json
import platform
import shutil
import stat
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

RAIZ_SKILL = Path(__file__).resolve().parent.parent
VERSION = "v1.4.2"
URL = "https://github.com/AOMediaCodec/libavif/releases/download/{v}/{archivo}"
PAQUETES = {  # plataforma → (archivo, sha256)
    "linux": ("linux-artifacts.zip", "faf58a670ffbfdc0e3559e6d37592cff277c447dd39453f1cd1d7d7f5a20b8ef"),
    "windows": ("windows-artifacts.zip", "cb2d9fea43dcbab1d0707e3b37eb7b08070ad2fb60a2c188c39ec12382c0484a"),
    "macos": ("macOS-artifacts.zip", "41f9a3db7b7697aa4f9c83d5e07a1b2e00f28f23676d3f27698eef766689a6b6"),
}
EXE = ".exe" if sys.platform.startswith("win") else ""


def cache_libavif() -> Path:
    return Path.home() / ".cache" / "fotos-estudio-degradado" / "libavif"


def leer_config() -> dict:
    try:
        return json.loads((RAIZ_SKILL / "config.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def localizar_avifenc(cfg: dict) -> dict | None:
    """Busca avifenc/avifdec utilizables (libavif ≥ 1.0): ruta del config, caché de la skill o PATH."""
    candidatos = []
    if cfg.get("avifenc_ruta"):
        p = Path(cfg["avifenc_ruta"])
        candidatos.append((p, p.with_name("avifdec" + EXE)))
    c = cache_libavif()
    candidatos.append((c / f"avifenc{EXE}", c / f"avifdec{EXE}"))
    enc, dec = shutil.which("avifenc"), shutil.which("avifdec")
    if enc and dec:
        candidatos.append((Path(enc), Path(dec)))
    for enc, dec in candidatos:
        if enc.is_file() and dec.is_file():
            try:
                r = subprocess.run([str(enc), "--version"], capture_output=True, text=True, timeout=20)
            except (OSError, subprocess.SubprocessError):
                continue
            texto = (r.stdout + r.stderr).strip()
            version = texto.split("Version:")[-1].split()[0] if "Version:" in texto else "?"
            try:
                mayor = int(version.split(".")[0])
            except ValueError:
                mayor = 0
            if mayor >= 1:  # -q y -d existen desde libavif 1.0
                return {"avifenc": str(enc), "avifdec": str(dec), "version": version}
    return None


def plataforma() -> str | None:
    maquina = platform.machine().lower()
    if sys.platform.startswith("win"):
        return "windows" if maquina in ("amd64", "x86_64") else None
    if sys.platform.startswith("linux"):
        return "linux" if maquina in ("x86_64", "amd64") else None
    if sys.platform == "darwin":
        return "macos"
    return None


def instalar() -> int:
    plat = plataforma()
    if plat is None:
        print(f"No hay binarios oficiales para {sys.platform}/{platform.machine()}. "
              "Instala libavif con el gestor de paquetes (p. ej., apt install libavif-bin) "
              "o deja avif_10bits en 'nunca'.")
        return 1
    archivo, huella = PAQUETES[plat]
    url = URL.format(v=VERSION, archivo=archivo)
    print(f"Descargando {url} …", flush=True)
    try:
        with urllib.request.urlopen(url, timeout=120) as r:
            datos = r.read()
    except Exception as e:  # red bloqueada o sin conexión
        print(f"No se pudo descargar ({type(e).__name__}: {e}). Revisa que github.com esté permitido.")
        return 1
    real = hashlib.sha256(datos).hexdigest()
    if real != huella:
        print(f"La huella no coincide (esperada {huella}, recibida {real}). No se instala nada.")
        return 1
    destino = cache_libavif()
    destino.mkdir(parents=True, exist_ok=True)
    exe = ".exe" if plat == "windows" else ""
    with zipfile.ZipFile(io.BytesIO(datos)) as z:
        for nombre in (f"avifenc{exe}", f"avifdec{exe}"):
            (destino / nombre).write_bytes(z.read(nombre))
            if plat != "windows":
                p = destino / nombre
                p.chmod(p.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print(f"Binarios guardados en {destino}")
    return estado()


def estado() -> int:
    enc = localizar_avifenc(leer_config())
    if enc:
        print(f"10 bits disponible: avifenc {enc['version']} en {enc['avifenc']}")
        return 0
    try:
        from PIL import features
        pillow = "sí" if features.check("avif") else "NO"
    except ImportError:
        pillow = "Pillow no está instalado en este Python"
    print(f"10 bits no disponible: se codificará AVIF a 8 bits con Pillow (AVIF en Pillow: {pillow}).")
    if sys.platform.startswith("win") and (cache_libavif() / "avifenc.exe").is_file():
        print("avifenc.exe está descargado pero no arranca: instala el Microsoft Visual C++ Redistributable "
              "(x64) y vuelve a ejecutar «python avif10.py estado».")
    return 1


if __name__ == "__main__":
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    orden = sys.argv[1] if len(sys.argv) > 1 else "estado"
    if orden not in ("estado", "instalar"):
        print(__doc__)
        sys.exit(2)
    sys.exit(instalar() if orden == "instalar" else estado())
