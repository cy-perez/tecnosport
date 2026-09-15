#!/usr/bin/env python3
"""
Recortes a tamaño real para revisar a ojo.

    python ampliar.py SALIDA NOMBRE                   # zonas automáticas
    python ampliar.py SALIDA NOMBRE --zona X,Y,W,H    # zona del resultado en px (se puede repetir)
    python ampliar.py SALIDA NOMBRE --original        # foto original con cuadrícula (para --excluir)

Zonas automáticas (hasta cuatro, 500×500 px al 100 %, en este orden de prioridad):
  - el tramo del contorno con más bordes inciertos (restos de fondo, halos, transparencias);
  - el tramo donde el producto se parecía a su fondo original (recorte dudoso), si lo hay;
  - el tramo donde el producto menos se separa del fondo nuevo, si lo hay;
  - la base del producto (superficie de apoyo que el recorte pudo conservar);
  - el centro del producto (nitidez, textura, artefactos de compresión).
Las zonas que se solapan en más de la mitad se muestran una sola vez con ambos nombres.
El mosaico queda en SALIDA/.trabajo/zoom/ y su ruta se imprime para abrirlo con view.
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

import hojas  # noqa: E402
import imagen  # noqa: E402
import reporte  # noqa: E402

TAM = 500


def utf8() -> None:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def ruta_resultado(raiz: Path, f: dict) -> Path | None:
    sal = f.get("salidas") or {}
    if sal.get("maestra"):
        return raiz / sal["maestra"]["ruta"]
    ret = f.get("salidas_retenidas") or {}
    if ret.get("maestra"):
        return raiz / reporte.TRABAJO / "retenidas" / f["nombre"] / ret["maestra"]["ruta"]
    if f.get("vista_previa"):
        return raiz / f["vista_previa"]
    return None


def ventana(cx: float, cy: float, ancho: int, alto: int, tam: int = TAM) -> tuple[int, int, int, int]:
    x0 = int(min(max(0, round(cx - tam / 2)), max(0, ancho - tam)))
    y0 = int(min(max(0, round(cy - tam / 2)), max(0, alto - tam)))
    return x0, y0, min(tam, ancho), min(tam, alto)


def punto_contorno(m: np.ndarray, b: tuple, angulo: float) -> tuple[float, float]:
    """Punto más externo del producto en la dirección `angulo` (0° = derecha, 90° = abajo) desde el centro de su caja."""
    alto, ancho = m.shape
    ang = math.radians(angulo)
    cx, cy = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    for r in range(int(math.hypot(ancho, alto) / 2), 0, -2):
        x, y = int(cx + r * math.cos(ang)), int(cy + r * math.sin(ang))
        if 0 <= x < ancho and 0 <= y < alto and m[y, x] > 0.5:
            return x, y
    return cx, cy


def solape(z1: tuple, z2: tuple) -> float:
    """Fracción del área de la zona menor que comparten dos ventanas (x, y, w, h)."""
    ax = max(0, min(z1[0] + z1[2], z2[0] + z2[2]) - max(z1[0], z2[0]))
    ay = max(0, min(z1[1] + z1[3], z2[1] + z2[3]) - max(z1[1], z2[1]))
    return ax * ay / max(1, min(z1[2] * z1[3], z2[2] * z2[3]))


def zonas_automaticas(img01: np.ndarray, m: np.ndarray, cfg: dict, f: dict | None = None,
                      maximo: int = 4) -> list[tuple[str, tuple]]:
    alto, ancho = m.shape
    f = f or {}
    b = imagen.caja(m, 0.5)
    if b is None:
        return []
    candidatas = []
    # 1. bordes inciertos: densidad de alfa intermedio, calculada a 1/4 de resolución
    inc = ((m > 0.15) & (m < 0.85)).astype(np.float32)
    if inc.sum() > 50:
        chico = cv2.resize(inc, (ancho // 4, alto // 4), interpolation=cv2.INTER_AREA)
        dens = cv2.boxFilter(chico, -1, (TAM // 8, TAM // 8), normalize=False)
        y, x = np.unravel_index(int(np.argmax(dens)), dens.shape)
        candidatas.append(("bordes inciertos", ventana(x * 4 + 2, y * 4 + 2, ancho, alto)))
    # 2. recorte dudoso: donde el producto se parecía a su fondo original (dato del reporte)
    co = (f.get("recorte") or {}).get("contraste_original") or {}
    if co.get("bajo_contraste") and co.get("peor_angulo") is not None:
        px, py = punto_contorno(m, b, co["peor_angulo"])
        candidatas.append((f"recorte dudoso (ΔE {co['de_min']})", ventana(px, py, ancho, alto)))
    # 3. poca separación con el fondo nuevo (del reporte; si no está, se mide sobre la imagen)
    sep = (f.get("sombra") or f.get("resplandor") or {}).get("separacion")
    if not sep:
        sep = imagen.separacion(img01, m, cfg)
    if sep.get("sin_separacion") and sep.get("peor_angulo") is not None:
        px, py = punto_contorno(m, b, sep["peor_angulo"])
        candidatas.append((f"poca separación (ΔL* {sep['dl_min']})", ventana(px, py, ancho, alto)))
    # 4. base: la superficie de apoyo que el recorte pudo conservar
    fila = max(b[1], b[3] - max(3, (b[3] - b[1]) // 30))
    xs = np.nonzero((m[fila:b[3]] > 0.5).any(axis=0))[0]
    bx = float(np.median(xs)) if len(xs) else (b[0] + b[2]) / 2
    candidatas.append(("base del producto", ventana(bx, b[3] - TAM * 0.3, ancho, alto)))
    # 5. centro: nitidez, textura y artefactos de compresión
    candidatas.append(("centro del producto", ventana((b[0] + b[2]) / 2, (b[1] + b[3]) / 2, ancho, alto)))
    # las que se solapan en más de la mitad se fusionan (la primera conserva su lugar y suma el nombre)
    unicas: list[list] = []
    for nombre, z in candidatas:
        for u in unicas:
            if solape(z, u[1]) > 0.5:
                u[0] = f"{u[0]} + {nombre}"
                break
        else:
            unicas.append([nombre, z])
    return [(n, z) for n, z in unicas[:maximo]]


def mosaico(img8: np.ndarray, zonas: list[tuple[str, tuple]], destino: Path) -> Path:
    fuente = hojas._fuente(17, True)
    chica = hojas._fuente(14, False)
    cols = 2 if len(zonas) > 1 else 1
    filas = math.ceil(len(zonas) / cols)
    celda_w = max(z[2] for _, z in zonas)
    barra = 46
    celda_h = max(z[3] for _, z in zonas) + barra
    lienzo = Image.new("RGB", (cols * celda_w + (cols - 1) * 12, filas * celda_h + (filas - 1) * 12), (255, 255, 255))
    d = ImageDraw.Draw(lienzo)
    for i, (nombre, (x, y, w, h)) in enumerate(zonas):
        cx, cy = (i % cols) * (celda_w + 12), (i // cols) * (celda_h + 12)
        d.rectangle([cx, cy, cx + celda_w - 1, cy + barra - 2], fill=(40, 40, 40))
        titulo = nombre
        while d.textlength(titulo, font=fuente) > celda_w - 16 and len(titulo) > 4:
            titulo = titulo[:-2].rstrip() + "…"
        d.text((cx + 8, cy + 3), titulo, fill=(255, 255, 255), font=fuente)
        d.text((cx + 8, cy + 24), f"x {x} · y {y} · {w}×{h} px al 100 %", fill=(200, 200, 200), font=chica)
        lienzo.paste(Image.fromarray(np.ascontiguousarray(img8[y:y + h, x:x + w])), (cx, cy + barra))
    destino.parent.mkdir(parents=True, exist_ok=True)
    lienzo.save(destino, quality=92)
    return destino


def vista_original(raiz: Path, f: dict, destino: Path) -> Path:
    foto = imagen.cargar_foto(Path(f["ruta_origen"]))
    rgb = foto["rgb"]
    alto, ancho = rgb.shape[:2]
    esc = min(1.0, 1600 / max(ancho, alto))
    im = Image.fromarray(imagen.a_8bits(cv2.resize(rgb, (round(ancho * esc), round(alto * esc)),
                                                   interpolation=cv2.INTER_AREA)))
    d = ImageDraw.Draw(im, "RGBA")
    fuente = hojas._fuente(15, True)
    paso = next(p for p in (50, 100, 250, 500, 1000, 2000) if max(ancho, alto) / p <= 16)
    for v in range(0, ancho + 1, paso):
        d.line([(v * esc, 0), (v * esc, im.height)], fill=(255, 0, 255, 90), width=1)
        d.text((v * esc + 3, 3), str(v), fill=(255, 0, 255, 255), font=fuente, stroke_width=2, stroke_fill=(255, 255, 255))
    for v in range(paso, alto + 1, paso):
        d.line([(0, v * esc), (im.width, v * esc)], fill=(255, 0, 255, 90), width=1)
        d.text((3, v * esc + 3), str(v), fill=(255, 0, 255, 255), font=fuente, stroke_width=2, stroke_fill=(255, 255, 255))
    c = f.get("caja_origen")
    if c:
        d.rectangle([c["x0"] * esc, c["y0"] * esc, c["x1"] * esc, c["y1"] * esc], outline=(0, 170, 60, 255), width=2)
    for z in f.get("exclusiones", []):
        d.rectangle([z[0] * esc, z[1] * esc, z[2] * esc, z[3] * esc], outline=(220, 0, 0, 255), fill=(220, 0, 0, 60),
                    width=3)
    destino.parent.mkdir(parents=True, exist_ok=True)
    im.save(destino, quality=90)
    print(f"Foto original {ancho}×{alto} px (ya girada), cuadrícula cada {paso} px; verde = caja del producto, "
          "rojo = zonas excluidas.")
    return destino


def main() -> int:
    utf8()
    p = argparse.ArgumentParser(description="Recortes al 100 % para revisar a ojo",
                                formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    p.add_argument("salida")
    p.add_argument("nombre")
    p.add_argument("--zona", action="append", default=[], metavar="X,Y,W,H")
    p.add_argument("--original", action="store_true")
    a = p.parse_args()
    raiz = Path(a.salida)
    rep = reporte.cargar(raiz)
    fotos = {f["nombre"]: f for f in rep.get("fotos", [])}
    f = fotos.get(a.nombre)
    if f is None:
        print(f"No está en el reporte: {a.nombre}. Nombres: {', '.join(sorted(fotos))}")
        return 1
    zoom = raiz / reporte.TRABAJO / "zoom"
    if a.original:
        print(vista_original(raiz, f, zoom / f"{a.nombre}-original.jpg"))
        return 0
    ruta = ruta_resultado(raiz, f)
    if ruta is None or not ruta.is_file():
        print(f"{a.nombre} no tiene imagen de resultado (estado {reporte.estado_final(f)}).")
        return 1
    with Image.open(ruta) as im:
        img8 = np.asarray(im.convert("RGB"))
    alto, ancho = img8.shape[:2]
    if a.zona:
        zonas = []
        for z in a.zona:
            x, y, w, h = [int(float(v)) for v in z.split(",")]
            w, h = min(w, 1000), min(h, 1000)
            x, y = max(0, min(x, ancho - 1)), max(0, min(y, alto - 1))
            zonas.append(("zona pedida", (x, y, min(w, ancho - x), min(h, alto - y))))
    else:
        ruta_m = raiz / reporte.TRABAJO / "mascaras" / f"{a.nombre}.png"
        if not ruta_m.is_file():
            print("No está la máscara de esta foto; reprocésala o usa --zona.")
            return 1
        with Image.open(ruta_m) as im:
            m = np.asarray(im, dtype=np.float32) / 255.0
        zonas = zonas_automaticas(img8.astype(np.float32) / 255.0, m, rep["configuracion"], f)
    print(mosaico(img8, zonas, zoom / f"{a.nombre}-zonas.jpg"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
