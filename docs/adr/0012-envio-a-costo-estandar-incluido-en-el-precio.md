# ADR 0012. Envío a costo estándar incluido en el precio, sin cotización

Fecha: 2026-09-02. Estado: **superada por `adr/0021`**. Superó a `adr/0004`.

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

## Nota de implementación (2026-09-03)

La cobertura se construyó como se anticipaba, con un ajuste de nombre: el
puerto se llama `RepositorioCoberturaContraentrega`, no `RecaudoContraentrega`
como decía el borrador — es una tabla de disponibilidad (`cobertura_contraentrega`,
clave primaria el código DANE de la ciudad), no un puerto de recaudo.

Dos cosas que este ADR no dejaba explícitas y vale la pena registrar: la tabla
**arranca vacía**, así que contraentrega no aparece en ninguna ciudad hasta que
un administrador la cargue a mano vía `POST`/`DELETE
/api/v1/admin/cobertura-contraentrega` (sin UI todavía) — fail-closed, no se
inventó una cobertura inicial; y no hay ninguna integración automática con la
transportadora que la mantenga sincronizada. Detalle completo en
`docs/11-pagos-y-envios.md` y `ADR-0013`.

## Superada (2026-09-08)

Pasó lo que este ADR había anticipado como motivo para reabrirlo: el negocio
decidió cotizar por destino, con Skydropx como agregador. El precio publicado
vuelve a ser precio base y el flete se cobra aparte, informado por separado
antes de pagar. Ver `adr/0021`, y `adr/0022` y `adr/0023` para el seguimiento y
el recaudo.

Lo que este documento decía y **sigue siendo cierto**: el costo real del flete se
registra en `Envio` y es información interna. La diferencia es que ahora hay un
valor cobrado contra el que compararlo.

Lo que decía y **ya no vale**: que no hacen falta códigos DANE del destino ni
peso y dimensiones por variante. Los tres vuelven a ser obligatorios, y la tabla
`cobertura_contraentrega` que nació con la nota de implementación de aquí se
retira (`adr/0023`).
