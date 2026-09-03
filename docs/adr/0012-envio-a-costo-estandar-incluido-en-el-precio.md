# ADR 0012. Envío a costo estándar incluido en el precio, sin cotización

Fecha: 2026-09-02. Estado: aceptada. Supera a `adr/0004`.

## Contexto

`adr/0004` proponía un puerto `CotizadorEnvio` que calculaba el costo de envío
por destino, peso y volumen, con tres estrategias posibles (tarifas propias,
agregador, API por transportadora). Eso exigía capturar código DANE del
destino antes de pagar, y peso y dimensiones obligatorios en cada variante.

El negocio decidió no cotizar: cada producto se publica con un precio que ya
incluye un valor de envío estándar, igual en todo el país, sin importar lo que
realmente cueste enviarlo a ese destino.

## Decisión

Se elimina el puerto `CotizadorEnvio` y el endpoint `POST
/api/v1/envios/cotizacion`. El checkout no calcula ni pide nada para fijar el
costo de envío: el precio que ve el cliente en el catálogo es el que paga.

Lo que la transportadora cobra por cada envío real se sigue registrando en
`Envio` como costo real, para que el margen del pedido sea verdadero — pero es
un dato interno, no algo que el servidor cotiza ni que el cliente ve o elige.

La cobertura de contraentrega no cambia: sigue siendo un puerto
`RecaudoContraentrega` y una tabla de ciudades cubiertas (`GET
/api/v1/envios/cobertura`), porque ahí sí importa si la transportadora con
recaudo llega al destino, más allá de cuánto cueste el flete.

## Consecuencias

El checkout se simplifica: no hay paso de cotización antes de pagar, ni
dependencia de códigos DANE o de peso y dimensiones por variante. Si el costo
real de un envío supera el estándar incluido en el precio, ese margen negativo
puntual es una decisión de negocio, no un error del sistema — y queda visible
porque el costo real se registra siempre.

Si más adelante el negocio quiere volver a cotizar por destino (por ejemplo,
si el volumen de envíos grandes o lejanos hace insostenible el estándar único),
se reabre esta decisión con un nuevo ADR; el código de `adr/0004` no se
recupera tal cual porque el modelo de datos y el contrato de API ya no lo
sostienen.
