"""
Qué lienzo lleva cada producto y qué archivos web salen de cada foto.

Vive aparte de `procesar.py` por una razón práctica: aquel importa OpenCV al cargarse, y estas
reglas son las que hay que probar —en `npm run verificar`, donde no está—. Solo usa la biblioteca
estándar. `verificar.py` lee los anchos de aquí también, para que el que escribe y el que relee
no puedan tener dos ideas distintas de qué archivos debe haber.
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


def anchos_web(cfg: dict, lienzo: int, por_producto: bool) -> list[tuple[int, list[str]]]:
    """Los archivos web de una foto: [(ancho, formatos)], del mayor al menor.

    Salen los `anchos_variantes` que caben en el lienzo, cada uno en `formatos_web` salvo que
    `formatos_por_ancho` diga otra cosa. Con `--variantes` son dos: 1200 en AVIF y JPEG —el
    carrusel de la ficha, y la vista previa de WhatsApp, que no muestra AVIF— y 480 en AVIF —la
    miniatura de la galería y la tarjeta de la rejilla—. Es lo que suben `tools/material-catalogo.mjs`
    y `subida-de-imagenes.mjs`; hasta el 16/10/2026 salían cinco anchos en dos formatos, once
    archivos por foto, y al sitio llegaban cuatro.

    Por producto, **el lienzo ocupa el lugar del ancho mayor cuando no lo alcanza**: un producto
    cuyo material solo da para 1000 px publica su carrusel a 1000, no se queda solo con la
    miniatura. Cuando lo alcanza o lo pasa, no se añade: el sitio no sirve más de 1200 (ADR-0056),
    y un lienzo de 2000 se queda en la maestra, que es el archivo.
    """
    pedidos = {int(x) for x in cfg.get("anchos_variantes") or []}
    anchos = {w for w in pedidos if w <= lienzo}
    if por_producto and (not pedidos or lienzo < max(pedidos)):
        anchos.add(int(lienzo))
    por_ancho = cfg.get("formatos_por_ancho") or {}
    return [(w, list(por_ancho.get(str(w), cfg["formatos_web"]))) for w in sorted(anchos, reverse=True)]


def lienzo_previo(registros, grupo: str) -> int | None:
    """El lienzo con que ya se procesaron las fotos de ese producto, según el reporte: el más
    repetido, y a igualdad el mayor. None si no hay ninguna.

    Para `--conservar-lienzo`: reprocesar dos fotos de un modelo de seis elegiría el lienzo solo con
    esas dos, y las otras cuatro quedarían en otro tamaño."""
    cuenta: dict[int, int] = {}
    for r in registros:
        if r.get("grupo") == grupo and r.get("lienzo"):
            lado = int(r["lienzo"][0])
            cuenta[lado] = cuenta.get(lado, 0) + 1
    if not cuenta:
        return None
    return max(cuenta, key=lambda l: (cuenta[l], l))
