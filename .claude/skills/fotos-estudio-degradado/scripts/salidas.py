"""
Qué lienzo lleva cada producto.

Vive aparte de `procesar.py` por una razón práctica: aquel importa OpenCV al cargarse, y estas
reglas son las que hay que probar —en `npm run verificar`, donde no está—. Solo usa la biblioteca
estándar.
"""

from __future__ import annotations


def _escala(cfg: dict) -> list[int]:
    return sorted({int(x) for x in cfg.get("lienzos_escala") or []}, reverse=True)


def elegir_lienzo(lados: list[int], cfg: dict) -> int:
    """Mayor escalón de `lienzos_escala` que el producto alcanza sin pasar de `ampliacion_tolerada`.

    Si no llega a ninguno devuelve el menor: un producto con poco material se publica al máximo
    que ese material da, en vez de quedarse fuera del catálogo.
    """
    escala = _escala(cfg)
    if not escala:
        return int(cfg["lienzo"][0])
    mejor = max([x for x in lados if x], default=0)
    ocupacion, tolerada = float(cfg["ocupacion"]), float(cfg.get("ampliacion_tolerada", 1.0))
    for lado in escala:
        if mejor * tolerada >= lado * ocupacion:
            return lado
    return escala[-1]


def listas_en(lienzo: int, lados: list[int], cfg: dict) -> int:
    """Cuántas fotos quedan por debajo de `ampliacion_revisar` en ese lienzo."""
    objetivo = lienzo * float(cfg["ocupacion"])
    tope = float(cfg["ampliacion_revisar"])
    return sum(1 for x in lados if x and objetivo / x < tope)


def elegir_lienzo_tanda(lados: list[int], cfg: dict, minimo: int, fotos_minimas: int) -> int:
    """El lienzo de un modelo de la tanda: lo elige la compuerta, no la mejor foto.

    La tanda pide `fotos_minimas` fotos LISTA en un lienzo de `minimo` px o más (ADR-0079), y
    `elegir_lienzo` no mira eso: sube el lienzo hasta donde llega la mejor foto, aunque deje a las
    demás en REVISAR —el Galaxy A57 tenía una foto de 1194 px y cinco de 797: lienzo 1600 y una
    sola LISTA; en 1200, las seis—, y lo baja de `minimo` cuando la mejor no llega con
    `ampliacion_tolerada` aunque sí con `ampliacion_revisar` —el Galaxy A27, seis de 800 px: lienzo
    1000 y fuera de la tanda, cuando en 1200 salen a 1,28× y son LISTA—.

    Por eso: el **mayor** escalón de `minimo` o más donde caben `fotos_minimas` LISTA —el mayor y
    no el que más deje, para no bajar a 1200 un modelo que da para 2000—. Si ninguno, el de `minimo`
    o más que más deje, y a igualdad el mayor. Si ni en `minimo` sale una sola, la regla de
    siempre: ese modelo no va a pasar la compuerta, y que se publique al máximo que su material da.
    """
    candidatos = [l for l in _escala(cfg) if l >= int(minimo)]
    if not candidatos:
        return elegir_lienzo(lados, cfg)
    for lado in candidatos:
        if listas_en(lado, lados, cfg) >= fotos_minimas:
            return lado
    mejor = max(candidatos, key=lambda l: (listas_en(l, lados, cfg), l))
    return mejor if listas_en(mejor, lados, cfg) else elegir_lienzo(lados, cfg)
