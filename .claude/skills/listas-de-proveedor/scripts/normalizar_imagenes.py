#!/usr/bin/env python3
"""
Lleva las fotos de cada producto al estándar de publicación de TecnoSport.

Uso:
    python3 normalizar_imagenes.py crudas/ --salida imagenes/

Espera una carpeta por producto (el nombre de la carpeta es el id del producto)
y deja en la salida la misma estructura ya normalizada:

    imagenes/<id>/<id>-01.jpg      2000x2000, fondo blanco, JPEG calidad 88
    imagenes/<id>/<id>-01-800.webp variante responsive

Estándar: lienzo cuadrado, producto ocupando ~85% del lienzo, margen parejo,
fondo blanco puro. Si la foto trae transparencia se compone sobre blanco.
Para quitar fondos o agregar sombra usar la skill fotos-de-producto.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageOps

LIENZO = 2000
OCUPACION = 0.85
VARIANTES = (1200, 800, 400)
EXT = (".jpg", ".jpeg", ".png", ".webp")


def normalizar(ruta: Path, destino: Path, nombre: str):
    img = Image.open(ruta)
    img = ImageOps.exif_transpose(img)
    if img.mode in ("RGBA", "LA", "P"):
        img = img.convert("RGBA")
        fondo = Image.new("RGBA", img.size, (255, 255, 255, 255))
        img = Image.alpha_composite(fondo, img)
    img = img.convert("RGB")

    objetivo = int(LIENZO * OCUPACION)
    img.thumbnail((objetivo, objetivo), Image.LANCZOS)
    lienzo = Image.new("RGB", (LIENZO, LIENZO), (255, 255, 255))
    lienzo.paste(img, ((LIENZO - img.width) // 2, (LIENZO - img.height) // 2))

    destino.mkdir(parents=True, exist_ok=True)
    maestra = destino / f"{nombre}.jpg"
    lienzo.save(maestra, "JPEG", quality=88, optimize=True, progressive=True)
    for ancho in VARIANTES:
        lienzo.resize((ancho, ancho), Image.LANCZOS).save(
            destino / f"{nombre}-{ancho}.webp", "WEBP", quality=82, method=6
        )
    return maestra


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("entrada", help="carpeta con una subcarpeta por producto")
    ap.add_argument("--salida", default="imagenes")
    args = ap.parse_args()

    entrada, salida = Path(args.entrada), Path(args.salida)
    total = 0
    for carpeta in sorted(p for p in entrada.iterdir() if p.is_dir()):
        fotos = sorted(f for f in carpeta.iterdir() if f.suffix.lower() in EXT)
        if not fotos:
            print(f"  sin fotos: {carpeta.name}")
            continue
        for i, f in enumerate(fotos[:4], start=1):
            normalizar(f, salida / carpeta.name, f"{carpeta.name}-{i:02d}")
            total += 1
        if len(fotos) < 4:
            print(f"  {carpeta.name}: solo {len(fotos)} de 4 fotos")
    print(f"{total} imágenes normalizadas en {salida}")


if __name__ == "__main__":
    main()
