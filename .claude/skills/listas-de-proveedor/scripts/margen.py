"""Regla 18: lo que no le gana al costo la ganancia mínima no se ofrece.

    mercado − costo ≥ min(10 % del costo, TOPE_GANANCIA_TECNOLOGIA)

El 10 % es la decisión del negocio del 15/10/2026 (subió del 5 % al 10 % el 24/10/2026), y el
tope, la del 27/10/2026: un equipo de cinco millones no tiene por qué ganarle 500.000 al costo
cuando la API nunca le sugiere más de 400.000. Es la misma regla que la API exige al aprobar y
al importar la lista (`MargenMinimo.deTecnologia` en `apps/api/domain`), sobre el precio de
contado.

El margen es el precio de mercado que asignó el paso 4 sobre el costo del proveedor. La
configuración que no llega no se propone si es nueva y deja de ofrecerse si ya se vendía.
La que no tiene precio de mercado no tiene margen que medir y no se toca.

Vive aparte porque la aplican dos pasos que no pueden importarse entre sí —`exportar_lista.py`,
que la manda como desaparecida a la API, y `fichas.py`, que no le crea carpeta de modelo— y
tienen que leer el mismo umbral: si difirieran, se buscarían fotos de lo que nunca va a entrar.
"""

from decimal import Decimal

MARGEN_MINIMO = 0.10

# El tope de la ganancia mínima, en pesos. Es el mismo `PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA` de
# la API (la ganancia máxima de `SugerenciaDePrecioTecnologia`, 400.000 por omisión): si cambia
# allá, cambia aquí, o la skill dejaría fuera lo que la API aprueba, o propondría lo que rechaza.
TOPE_GANANCIA_TECNOLOGIA = 400_000


def margen(producto) -> float | None:
    mercado, costo = producto.get("precio_mercado_cop"), producto.get("precio_proveedor_cop")
    if not mercado or not costo:
        return None
    return (mercado - costo) / costo


def ganancia_minima(costo) -> Decimal:
    """Lo menos que hay que ganarle a ese costo: el 10 %, sin pasar del tope.

    En decimal y no en flotante: el borde exacto —1.100.000 sobre 1.000.000— entra, y el
    redondeo de 0,1 no lo decide.
    """
    return min(Decimal(costo) * Decimal(str(MARGEN_MINIMO)), Decimal(TOPE_GANANCIA_TECNOLOGIA))


def se_ofrece(producto) -> bool:
    mercado, costo = producto.get("precio_mercado_cop"), producto.get("precio_proveedor_cop")
    if not mercado or not costo:
        return True
    return mercado - costo >= ganancia_minima(costo)
