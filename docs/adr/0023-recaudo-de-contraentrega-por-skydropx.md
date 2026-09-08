# ADR 0023. El recaudo de contraentrega pasa a Skydropx

Fecha: 2026-09-08. Estado: aceptada. Modifica `adr/0006` y retira la tabla propia
de cobertura que dejó la nota de implementación de `adr/0012`.

## Contexto

`adr/0006` habilitó la contraentrega con cuatro reglas de servidor —cobertura de
la ciudad, monto máximo, categorías excluidas e historial de rechazos— y la
cobertura se resolvió con una tabla propia, `cobertura_contraentrega`, que
**arranca vacía y se carga a mano** por `POST`/`DELETE
/api/v1/admin/cobertura-contraentrega`, sin interfaz y sin ninguna sincronización
con la transportadora. En la práctica, hoy la contraentrega está apagada:
`CONTRAENTREGA_HABILITADA` en falso y la tabla sin una sola fila.

Skydropx ofrece pago contra entrega en Colombia con varias transportadoras y lo
activa **dentro de la propia cotización**: si la tarifa admite recaudo, el destino
tiene cobertura; si no la admite, no la tiene. Eso hace que mantener una tabla
propia sea mantener a mano una copia peor de un dato que el proveedor ya da.

## Decisión

**La cobertura de contraentrega sale de la cotización, no de una tabla.** Un
destino admite contraentrega si al menos una de las tarifas cotizadas para ese
paquete admite recaudo. Se retiran la tabla `cobertura_contraentrega`, su puerto
`RepositorioCoberturaContraentrega` y los endpoints de carga manual.

**Las otras tres reglas de `adr/0006` se quedan**, y son nuestras: monto máximo,
categorías excluidas e historial de rechazos del comprador. `PoliticaContraentrega`
sigue siendo dominio puro; lo único que cambia es de dónde le llega el dato de
cobertura.

**El monto máximo propio se mantiene aunque el proveedor tenga el suyo.** La ayuda
de Skydropx reporta que el servicio no acepta recaudos por debajo de COP 2.000 ni
por encima de COP 2.000.000. `[[ CONFIRMAR EN EL CONTRATO: límites mínimo y
máximo del recaudo, comisión, seguro obligatorio sobre el valor declarado y plazo
de dispersión del dinero. ]]` Dos motivos para no borrar el nuestro: un límite del
proveedor puede cambiar sin avisarnos, y el negocio puede querer un techo más bajo
que el del proveedor para celulares. `CONTRAENTREGA_MONTO_MAXIMO` sigue existiendo
y sigue siendo un dato de negocio pendiente.

**El valor a recaudar es el total del pedido, flete incluido.** Con la cotización,
el total ya no es solo mercancía: es líneas más envío, y es esa suma la que la
transportadora cobra en la puerta. Sale de `Pedido.total()`, nunca del cliente.

**Solo efectivo.** Las transportadoras no aceptan otro medio en el recaudo, así
que el checkout tiene que decirlo antes de que el comprador elija el método —no
en el correo de confirmación, cuando ya no puede cambiar de opinión.

**El estado del recaudo llega por el mismo webhook del seguimiento** (`ADR-0022`).
`RECAUDO_PENDIENTE` y `RECAUDO_CONCILIADO` se conservan tal cual: el primero
cuando la entrega se confirma, el segundo cuando la plataforma reporta el dinero
cobrado y dispersado. `POST /api/v1/admin/pedidos/{id}/recaudo` **se mantiene**
como salida manual, porque un recaudo que el proveedor nunca reporte tiene que
poder cerrarse igual, y porque el dinero se concilia contra el extracto del banco,
no contra la pantalla de un tercero.

**La verificación por contacto antes de despachar no se toca.** Es la regla que
más pérdida evita y no depende del proveedor.

## Alternativas

**Dejar la contraentrega como está** (tabla propia, despacho y conciliación a
mano): no reabre un tramo ya cerrado y probado, pero conserva una tabla vacía que
alguien tendría que llenar y mantener a mano para que el método sirviera algún
día. Es trabajo operativo permanente para replicar un dato que la cotización ya
trae.

**Suspender la contraentrega** mientras entra la cotización: barato, porque hoy
está apagada de hecho, pero deja fuera al comprador que no paga por internet, que
es justo la razón por la que `adr/0006` la habilitó.

## Consecuencias

`MetodosDePagoDisponibles` **depende ahora de la cotización**: no se puede decir
si hay contraentrega sin haber cotizado el destino. Eso encadena las dos llamadas
del checkout y hace que una cotización fallida apague también la contraentrega,
no solo el envío a domicilio. Coherente con `ADR-0021`: sin tarifa no hay envío,
y sin envío no hay recaudo.

**La recogida en el punto sigue sin admitir contraentrega**, por la misma razón de
siempre: no hay transportadora que recaude en un mostrador propio. Quien recoge
paga en el punto o paga en línea antes, y eso es un método de pago distinto, no
una contraentrega.

Se pierde el control fino de "en esta ciudad sí y en esta no" que daba la tabla
manual. Si el negocio necesita excluir una ciudad concreta a pesar de que la
transportadora la cubra, hace falta una lista de exclusión —no una de inclusión— y
hoy no existe.

Las pruebas de disponibilidad de contraentrega de `docs/06-testing.md` cambian de
premisa: "fuera de cobertura" ya no es "no está en la tabla" sino "ninguna tarifa
cotizada admite recaudo".
