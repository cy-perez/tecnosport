"""Regla 18: lo que no le gana al costo la ganancia mínima no se ofrece.

    mercado − costo ≥ GANANCIA_MINIMA_TECNOLOGIA   (50.000 pesos)

Decisión del negocio del 29/10/2026 (ADR-0101): la ganancia mínima es fija, en pesos, y no un
porcentaje del costo. Hasta ese día era min(10 % del costo, 400.000), que a un equipo de
1.900.000 le pedía 190.000 y no dejaba venderlo con 50.000. Es la misma regla que la API exige al
aprobar y al importar la lista (`MargenMinimo.deTecnologia` en `apps/api/domain`, con
`PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA`), sobre el precio de contado. La API exige además un
techo de 400.000 al aprobar; aquí no se descarta nada por ganar de más: el contado que se
apruebe lo baja quien revisa.

El margen es el precio de mercado que asignó el paso 4 sobre el costo del proveedor. La
configuración que no llega no se propone si es nueva y deja de ofrecerse si ya se vendía.
La que no tiene precio de mercado no tiene margen que medir y no se toca.

Vive aparte porque la aplican dos pasos que no pueden importarse entre sí —`exportar_lista.py`,
que la manda como desaparecida a la API, y `fichas.py`, que no le crea carpeta de modelo— y
tienen que leer el mismo umbral: si difirieran, se buscarían fotos de lo que nunca va a entrar.
"""

from decimal import Decimal

# La ganancia mínima, en pesos. Es el mismo `PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA` de la API
# (50.000 por omisión): si cambia allá, cambia aquí, o la skill dejaría fuera lo que la API
# aprueba, o propondría lo que rechaza.
GANANCIA_MINIMA_TECNOLOGIA = 50_000


def margen(producto) -> float | None:
    mercado, costo = producto.get("precio_mercado_cop"), producto.get("precio_proveedor_cop")
    if not mercado or not costo:
        return None
    return (mercado - costo) / costo


def ganancia_minima(costo) -> Decimal:
    """Lo menos que hay que ganarle a ese costo: los mismos pesos, sea cual sea el costo."""
    return Decimal(GANANCIA_MINIMA_TECNOLOGIA)


def se_ofrece(producto) -> bool:
    mercado, costo = producto.get("precio_mercado_cop"), producto.get("precio_proveedor_cop")
    if not mercado or not costo:
        return True
    return mercado - costo >= ganancia_minima(costo)
