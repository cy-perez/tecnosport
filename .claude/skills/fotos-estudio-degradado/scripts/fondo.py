#!/usr/bin/env python3
"""
Genera o verifica el fondo plantilla (degradado radial gris con tramado).

    python fondo.py --verificar          # compara assets/ con lo que produce config.json
    python fondo.py --generar            # reescribe el archivo de assets
    python fondo.py --generar -o fondo.png --config otra.json

El fondo se calcula con aritmética determinista (hash entero para el ruido), así que
sale idéntico bit a bit en Linux y Windows. La huella impresa es la de los píxeles.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import imagen  # noqa: E402


def main() -> int:
    p = argparse.ArgumentParser(description="Fondo plantilla de fotos-estudio-degradado")
    p.add_argument("--config", help="config.json alternativo")
    p.add_argument("--generar", action="store_true", help="escribir el archivo del fondo")
    p.add_argument("--verificar", action="store_true", help="comparar el archivo con el config")
    p.add_argument("-o", "--salida", help="ruta de salida (por defecto, la de fondo_archivo)")
    a = p.parse_args()
    cfg = imagen.cargar_config(a.config)
    fondo = imagen.generar_fondo(cfg)
    ancho, alto = cfg["lienzo"]
    print(f"Lienzo {ancho}×{alto} · centro {cfg['fondo_centro']} → esquinas {cfg['fondo_esquinas']} · "
          f"tramado ±{cfg['dither_niveles']} (semilla {cfg['dither_semilla']})")
    print(f"Centro {tuple(fondo[alto // 2, ancho // 2])} · esquina {tuple(fondo[0, 0])} · "
          f"mitad del lado {tuple(fondo[0, ancho // 2])}")
    print(f"Huella de píxeles: {imagen.huella_pixeles(fondo)}")
    destino = Path(a.salida) if a.salida else imagen.RAIZ_SKILL / cfg["fondo_archivo"]
    if a.generar:
        destino.parent.mkdir(parents=True, exist_ok=True)
        gris = bool(np.array_equal(fondo[..., 0], fondo[..., 1]) and np.array_equal(fondo[..., 0], fondo[..., 2]))
        Image.fromarray(fondo[..., 0] if gris else fondo).save(destino, optimize=True)
        print(f"Guardado: {destino} ({destino.stat().st_size // 1024} KB)")
    if a.verificar or not a.generar:
        _, origen = imagen.cargar_fondo(cfg)
        ok = origen.startswith("assets")
        print(("OK: el archivo coincide con el config" if ok else f"DIFERENTE: {origen}"))
        return 0 if ok else 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
