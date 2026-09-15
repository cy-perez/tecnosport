#!/usr/bin/env python3
"""
Lleva las fotos de cada producto al formato de foto maestra de TecnoSport.

Uso:
    python3 normalizar_imagenes.py crudas/ --salida imagenes/

Espera una carpeta por producto (el nombre de la carpeta es el id del producto)
y deja en la salida la misma estructura ya optimizada:

    imagenes/<id>/<id>-01.jpg      2000x2000, sRGB, sin EXIF
    imagenes/<id>/<id>-01-800.jpg  variante responsive, mismo formato

Foto maestra: 2000 x 2000 px, cuadrada (1:1), en sRGB, sin metadatos EXIF y con
el producto ocupando el 85% del cuadro.

**No se toca la imagen.** Esto es una optimización, no una edición: no se
recorta el fondo, no se fuerza a blanco, no se agrega sombra y no se cambia el
formato del archivo. Un JPEG sale JPEG y un PNG con transparencia sale PNG con
transparencia. La regla vale igual para las variantes responsive, que antes
salían siempre en WebP: ahora heredan el formato del original, como la maestra. Es deliberado: la foto del fabricante ya viene aprobada por la
marca, y reencuadrarla sobre un blanco inventado produce un halo cuando el
fondo original no era blanco puro —que es lo normal en las fotos de Icecat, que
traen degradados y sombras suaves—.

Para que el cuadrado no invente fondo, el relleno se toma del borde de la propia
imagen: se mide el color del marco de 1 px y se rellena con ese color. Si la
imagen trae transparencia, el relleno también es transparente.

Para quitar fondos o agregar sombra de verdad, usar la skill `fotos-de-producto`,
que es donde vive esa decisión.
"""

import argparse
import io
from pathlib import Path

from PIL import Image, ImageCms, ImageOps

LIENZO = 2000
OCUPACION = 0.85          # el producto ocupa el 85% del cuadro
VARIANTES = (1200, 800, 400)
CALIDAD_MAESTRA = 88
CALIDAD_VARIANTE = 82
EXT = (".jpg", ".jpeg", ".png", ".webp")

# Formato de salida por formato de entrada: se conserva el original.
SALIDA = {
    "JPEG": (".jpg", "JPEG"),
    "MPO": (".jpg", "JPEG"),      # algunas camaras guardan JPEG como MPO
    "PNG": (".png", "PNG"),
    "WEBP": (".webp", "WEBP"),
}


def a_srgb(img):
    """
    Convierte al espacio sRGB. Si la imagen trae un perfil ICC distinto se
    transforma de verdad; si no trae perfil, se asume que ya es sRGB, que es
    lo que hace cualquier navegador.
    """
    perfil = img.info.get("icc_profile")
    if not perfil:
        return img
    try:
        origen = ImageCms.ImageCmsProfile(io.BytesIO(perfil))
        destino = ImageCms.createProfile("sRGB")
        modo = "RGBA" if img.mode in ("RGBA", "LA", "P") else "RGB"
        return ImageCms.profileToProfile(img, origen, destino,
                                         outputMode=modo) or img
    except Exception:
        # un perfil corrupto no puede tumbar el lote: se deja la imagen como esta
        return img


def color_de_relleno(img):
    """
    El color del marco de 1 px, para que el cuadrado se rellene con el mismo
    fondo que ya tiene la foto en vez de con un blanco inventado.
    Si el borde es transparente devuelve un color con alfa 0, para que el
    relleno tampoco invente fondo.
    """
    tiene_alfa = img.mode in ("RGBA", "LA")
    borde = []
    an, al = img.size
    paso_x = max(1, an // 64)
    paso_y = max(1, al // 64)
    for x in range(0, an, paso_x):
        borde.append(img.getpixel((x, 0)))
        borde.append(img.getpixel((x, al - 1)))
    for y in range(0, al, paso_y):
        borde.append(img.getpixel((0, y)))
        borde.append(img.getpixel((an - 1, y)))
    if not borde:
        return (255, 255, 255, 0) if tiene_alfa else (255, 255, 255)

    if tiene_alfa:
        opacos = [p for p in borde if p[-1] > 8]
        if not opacos:
            return (255, 255, 255, 0)
        borde = opacos
    canales = len(borde[0])
    # mediana por canal: resiste un pixel raro en una esquina mejor que el promedio
    return tuple(sorted(p[c] for p in borde)[len(borde) // 2]
                 for c in range(canales))


def guardar(img, ruta: Path, formato: str, calidad: int):
    """
    Escribe sin `exif=` ni `icc_profile=`: la imagen sale limpia de metadatos
    y en sRGB. Vale igual para la maestra y para las variantes, que se rigen
    por la misma premisa y conservan el formato del original.
    """
    if formato == "JPEG":
        img.save(ruta, "JPEG", quality=calidad, optimize=True, progressive=True)
    elif formato == "PNG":
        # PNG es sin pérdida: la calidad no aplica, solo el esfuerzo de compresión
        img.save(ruta, "PNG", optimize=True)
    else:
        img.save(ruta, "WEBP", quality=calidad, method=6)


def normalizar(ruta: Path, destino: Path, nombre: str):
    img = Image.open(ruta)
    formato = img.format or "JPEG"
    img = ImageOps.exif_transpose(img)          # aplica la rotacion y suelta el EXIF
    img = a_srgb(img)

    ext, guardar_como = SALIDA.get(formato, (".jpg", "JPEG"))
    conserva_alfa = guardar_como in ("PNG", "WEBP") and img.mode in ("RGBA", "LA", "P")

    if conserva_alfa:
        img = img.convert("RGBA")
    elif img.mode != "RGB":
        # JPEG no soporta alfa: solo aqui se compone, y sobre el color del borde
        if img.mode in ("RGBA", "LA", "P"):
            img = img.convert("RGBA")
            fondo = Image.new("RGBA", img.size, color_de_relleno(img))
            img = Image.alpha_composite(fondo, img)
        img = img.convert("RGB")

    relleno = color_de_relleno(img)

    objetivo = int(LIENZO * OCUPACION)
    img.thumbnail((objetivo, objetivo), Image.LANCZOS)
    lienzo = Image.new(img.mode, (LIENZO, LIENZO), relleno)
    caja = ((LIENZO - img.width) // 2, (LIENZO - img.height) // 2)
    lienzo.paste(img, caja, img if conserva_alfa else None)

    destino.mkdir(parents=True, exist_ok=True)
    maestra = destino / f"{nombre}{ext}"
    guardar(lienzo, maestra, guardar_como, CALIDAD_MAESTRA)
    for ancho in VARIANTES:
        guardar(lienzo.resize((ancho, ancho), Image.LANCZOS),
                destino / f"{nombre}-{ancho}{ext}", guardar_como, CALIDAD_VARIANTE)
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
    print(f"{total} imágenes optimizadas en {salida}")


if __name__ == "__main__":
    main()
