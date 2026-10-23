"""Qué colores se sugieren en el panel para cada configuración (`coloresSugeridos`).

Vive aparte, sin dependencias, porque lo usan la comparación, el redactor, la ficha de cada
modelo y la exportación, y `comparar_lista` ↔ `conocidos` ↔ `fichas` ya se importan entre sí.
"""

import unicodedata

SUPUESTO_PALETA_COMPLETA = ("colores sugeridos: la lista no marca colores, así que se sugiere la paleta completa "
                            "del modelo (regla 13); el panel la premarca y la persona quita lo que no haya")


def sin_tildes(texto: str) -> str:
    return unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode().lower()


def colores_sugeridos(familias, paleta):
    """Los colores de la paleta que nombran alguna de las familias que marcó la lista.

    Una sugerencia, no una decisión: la persona confirma en el panel contra el
    mensaje del proveedor. **Sin emojis se sugiere la paleta completa** (decisión del
    22/10/2026, la premisa de la regla 13 de `colores.md`): las listas casi nunca marcan
    color, y en la del 22/10 62 de 63 configuraciones llegaban al panel sin ninguno
    premarcado, cada una para marcar a mano.
    """
    if not familias:
        return list(paleta or [])
    claves = [sin_tildes(f).split()[0] for f in familias]
    return [c for c in paleta or [] if any(k in sin_tildes(c) for k in claves)]
