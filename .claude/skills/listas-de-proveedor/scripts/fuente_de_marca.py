#!/usr/bin/env python3
"""¿Lo que la persona guardó en «Fuente de la marca» es la ficha de especificaciones?

    python3 scripts/fuente_de_marca.py validar catalogo/4-entregables/fichas

El 22/10/2026 llegaron primero las páginas «General» de dos modelos de mi.com: traían
pantalla y batería, pero no peso, ni contenido de la caja, ni las configuraciones que el
fabricante vende, y hubo que pedirlas otra vez. Esto lo dice antes de redactar.

Para cada carpeta de modelo con «Fuente de la marca», clasifica cada archivo:

- **especificaciones**: la URL guardada termina en `/specs/`, o el título dice
  «Especificaciones» (mi.com, el soporte de Motorola);
- **specsheet**: un PDF con «spec» en el nombre (JBL);
- **general**: una página de producto de mi.com sin `/specs/`: hay que guardar la otra pestaña;
- **sin reconocer**: no se sabe; se mira a mano.

Una carpeta vacía de una marca que no se lee de forma automática es **falta**. Solo informa el
título y la URL de lo guardado: el contenido de la página no se imprime. Sale con 1 si hay alguna
general o alguna falta.
"""

import argparse
import html
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fichas import FUENTE_DE_LA_MARCA, MARCAS_SIN_LECTURA_AUTOMATICA  # noqa: E402

RE_TITULO = re.compile(r"<title[^>]*>(.*?)</title>", re.I | re.S)
RE_CANONICA = re.compile(r"<link[^>]+rel=[\"']canonical[\"'][^>]*>", re.I)
RE_HREF = re.compile(r"href=[\"']([^\"']+)[\"']", re.I)
# Lo que Chrome deja al guardar con «solo HTML»: <!-- saved from url=(0047)https://… -->
RE_GUARDADA_DESDE = re.compile(r"saved from url=\(\d+\)(\S+?)\s*-->", re.I)
# «Página web, un solo archivo» (.mhtml) no deja ese comentario: la ubicación va en la cabecera.
RE_UBICACION_MHTML = re.compile(r"^(?:Snapshot-)?Content-Location:\s*(\S+)", re.I | re.M)
EXTENSIONES = {".html", ".htm", ".mhtml", ".pdf"}

ESPECIFICACIONES, SPECSHEET, GENERAL, SIN_RECONOCER, FALTA = (
    "especificaciones", "specsheet", "general", "sin reconocer", "falta")


def url_de(texto: str):
    """La URL de lo guardado. Lo que el navegador anota que guardó manda sobre la canónica: una
    /specs/ que apunta su canónica a la página de producto salía «General» (revisión del PR #268)."""
    guardada = RE_GUARDADA_DESDE.search(texto) or RE_UBICACION_MHTML.search(texto)
    if guardada:
        return guardada.group(1)
    canonica = RE_CANONICA.search(texto)
    if canonica:
        href = RE_HREF.search(canonica.group(0))
        if href:
            return html.unescape(href.group(1))
    return None


def clasificar(archivo: Path) -> tuple[str, str]:
    """(clase, detalle legible) de un archivo guardado."""
    if archivo.suffix.lower() == ".pdf":
        if "spec" in archivo.name.lower():
            return SPECSHEET, archivo.name
        return SIN_RECONOCER, f"{archivo.name}: un PDF sin «spec» en el nombre; ¿es el specsheet?"
    texto = archivo.read_text(encoding="utf-8", errors="replace")
    titulo = RE_TITULO.search(texto)
    titulo = re.sub(r"\s+", " ", html.unescape(titulo.group(1))).strip() if titulo else ""
    url = url_de(texto) or ""
    # En mi.com manda la URL: el título de la pestaña General también dice «Todas las
    # especificaciones y características» (22/10/2026).
    if "mi.com" in url:
        if "/specs" in url:
            return ESPECIFICACIONES, f"{titulo} — {url}".strip(" —")
        specs = url.split("?")[0].rstrip("/") + "/specs/"
        return GENERAL, f"es la pestaña «General» ({url}): guarda {specs}"
    if "/specs" in url or "especificaciones" in titulo.lower():
        return ESPECIFICACIONES, f"{titulo} — {url}".strip(" —")
    return SIN_RECONOCER, f"{titulo or archivo.name} — {url or 'sin URL'}"


def marca_de(carpeta: Path):
    for ficha in carpeta.glob("*-ficha.txt"):
        for linea in ficha.read_text(encoding="utf-8").splitlines():
            if linea.startswith("MARCA:"):
                return linea.split(":", 1)[1].strip()
    return None


def validar(raiz: Path) -> list[tuple[str, str, str]]:
    """[(carpeta, clase, detalle)] de todas las carpetas de modelo con «Fuente de la marca»."""
    resultado = []
    for carpeta in sorted(p for p in Path(raiz).iterdir() if p.is_dir() and not p.name.startswith("_")):
        fuente = carpeta / FUENTE_DE_LA_MARCA
        if not fuente.is_dir():
            continue
        archivos = sorted(f for f in fuente.iterdir() if f.is_file() and f.suffix.lower() in EXTENSIONES)
        if not archivos:
            if marca_de(carpeta) in MARCAS_SIN_LECTURA_AUTOMATICA:
                resultado.append((carpeta.name, FALTA, "la carpeta está vacía"))
            continue
        for archivo in archivos:
            clase, detalle = clasificar(archivo)
            resultado.append((carpeta.name, clase, detalle))
    return resultado


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="orden", required=True)
    v = sub.add_parser("validar", help="clasifica lo guardado en cada «Fuente de la marca»")
    v.add_argument("fichas", help="catalogo/4-entregables/fichas")
    a = ap.parse_args()
    resultado = validar(Path(a.fichas))
    malas = 0
    for carpeta, clase, detalle in resultado:
        malo = clase in (GENERAL, FALTA)
        malas += malo
        print(f"[{'!!' if malo else 'ok' if clase in (ESPECIFICACIONES, SPECSHEET) else '??'}] "
              f"{carpeta}: {clase} · {detalle}")
    print(f"\n{len(resultado)} archivos o carpetas revisados · {malas} por corregir")
    sys.exit(1 if malas else 0)


if __name__ == "__main__":
    main()
