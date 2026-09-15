#!/usr/bin/env python3
"""
Hojas de revisión antes/después (8 fotos por hoja).

Se regeneran completas a partir de reporte.json y de las miniaturas guardadas en
.trabajo/miniaturas, así que siempre muestran el estado actual (también después de
marcar fotos a ojo). Orden: REPETIR, REVISAR y LISTA; dentro de cada estado, por nombre.

    python hojas.py SALIDA
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()

from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import reporte  # noqa: E402

MINI = 300
ANCHO = 1600
FILA = 350
CABECERA = 44
TEXTO_X = 20 + MINI + 20 + MINI + 30
COLORES = {"LISTA": (20, 125, 60), "REVISAR": (185, 105, 0), "REPETIR": (185, 30, 30)}


def ruta_miniatura(raiz: Path, nombre: str, cual: str) -> Path:
    return Path(raiz) / reporte.TRABAJO / "miniaturas" / f"{nombre}-{cual}.jpg"


def _fuente(tam: int, negrita: bool = False):
    nombres = (["DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "arialbd.ttf",
                "Arial Bold.ttf", "C:/Windows/Fonts/arialbd.ttf"] if negrita else
               ["DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "arial.ttf", "Arial.ttf",
                "C:/Windows/Fonts/arial.ttf"])
    for n in nombres:
        try:
            return ImageFont.truetype(n, tam)
        except OSError:
            continue
    return ImageFont.load_default(size=tam)


def _envolver(texto: str, fuente, ancho: int, d: ImageDraw.ImageDraw) -> list[str]:
    lineas, actual = [], ""
    for palabra in texto.split():
        prueba = (actual + " " + palabra).strip()
        if d.textlength(prueba, font=fuente) <= ancho or not actual:
            actual = prueba
        else:
            lineas.append(actual)
            actual = palabra
    if actual:
        lineas.append(actual)
    return lineas


def _datos_linea(f: dict) -> list[str]:
    e = f.get("entrada") or {}
    lineas = []
    partes = [f.get("archivo", "")]
    if e.get("ancho"):
        partes.append(f"{e['ancho']}×{e['alto']} px")
    if f.get("escala"):
        partes.append(f"escala {f['escala']:.2f}×")
    caja = f.get("caja_producto") or {}
    if caja:
        partes.append(f"lado {caja['lado_mayor']} px · centro ±{caja['desvio_centro_px']:.1f}")
    lineas.append(" · ".join(p for p in partes if p))
    sal = f.get("salidas") or {}
    partes = []
    if sal.get("maestra"):
        partes.append(f"JPEG {sal['maestra']['kb']} KB")
    web = sal.get("web", [])
    principal = [w for w in web if w["ancho"] == max(x["ancho"] for x in web)] if web else []
    for w in principal:
        txt = f"{w['formato'].upper()} {w['kb']} KB q{w['calidad']}"
        if w.get("bits"):
            txt += f" {w['bits']} bits"
        if w.get("banding") is not None:
            txt += f" · escalones {w['banding']:.2f}"
        partes.append(txt)
    if len(web) > len(principal):
        partes.append(f"+{len(web) - len(principal)} tamaños")
    dc = f.get("delta_croma")
    if dc is not None:
        partes.append(f"Δcroma {dc:.2f}")
    if partes:
        lineas.append(" · ".join(partes))
    return lineas


def generar(raiz: Path, rep: dict, por_hoja: int = 8) -> tuple[list[Path], dict[str, str]]:
    raiz = Path(raiz)
    for vieja in raiz.glob("revision-*.jpg"):
        if reporte.PATRON_HOJA.match(vieja.name):
            vieja.unlink()
    fotos = reporte.ordenar(rep.get("fotos", []))
    f_titulo, f_estado = _fuente(24, True), _fuente(22, True)
    f_texto, f_chico = _fuente(17), _fuente(15)
    rutas, ubicacion = [], {}
    total_hojas = max(1, -(-len(fotos) // por_hoja))
    ancho_texto = ANCHO - TEXTO_X - 20
    for i in range(0, len(fotos), por_hoja):
        grupo = fotos[i:i + por_hoja]
        n_hoja = i // por_hoja + 1
        hoja = Image.new("RGB", (ANCHO, CABECERA + FILA * len(grupo)), (255, 255, 255))
        d = ImageDraw.Draw(hoja)
        d.rectangle([0, 0, ANCHO, CABECERA - 1], fill=(40, 40, 40))
        for k, etiqueta in enumerate(("antes", "después")):
            x = 20 + k * (MINI + 20) + (MINI - d.textlength(etiqueta, font=f_texto)) / 2
            d.text((x, 11), etiqueta, fill=(210, 210, 210), font=f_texto)
        d.text((TEXTO_X, 11), f"Hoja {n_hoja} de {total_hojas} · estado, motivos y datos",
               fill=(255, 255, 255), font=f_texto)
        for j, f in enumerate(grupo):
            y = CABECERA + j * FILA + 20
            for k, cual in enumerate(("antes", "despues")):
                x = 20 + k * (MINI + 20)
                d.rectangle([x - 1, y - 1, x + MINI, y + MINI], outline=(215, 215, 215))
                ruta_m = ruta_miniatura(raiz, f["nombre"], cual)
                if ruta_m.is_file():
                    with Image.open(ruta_m) as m:
                        m = m.convert("RGB")
                        hoja.paste(m, (x + (MINI - m.width) // 2, y + (MINI - m.height) // 2))
                else:
                    d.text((x + 20, y + MINI // 2 - 10), "sin imagen", fill=(150, 150, 150), font=f_texto)
            estado = reporte.estado_final(f)
            d.text((TEXTO_X, y - 4), f["nombre"], fill=(20, 20, 20), font=f_titulo)
            ancho_nombre = d.textlength(f["nombre"], font=f_titulo)
            if TEXTO_X + ancho_nombre + 20 + d.textlength(estado, font=f_estado) > ANCHO - 20:
                yy = y + 30
                d.text((TEXTO_X, yy), estado, fill=COLORES[estado], font=f_estado)
                yy += 32
            else:
                d.text((TEXTO_X + ancho_nombre + 20, y - 2), estado, fill=COLORES[estado], font=f_estado)
                yy = y + 34
            limite = y + FILA - 40
            renglones = [(t, f_chico, (90, 90, 90), "") for t in _datos_linea(f)]
            renglones += [(m, f_texto, (25, 25, 25), "• ") for m in f.get("motivos", [])]
            if f.get("revisada") and estado != "REPETIR":
                nota = f["revisada"].get("nota") or ""
                renglones.append(("Revisada a ojo" + (f": {nota}" if nota else ""), f_texto, (20, 125, 60), "✓ "))
            renglones += [(a, f_texto, (170, 95, 0), "! ") for a in f.get("avisos", [])]
            renglones += [(n, f_chico, (115, 115, 115), "· ") for n in f.get("notas", [])]
            cortado = False
            for texto, fuente, color, prefijo in renglones:
                for k, linea in enumerate(_envolver(texto, fuente, ancho_texto - 20, d)):
                    if yy > limite:
                        cortado = True
                        break
                    d.text((TEXTO_X, yy), (prefijo if k == 0 else "  ") + linea, fill=color, font=fuente)
                    yy += fuente.size + 6
                if cortado:
                    break
            if cortado:
                d.text((TEXTO_X, limite + 6), "… (el resto está en reporte.json)", fill=(115, 115, 115), font=f_chico)
            if j < len(grupo) - 1:
                yl = CABECERA + (j + 1) * FILA
                d.line([(0, yl), (ANCHO, yl)], fill=(220, 220, 220), width=2)
            ubicacion[f["nombre"]] = f"revision-{n_hoja}.jpg"
        ruta_h = raiz / f"revision-{n_hoja}.jpg"
        hoja.save(ruta_h, quality=88)
        rutas.append(ruta_h)
    return rutas, ubicacion


def main() -> int:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    p = argparse.ArgumentParser(description="Regenera las hojas de revisión desde reporte.json",
                                formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    p.add_argument("salida", help="carpeta de salida (la que tiene reporte.json)")
    raiz = Path(p.parse_args().salida)
    rep = reporte.cargar(raiz)
    if not rep:
        print(f"No hay {reporte.NOMBRE} en {raiz}")
        return 1
    rutas, _ = generar(raiz, rep, int(rep.get("configuracion", {}).get("fotos_por_hoja", 8)))
    rep["hojas_revision"] = [r.name for r in rutas]
    reporte.guardar(raiz, rep)
    print("Hojas: " + ", ".join(str(r) for r in rutas))
    return 0


if __name__ == "__main__":
    sys.exit(main())
