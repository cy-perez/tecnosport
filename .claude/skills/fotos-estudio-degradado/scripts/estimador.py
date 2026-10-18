"""
Cuánto mide el producto en una foto antes de recortarlo: con eso se elige el lienzo.

Vive aparte de `procesar.py` porque aquel importa OpenCV al cargarse, y esto solo pide NumPy y
Pillow: así sus pruebas corren donde OpenCV no está.

Hasta el 18/10/2026 el contenido era «todo lo más oscuro que 247», y eso solo sirve sobre blanco.
El material de Apple llega sobre gris claro (#F5F5F7, 245): toda la foto quedaba por debajo del
umbral, el producto «medía» la foto entera y la tanda elegía un lienzo de 1600 donde el iPhone 18
Pro, de 712 px en un cuadro de 1000, salía a 1,91× y en REVISAR. En 1200 sale a 1,43×: LISTA.
Ahora el fondo se mide en la foto —un anillo junto al borde— y el contenido es lo que se aparta
de él.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

# El anillo donde se mide el fondo empieza a esta fracción del borde: el material de Apple trae un
# filete de 1 a 3 px más oscuro (227 sobre 245) que no es fondo ni producto.
MARGEN_BORDE = 0.01
ANCHO_ANILLO = 3
# Cuánto tiene que apartarse un píxel del fondo para ser contenido. Sobre blanco equivale al
# umbral de antes (255 − 8 = 247).
TOLERANCIA = 8
# Un fondo más oscuro que esto, o un anillo donde menos de esta fracción es fondo, no es un fondo
# de estudio —una mesa, una cama, una foto de ambiente—: se devuelve la foto entera, que es la
# estimación conservadora (el lienzo sale mayor, no menor, y el aviso de escala tras el recorte
# sigue diciendo la ampliación real). No se exige el anillo entero: un producto que llega hasta
# arriba y abajo de una foto apaisada lo cruza, y no por eso deja de ser un fondo de estudio.
FONDO_MINIMO = 200
FRACCION_FONDO = 0.6
UMBRAL_ALFA = 25
LADO_DE_TRABAJO = 800


def lado_de_contenido(gris: np.ndarray) -> int | None:
    """Lado mayor, en píxeles de `gris`, de lo que se aparta del fondo. None si el fondo no es uno
    de estudio (claro y parejo) o si no hay contenido."""
    alto, ancho = gris.shape
    k = max(2, round(MARGEN_BORDE * max(alto, ancho)))
    a = ANCHO_ANILLO
    if alto <= 2 * (k + a) or ancho <= 2 * (k + a):
        return None
    anillo = np.concatenate([
        gris[k:k + a, k:ancho - k].ravel(), gris[alto - k - a:alto - k, k:ancho - k].ravel(),
        gris[k:alto - k, k:k + a].ravel(), gris[k:alto - k, ancho - k - a:ancho - k].ravel(),
    ]).astype(np.int16)
    fondo = float(np.median(anillo))
    if fondo < FONDO_MINIMO or (np.abs(anillo - fondo) <= TOLERANCIA).mean() < FRACCION_FONDO:
        return None
    contenido = np.abs(gris.astype(np.int16) - fondo) > TOLERANCIA
    contenido[:k, :] = contenido[alto - k:, :] = False
    contenido[:, :k] = contenido[:, ancho - k:] = False
    if contenido.sum() < 20:
        return None
    ys, xs = np.nonzero(contenido)
    return max(int(ys.max() - ys.min()) + 1, int(xs.max() - xs.min()) + 1)


def estimar_lado_producto(ruta: Path) -> int | None:
    """Lado mayor del producto en la foto, en píxeles de la foto. None si no se puede abrir."""
    try:
        with Image.open(ruta) as im:
            ancho0, alto0 = im.size
            if not ancho0 or not alto0:
                return None
            if "A" in im.getbands() or (im.mode == "P" and "transparency" in im.info):
                # Un PNG recortado trae el producto en el alfa: se mide ahí, sin adivinar el
                # fondo —el color guardado de lo transparente puede ser negro—.
                alfa = im.convert("RGBA").getchannel("A")
                if alfa.getextrema()[0] < 255 - UMBRAL_ALFA:
                    caja = alfa.point(lambda v: 255 if v > UMBRAL_ALFA else 0).getbbox()
                    return max(caja[2] - caja[0], caja[3] - caja[1]) if caja else max(ancho0, alto0)
            g = im.convert("L")
            if max(ancho0, alto0) > LADO_DE_TRABAJO:
                f = LADO_DE_TRABAJO / max(ancho0, alto0)
                g = g.resize((max(1, round(ancho0 * f)), max(1, round(alto0 * f))), Image.BILINEAR)
            gris = np.asarray(g)
    except Exception:
        return None
    if gris.size == 0:
        return None
    lado = lado_de_contenido(gris)
    if lado is None:
        return max(ancho0, alto0)
    return max(1, int(round(lado * max(ancho0, alto0) / max(gris.shape))))
