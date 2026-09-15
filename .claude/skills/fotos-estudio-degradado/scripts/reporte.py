"""
Reporte y estados de fotos-estudio-degradado.

Cada foto guarda sus motivos automáticos (los detecta el script), sus marcas manuales
(las pone quien revisa a ojo: manos, ganchos, maniquíes, marcas de agua, etiquetas de
precio, varios productos) y una aprobación opcional para los REVISAR ya revisados.
El estado final se calcula siempre con la misma regla:

    REPETIR  si hay algún motivo o marca REPETIR (no se puede aprobar a ojo)
    REVISAR  si hay una marca REVISAR, o motivos REVISAR sin aprobación
    LISTA    en cualquier otro caso

Las fotos REPETIR no dejan archivos en las carpetas de entrega: así nadie sube por
error una foto que hay que volver a tomar.
"""
from __future__ import annotations

import datetime as _dt
import json
import os
import re
import shutil
import tempfile
import zipfile
from pathlib import Path

ORDEN = {"REPETIR": 0, "REVISAR": 1, "LISTA": 2}
NOMBRE = "reporte.json"
TRABAJO = ".trabajo"  # archivos auxiliares: miniaturas, máscaras, vistas previas, avance y registro
PATRON_HOJA = re.compile(r"^revision-\d+\.jpg$")


def ahora() -> str:
    return _dt.datetime.now().astimezone().isoformat(timespec="seconds")


def estado_final(f: dict) -> str:
    auto = f.get("motivos_auto", [])
    marcas = f.get("marcas", [])
    aprobada = f.get("revisada")
    repetir = [m["texto"] for m in auto if m["estado"] == "REPETIR"] + \
              [m.get("motivo", "sin motivo") + " (a ojo)" for m in marcas if m["estado"] == "REPETIR"]
    revisar_manual = [m.get("motivo", "sin motivo") + " (a ojo)" for m in marcas if m["estado"] == "REVISAR"]
    revisar_auto = [m["texto"] for m in auto if m["estado"] == "REVISAR"]
    if repetir:
        f["estado"], f["motivos"] = "REPETIR", repetir + revisar_manual + revisar_auto
    elif revisar_manual or (revisar_auto and not aprobada):
        f["estado"] = "REVISAR"
        f["motivos"] = revisar_manual + ([] if aprobada else revisar_auto)
    else:
        f["estado"], f["motivos"] = "LISTA", []
    return f["estado"]


def cargar(raiz: Path) -> dict:
    ruta = Path(raiz) / NOMBRE
    if ruta.exists():
        try:
            return json.loads(ruta.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            pass
    return {}


def resumen(fotos: list[dict]) -> dict:
    cuenta = {"total": len(fotos), "lista": 0, "revisar": 0, "repetir": 0}
    for f in fotos:
        cuenta[estado_final(f).lower()] += 1
    return cuenta


def ordenar(fotos: list[dict]) -> list[dict]:
    return sorted(fotos, key=lambda f: (ORDEN[estado_final(f)], f["nombre"]))


def escribir_json(ruta: Path, datos: dict) -> None:
    """Escritura atómica: un corte a mitad de camino no deja un JSON roto."""
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=ruta.name, suffix=".tmp", dir=ruta.parent)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(datos, fh, ensure_ascii=False, indent=2)
    os.replace(tmp, ruta)


def guardar(raiz: Path, rep: dict) -> Path:
    rep["fotos"] = ordenar(rep.get("fotos", []))
    rep["resumen"] = resumen(rep["fotos"])
    rep["actualizado"] = ahora()
    ruta = Path(raiz) / NOMBRE
    escribir_json(ruta, rep)
    return ruta


def texto_resumen(rep: dict) -> str:
    r = rep["resumen"]
    listas = "1 lista" if r["lista"] == 1 else f"{r['lista']} listas"
    return f"{listas} · {r['revisar']} para revisar · {r['repetir']} para repetir (de {r['total']})"


# --------------------------------------------------------------------------- salidas

def rutas_salida(f: dict) -> list[str]:
    sal = f.get("salidas") or {}
    rutas = [sal["maestra"]["ruta"]] if sal.get("maestra") else []
    return rutas + [w["ruta"] for w in sal.get("web", [])]


def retirar_salidas(raiz: Path, f: dict) -> int:
    """Mueve las salidas de una foto REPETIR a .trabajo/retenidas/<nombre>/ (se pueden restaurar)."""
    raiz = Path(raiz)
    rutas = rutas_salida(f)
    if not rutas:
        return 0
    destino = raiz / TRABAJO / "retenidas" / f["nombre"]
    movidas = 0
    for rel in rutas:
        origen = raiz / rel
        if origen.is_file():
            (destino / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(origen), str(destino / rel))
            movidas += 1
    f["salidas_retenidas"] = f.get("salidas")
    f["salidas"] = {}
    return movidas


def restaurar_salidas(raiz: Path, f: dict) -> int:
    """Devuelve a las carpetas de entrega las salidas retenidas cuando la foto deja de ser REPETIR."""
    raiz = Path(raiz)
    retenidas = f.get("salidas_retenidas")
    if not retenidas:
        return 0
    base = raiz / TRABAJO / "retenidas" / f["nombre"]
    tmp = {"salidas": retenidas}
    movidas = 0
    for rel in rutas_salida(tmp):
        origen = base / rel
        if origen.is_file():
            (raiz / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(origen), str(raiz / rel))
            movidas += 1
    f["salidas"] = retenidas
    f.pop("salidas_retenidas", None)
    shutil.rmtree(base, ignore_errors=True)
    return movidas


def borrar_salidas(raiz: Path, rutas: list[str]) -> None:
    for rel in rutas:
        p = Path(raiz) / rel
        if p.is_file():
            p.unlink()


def empaquetar(raiz: Path, rep: dict, destino: Path | None = None) -> Path:
    """ZIP sólo con lo que se entrega: maestras, archivos web, hojas de revisión y reporte.json."""
    raiz = Path(raiz)
    cfg = rep.get("configuracion", {})
    carpetas = {cfg.get("carpeta_maestras", "maestras"), cfg.get("carpeta_web", "escritorio")}
    # por producto el primer tramo es el producto, no una carpeta fija: entra todo salvo el trabajo interno
    por_producto = bool(cfg.get("por_producto"))
    destino = Path(destino) if destino else raiz.parent / f"{raiz.name}.zip"
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(raiz.rglob("*")):
            if not p.is_file():
                continue
            rel = p.relative_to(raiz)
            if rel.parts[0] in carpetas or (por_producto and len(rel.parts) > 1 and rel.parts[0] != TRABAJO):
                # JPEG, AVIF y WebP ya vienen comprimidos: se guardan sin recomprimir
                z.write(p, rel.as_posix(), compress_type=zipfile.ZIP_STORED)
            elif len(rel.parts) == 1 and (p.name == NOMBRE or PATRON_HOJA.match(p.name)):
                z.write(p, rel.as_posix())
    return destino
