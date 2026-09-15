#!/usr/bin/env python3
"""
Revisa el entorno de fotos-estudio-degradado usando sólo la biblioteca estándar.

    python entorno.py

Muestra el Python que se está usando (el mismo que ejecutará los scripts), cada
dependencia con su versión, si Pillow escribe AVIF y WebP y si hay avifenc para
AVIF a 10 bits. Si falta algo, imprime el comando exacto para instalarlo en ESTE
Python. Sale con código 0 si no falta nada obligatorio.
"""
from __future__ import annotations

import importlib.util
import sys
import sysconfig
from importlib import metadata
from pathlib import Path

RAIZ_SKILL = Path(__file__).resolve().parent.parent
REQUISITOS = RAIZ_SKILL / "requirements.txt"

# (módulo que se importa, paquete de pip, obligatorio)
DEPENDENCIAS = [
    ("numpy", "numpy", True),
    ("PIL", "pillow", True),
    ("cv2", "opencv-python-headless", True),
    ("rembg", "rembg", True),
    ("onnxruntime", "onnxruntime", True),
    ("pymatting", "pymatting", True),
    ("pillow_heif", "pillow-heif", False),
]


def version(modulo: str, paquete: str) -> str:
    candidatos = [paquete]
    if modulo == "cv2":  # cualquiera de las variantes de OpenCV sirve
        candidatos += ["opencv-python", "opencv-contrib-python", "opencv-contrib-python-headless"]
    for nombre in candidatos:
        try:
            return metadata.version(nombre)
        except metadata.PackageNotFoundError:
            continue
    return "?"


def en_entorno_virtual() -> bool:
    return sys.prefix != sys.base_prefix


def python_del_sistema_protegido() -> bool:
    """PEP 668: Python del sistema que exige --break-system-packages (p. ej. el contenedor de claude.ai)."""
    return not en_entorno_virtual() and (Path(sysconfig.get_path("stdlib")) / "EXTERNALLY-MANAGED").is_file()


def comando_instalacion() -> str:
    cmd = f'"{sys.executable}" -m pip install -r "{REQUISITOS}"'
    return cmd + " --break-system-packages" if python_del_sistema_protegido() else cmd


def faltantes() -> list[str]:
    return [paquete for modulo, paquete, obligatorio in DEPENDENCIAS
            if obligatorio and importlib.util.find_spec(modulo) is None]


def exigir() -> None:
    """Para los scripts: si falta una dependencia obligatoria, explica cómo instalarla y sale."""
    falta = faltantes()
    if falta:
        print(f"Faltan dependencias en este Python ({sys.executable}): {', '.join(falta)}.\n"
              f"Instálalas con:\n  {comando_instalacion()}\n"
              "Si usas un entorno virtual, actívalo antes (y abre claude con él activado). "
              "Diagnóstico completo: python entorno.py", file=sys.stderr)
        sys.exit(2)


def main() -> int:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    v = sys.version_info
    print(f"Python {v.major}.{v.minor}.{v.micro} · {sys.executable}")
    print("  entorno virtual: " + ("sí" if en_entorno_virtual() else "no (es el Python del sistema)"))
    problemas = 0
    if v < (3, 11):
        print("  ✗ hace falta Python 3.11 o posterior (probado con 3.12)")
        problemas += 1
    elif v >= (3, 15):
        print("  · Python muy reciente: si pip no encuentra onnxruntime, usa Python 3.12 o 3.13")
    for modulo, paquete, obligatorio in DEPENDENCIAS:
        if importlib.util.find_spec(modulo) is not None:
            print(f"  ✓ {paquete} {version(modulo, paquete)}")
        elif obligatorio:
            print(f"  ✗ {paquete}: no instalado")
            problemas += 1
        else:
            print(f"  · {paquete}: no instalado (opcional, para fotos HEIC de iPhone)")

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import avif10  # sólo biblioteca estándar

    enc = avif10.localizar_avifenc(avif10.leer_config())
    avif_pillow = webp_pillow = None
    if importlib.util.find_spec("PIL") is not None:
        from PIL import features
        avif_pillow, webp_pillow = bool(features.check("avif")), bool(features.check("webp"))
    if enc:
        print(f"  ✓ AVIF a 10 bits: avifenc {enc['version']} ({enc['avifenc']})")
    elif avif_pillow:
        print("  · AVIF a 10 bits no disponible: se usará Pillow a 8 bits "
              "(para 10 bits: python avif10.py instalar)")
    else:
        print("  ✗ nadie codifica AVIF: ejecuta «python avif10.py instalar» o actualiza Pillow")
        problemas += 1
    if webp_pillow is False:
        print("  · Pillow no escribe WebP (sólo importa si activas ese formato)")

    if problemas:
        print(f"\nPara instalar lo que falta en este Python:\n  {comando_instalacion()}")
        if not en_entorno_virtual():
            print("Si querías usar un entorno virtual, actívalo y vuelve a ejecutar este diagnóstico.")
        return 1
    print("\nTodo listo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
