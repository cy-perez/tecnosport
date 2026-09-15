#!/usr/bin/env python3
"""
Acomoda lo que entrega `fotos-estudio-degradado` en una carpeta por producto.

Esa skill entrega por formato y tamaño, que es lo cómodo para revisar un lote:

    SALIDA/maestras/honor-2i-01.jpg
    SALIDA/escritorio/honor-2i-01-1600.avif
    SALIDA/escritorio/honor-2i-01-1600.jpg

El catálogo, en cambio, se carga por producto. Este script reordena lo mismo sin
volver a procesar un píxel:

    imagenes/honor-2i/2000/honor-2i-01.jpg      <- la maestra
    imagenes/honor-2i/2000/honor-2i-01.avif
    imagenes/honor-2i/1600/honor-2i-01.avif
    imagenes/honor-2i/1600/honor-2i-01.jpg
    imagenes/honor-2i/1200/…  800/…  480/…

El nombre de la carpeta es el ancho real del archivo, así que un cargador de
imágenes traduce el ancho que pide el navegador a la ruta sin tablas de por
medio.

El id del producto sale del nombre del archivo quitándole el `-NN` final, que es
como lo numeran `icecat_local.py` y `descargar.py`.

Uso:
    python organizar_imagenes.py SALIDA --destino catalogo/fotos/imagenes
    python organizar_imagenes.py SALIDA --destino … --mover
"""

import argparse
import re
import shutil
from pathlib import Path

RE_VARIANTE = re.compile(r"^(?P<base>.+)-(?P<ancho>\d{3,4})$")
RE_PRODUCTO = re.compile(r"^(?P<id>.+)-\d{2}$")
ANCHO_MAESTRA = 2000


def id_producto(base):
    m = RE_PRODUCTO.match(base)
    return m.group("id") if m else base


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("salida", help="carpeta que dejó fotos-estudio-degradado")
    ap.add_argument("--destino", required=True, help="carpeta de imágenes del catálogo")
    ap.add_argument("--mover", action="store_true",
                    help="mueve en vez de copiar (por defecto copia, para no "
                         "tocar la salida de la otra skill)")
    a = ap.parse_args()

    origen = Path(a.salida)
    destino = Path(a.destino)
    maestras, escritorio = origen / "maestras", origen / "escritorio"
    if not maestras.is_dir():
        raise SystemExit(f"no encuentro {maestras}: ¿esa es la carpeta de salida?")

    llevar = shutil.move if a.mover else shutil.copy2
    puestos, productos, saltados = 0, set(), 0

    # La maestra manda en 2000: la variante de 2000 de `escritorio/` sale con
    # menos calidad (JPEG 88 contra 92) y ocuparía el mismo nombre.
    for foto in sorted(maestras.glob("*.jpg")):
        pid = id_producto(foto.stem)
        carpeta = destino / pid / str(ANCHO_MAESTRA)
        carpeta.mkdir(parents=True, exist_ok=True)
        llevar(str(foto), carpeta / foto.name)
        puestos += 1
        productos.add(pid)

    for foto in sorted(escritorio.iterdir()) if escritorio.is_dir() else []:
        if foto.suffix.lower() not in (".avif", ".jpg", ".jpeg", ".webp"):
            continue
        m = RE_VARIANTE.match(foto.stem)
        if not m:
            saltados += 1
            continue
        base, ancho = m.group("base"), m.group("ancho")
        if ancho == str(ANCHO_MAESTRA) and foto.suffix.lower() in (".jpg", ".jpeg"):
            continue                      # ya está la maestra, de mejor calidad
        carpeta = destino / id_producto(base) / ancho
        carpeta.mkdir(parents=True, exist_ok=True)
        llevar(str(foto), carpeta / f"{base}{foto.suffix.lower()}")
        puestos += 1
        productos.add(id_producto(base))

    print(f"{puestos} archivos en {len(productos)} productos -> {destino}")
    if saltados:
        print(f"{saltados} archivos de escritorio/ sin ancho en el nombre: se dejaron donde estaban")

    incompletos = []
    for pid in sorted(productos):
        anchos = sorted((p.name for p in (destino / pid).iterdir() if p.is_dir()),
                        key=int, reverse=True)
        if str(ANCHO_MAESTRA) not in anchos:
            incompletos.append(pid)
    if incompletos:
        print(f"\nsin maestra de {ANCHO_MAESTRA} px: {', '.join(incompletos)}")


if __name__ == "__main__":
    main()
