"""Regla 18: con menos de un 5 % de margen no se ofrece (decisión del negocio, 15/10/2026).

El margen es el precio de mercado que asignó el paso 4 sobre el costo del proveedor. La
configuración que no llega no se propone si es nueva y deja de ofrecerse si ya se vendía.
La que no tiene precio de mercado no tiene margen que medir y no se toca.

Vive aparte porque la aplican dos pasos que no pueden importarse entre sí —`exportar_lista.py`,
que la manda como desaparecida a la API, y `fichas.py`, que no le crea carpeta de modelo— y
tienen que leer el mismo umbral: si difirieran, se buscarían fotos de lo que nunca va a entrar.
"""

MARGEN_MINIMO = 0.05


def margen(producto) -> float | None:
    mercado, costo = producto.get("precio_mercado_cop"), producto.get("precio_proveedor_cop")
    if not mercado or not costo:
        return None
    return (mercado - costo) / costo


def se_ofrece(producto) -> bool:
    m = margen(producto)
    return m is None or m >= MARGEN_MINIMO
