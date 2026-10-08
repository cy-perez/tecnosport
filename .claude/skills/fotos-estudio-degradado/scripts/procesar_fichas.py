#!/usr/bin/env python3
"""
Procesa las fotos de las carpetas de modelo de `catalogo/entregables/fichas/`.

Decisión del 08/10/2026: las fotos de cada modelo viven en su carpeta, y cada
foto retocada queda junto a su original, **con el mismo nombre** (la extensión
puede cambiar: la maestra siempre es JPEG):

    fichas/Samsung Galaxy S25 Ultra/
    ├── Fotos originales/samsung-galaxy-s25-ultra_1.png
    └── Fotos procesadas/samsung-galaxy-s25-ultra_1.jpg        la maestra
                         web/1200/samsung-galaxy-s25-ultra_1.avif   los anchos para el sitio

Uso:
    python3 procesar_fichas.py catalogo/entregables/fichas
    python3 procesar_fichas.py catalogo/entregables/fichas --devolver   # tras un --segundo-plano

No reimplementa nada del estilo: arma un árbol de trabajo en `<fichas>/_estudio/`
—`crudas/<id del modelo>/` con copias de las originales—, corre `procesar.py` sobre
él con `--por-producto --variantes --nuevas` y devuelve cada resultado a la carpeta
de su modelo. `--nuevas` salta lo ya procesado que no cambió, así que correrlo de
nuevo después de soltar fotos nuevas procesa solo esas. Las hojas de revisión y el
reporte quedan en `_estudio/salida/`, en un solo sitio para todos los modelos.

Las fotos REPETIR no se devuelven: no hay archivo que subir, y quedan en la lista
del final para volver a conseguirlas.

Esta parte —preparar y devolver— usa solo la biblioteca estándar, para que sus
pruebas corran donde no está OpenCV.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path

ORIGINALES = "Fotos originales"   # los mismos nombres que listas-de-proveedor/scripts/fichas.py
PROCESADAS = "Fotos procesadas"
WEB = "web"
TRABAJO = "_estudio"
EXT_FOTO = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"}


def slug(texto: str) -> str:
    """El mismo de procesar.py, que nombra con él cada salida. Se copia porque
    procesar.py importa OpenCV al cargarse; si allá cambia, aquí también."""
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    t = re.sub(r"[^A-Za-z0-9]+", "-", t).strip("-").lower()
    return t or "foto"


def id_de_carpeta(carpeta: Path):
    for f in carpeta.glob("*-ficha.txt"):
        return f.name[: -len("-ficha.txt")]
    return slug(carpeta.name)


def modelos(raiz: Path) -> dict:
    """{id del modelo: carpeta} de las carpetas que tienen «Fotos originales»."""
    salida = {}
    for carpeta in sorted(Path(raiz).iterdir()):
        if carpeta.is_dir() and not carpeta.name.startswith("_") and (carpeta / ORIGINALES).is_dir():
            salida[id_de_carpeta(carpeta)] = carpeta
    return salida


def originales(carpeta: Path) -> list:
    return sorted(f for f in (carpeta / ORIGINALES).iterdir() if f.is_file() and f.suffix.lower() in EXT_FOTO)


def preparar(raiz: Path) -> tuple:
    """Copia las originales a `_estudio/crudas/<id>/`. Devuelve (copiadas, errores).

    Dos originales cuyo nombre da el mismo slug —«x_1.jpg» y «x-1.png»— saldrían
    con el mismo nombre y uno pisaría al otro: se informa y ninguno se procesa.
    """
    crudas = Path(raiz) / TRABAJO / "crudas"
    copiadas, errores = 0, []
    for mid, carpeta in modelos(raiz).items():
        vistos = {}
        destino = crudas / mid
        for f in originales(carpeta):
            clave = slug(f.stem)
            if clave in vistos:
                errores.append(f"{carpeta.name}: «{vistos[clave]}» y «{f.name}» quedarían con el mismo nombre")
                continue
            vistos[clave] = f.name
            copia = destino / f.name
            if copia.is_file() and copia.stat().st_size == f.stat().st_size and copia.stat().st_mtime >= f.stat().st_mtime:
                continue
            destino.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, copia)
            copiadas += 1
    return copiadas, errores


def devolver(raiz: Path) -> dict:
    """Copia cada resultado del reporte a la carpeta de su modelo, con el nombre de su original."""
    raiz = Path(raiz)
    salida = raiz / TRABAJO / "salida"
    reporte = json.loads((salida / "reporte.json").read_text(encoding="utf-8"))
    por_id = modelos(raiz)
    resumen = {"devueltas": 0, "repetir": [], "revisar": [], "sin_original": []}
    for foto in reporte.get("fotos", []):
        carpeta = por_id.get(foto.get("grupo") or "")
        original = None
        if carpeta:
            original = next((f for f in originales(carpeta) if slug(f.stem) == foto["nombre"]), None)
        if original is None:
            resumen["sin_original"].append(f"{foto.get('grupo')}/{foto['nombre']}")
            continue
        etiqueta = f"{carpeta.name}/{original.name}"
        estado = foto.get("estado")
        sal = foto.get("salidas") or {}
        if estado == "REPETIR" or not sal.get("maestra"):
            resumen["repetir"].append(etiqueta)
            continue
        if estado == "REVISAR":
            resumen["revisar"].append(etiqueta)
        procesadas = carpeta / PROCESADAS
        procesadas.mkdir(exist_ok=True)
        shutil.copy2(salida / sal["maestra"]["ruta"], procesadas / f"{original.stem}.jpg")
        for w in sal.get("web", []):
            ruta = Path(w["ruta"])
            destino = procesadas / WEB / ruta.parent.name / f"{original.stem}{ruta.suffix}"
            destino.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(salida / ruta, destino)
        resumen["devueltas"] += 1
    return resumen


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fichas", nargs="?", default="catalogo/entregables/fichas")
    ap.add_argument("--devolver", action="store_true",
                    help="solo copia a cada modelo lo que ya está en _estudio/salida (tras un --segundo-plano)")
    ap.add_argument("--segundo-plano", action="store_true", help="lanza procesar.py desacoplado y sale")
    a = ap.parse_args()
    raiz = Path(a.fichas)

    if not a.devolver:
        copiadas, errores = preparar(raiz)
        for e in errores:
            print(f"[error] {e}")
        print(f"{copiadas} foto(s) nuevas o cambiadas para procesar")
        orden = [sys.executable, str(Path(__file__).with_name("procesar.py")), str(raiz / TRABAJO / "crudas"),
                 "-o", str(raiz / TRABAJO / "salida"), "--por-producto", "--variantes", "--nuevas"]
        if a.segundo_plano:
            subprocess.run(orden + ["--segundo-plano"], check=True)
            print(f"Cuando termine: python3 {Path(__file__).name} \"{raiz}\" --devolver")
            return 0
        subprocess.run(orden, check=True)

    r = devolver(raiz)
    print(f"{r['devueltas']} foto(s) en «{PROCESADAS}»")
    for titulo, lista in (("REVISAR (mira la hoja de revisión antes de subirlas)", r["revisar"]),
                          ("REPETIR (no se devolvieron: hay que conseguir otra)", r["repetir"]),
                          ("sin original (se borró o se renombró)", r["sin_original"])):
        if lista:
            print(f"\n{titulo}:")
            for x in lista:
                print(f"  - {x}")
    print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
