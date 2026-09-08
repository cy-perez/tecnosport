# Pagos y envíos

## Métodos de pago

| Método | Proveedor | Cuándo entra el dinero |
|---|---|---|
| Tarjeta débito y crédito | Wompi | Al aprobar |
| PSE | Wompi | Al aprobar |
| Nequi | Wompi | Al aprobar |
| Bancolombia a la mano y botón Bancolombia | Wompi | Al aprobar |
| Addi, pago a cuotas | Wompi | Al aprobar el crédito |
| Transferencia manual | Ninguno | Al conciliar el comprobante |
| **Contraentrega** | Transportadora con recaudo, vía Skydropx | Días después de la entrega |

## Wompi

- **La aprobación no se decide con lo que trae el navegador.** El parámetro de
  retorno solo sirve para mostrar una pantalla. La verdad es el webhook firmado
  más una consulta directa a la transacción.
- **La firma del webhook se verifica siempre.** Un evento sin firma válida se
  descarta y se registra.
- **Firma de integridad** al crear la transacción, sobre referencia, monto y
  moneda, para que nadie altere el precio en el camino.
- Referencia única e idempotente por intento de pago.
- Ningún dato de tarjeta toca el servidor propio. Eso mantiene el alcance de PCI
  en el mínimo, y no hay que arruinarlo agregando un campo de tarjeta por
  comodidad.
- Un trabajo programado concilia los pagos que quedaron pendientes y nunca
  recibieron webhook. Los webhooks se pierden; el dinero no puede perderse con
  ellos.
- Ambiente de pruebas hasta que los recorridos completos pasen. Las llaves de
  producción entran solo por Secret Manager.

## Inventario y reintento de pago

Al confirmar el pedido se reserva inventario por 30 minutos (más abajo,
"reserva sin vencimiento" es solo para contraentrega). Un pago aprobado
convierte esa reserva en salida real; uno rechazado o con error la libera de
inmediato — no espera a que venza sola, para no bloquear stock que ya se sabe
que no se va a vender en ese intento.

**Reintentar un pago fallido no reutiliza la reserva liberada: la crea de
nuevo**, revalidada contra la existencia real en ese momento. Si ya no
alcanza — se vendió mientras tanto — el reintento se rechaza con
`ExistenciaInsuficienteException` (409), el mismo caso de negocio que al
crear el pedido, no un error del sistema. `POST /api/v1/pedidos/{id}/reintentar-pago`
regresa el pedido a `PAGO_PENDIENTE`; después de eso, el checkout pide un
intento de pago nuevo con `POST /api/v1/pagos/intentos`, igual que la primera
vez.

**El reintento no vuelve a cotizar el envío.** La tarifa congelada en el pedido
se conserva aunque haya vencido: cambiarle el total a un pedido que el comprador
ya aceptó sería cobrarle algo distinto de lo que confirmó. Si al despachar la
tarifa vencida ya no sirve, se cotiza de nuevo solo para emitir la guía y la
diferencia la absorbe el margen — visible, porque el costo real se registra
siempre.

**Caso límite:** un webhook tardío, o la conciliación programada llegando
después de los 30 minutos de la reserva, puede traer un pago aprobado sobre
una reserva que ya venció o que ya se había resuelto antes. Confirmarla a
ciegas arriesgaría una sobreventa (la unidad pudo venderse a otro comprador
ya), así que en ese caso el pago y el pedido se marcan igual como pagados —el
dinero ya entró, eso no se revierte— pero el inventario queda sin confirmar,
señalado en los logs del backend para revisión manual (`ADR-0014`).

## Envío

**Se cotiza.** El precio publicado de cada producto es un precio base con IVA y
**sin flete**; el costo de envío se calcula contra el destino real y se cobra
aparte (`ADR-0021`, que supera a `ADR-0012`). El total del pedido es la suma de
las líneas más el envío, y así se muestra antes de pagar.

No es solo una decisión comercial: el **artículo 50 de la Ley 1480 de 2011** exige
que, antes de finalizar la transacción, el comprador vea el precio individual de
cada bien, el precio total, los costos adicionales de envío **informados de forma
adecuada y separada**, y la suma total a pagar. El resumen del checkout es ese
resumen.

### Skydropx

El proveedor es **Skydropx Colombia**, un agregador que cubre varias
transportadoras (Servientrega, Coordinadora, Envía, TCC, Deprisa, Inter
Rapidísimo y otras) con una sola integración. Es la estrategia 2 de las tres que
`ADR-0004` había planteado.

Vive detrás del puerto `CotizadorEnvio` en `application`, con `SkydropxClient` en
`infrastructure`. El dominio no sabe que Skydropx existe, y cambiar de proveedor
—o volver a una tabla de tarifas propia— es escribir otro adaptador.

Lo verificado de su API, y **no supuesto** (regla dura #9):

- **OAuth 2.0 con client credentials**: `POST /api/v1/oauth/token` con
  `client_id`, `client_secret` y `grant_type`. El token dura **2 horas** y el
  límite es de **2 peticiones por segundo**. El token se cachea; pedir uno nuevo
  por cotización quema el límite en la primera hora punta.
- **La cotización es asíncrona.** `POST /api/v1/quotations` la crea y
  `GET /api/v1/quotations/{id}` devuelve las tarifas que hayan llegado; hay que
  consultar hasta que `is_completed` sea verdadero. Las tarifas **valen 24
  horas**.
- **El envío se crea con la cotización y la tarifa elegidas**:
  `POST /api/v1/shipments` con `quotation_id` y `rate_id`, y de ahí sale la guía.
- **Seguimiento**: `GET /api/v1/shipments/tracking/{guia}/{transportadora}`, más
  el webhook (`ADR-0022`).
- **Recolección en la dirección del negocio**: `POST /api/v1/pickups`.

`TODO: confirmar el host base de la cuenta colombiana en el panel (Conexiones >
API).` La documentación pública muestra `pro.skydropx.com` para producción y
`sb-pro.skydropx.com` para pruebas, y según la fuente aparecen también
`api-pro.skydropx.com` y `app.skydropx.com.co`. Va en `SKYDROPX_URL_BASE`, nunca
incrustado.

### Cómo se cotiza

1. El comprador escribe la dirección de entrega. Sin ciudad no hay cotización, y
   por eso ese paso va **antes** del método de pago.
2. El servidor arma el paquete con el peso y las dimensiones de las variantes del
   carrito y pide la cotización.
3. **El servidor elige la tarifa más económica** de las que cubren el destino. El
   comprador no elige transportadora: ve un costo y un plazo estimado. Un
   selector de tarifas es un paso más de checkout y una tarifa más que blindar
   contra manipulación del cliente.
4. La tarifa elegida **se congela en el pedido** con su identificador, la
   transportadora, el servicio, el valor cobrado, el plazo estimado y su
   vencimiento — igual que se congelan precio, nombre y SKU de cada línea.
5. **El cliente nunca manda el costo de envío.** Llega en la respuesta del
   servidor y se recalcula antes de cobrar (regla dura #7).

**Sin tarifa no hay envío a domicilio.** Si Skydropx no responde, si el destino no
tiene cobertura o si ninguna transportadora cotiza, el checkout **no** inventa un
valor ni aplica una tarifa de respaldo: ofrece solo la recogida en el punto y lo
explica. Cobrar un flete inventado es despachar a pérdida o cobrarle de más al
comprador, y las dos son peores que no vender.

**Peso y dimensiones son obligatorios por variante.** Sin paquete no hay
cotización. Una variante sin esos datos no se publica, y el catálogo ya sembrado
necesita relleno antes de encender la cotización.

**El costo real sigue registrándose en `Envio`**, separado del flete cobrado y de
la comisión de recaudo. La diferencia con antes es que ahora hay un valor cobrado
contra el que compararlo: el margen del pedido se puede leer, no estimar.

## Recogida en el punto

En el dominio se llama `TipoEntrega.RETIRO_EN_PUNTO` y así se queda; "recogida en
el punto" es cómo lo dice la interfaz.

**Sin costo de envío, y por eso hay que decirlo.** Elegir la recogida en el punto
de Medellín pone el costo de envío en cero: no se cotiza, no se pide dirección de
entrega y no se emite guía. Es la única forma de comprar sin pagar flete, así que
el checkout muestra el ahorro junto a la opción, no escondido en el total.

Requiere elegir el punto y genera un código de retiro. Es el camino más simple del
sistema y hay que mantenerlo así.

**No admite contraentrega**, por lo mismo de siempre: no hay transportadora que
recaude en un mostrador propio. Quien recoge paga en línea antes o paga en el
punto, y eso último es un método de pago distinto, no una contraentrega.

`TODO: dirección exacta del punto de recogida, horario y días de atención.` El
horario de atención sigue siendo un dato de negocio pendiente y por eso tampoco se
emite `openingHours` en los datos estructurados (`docs/09-plan-de-arranque.md`).

## Seguimiento del envío

Los movimientos del paquete llegan por **webhook firmado** de Skydropx a
`POST /api/v1/envios/webhook`, se verifican antes de aplicar nada y se guardan
todos, en orden, como `EventoSeguimiento` de `Envio` — nada se sobrescribe
(`ADR-0022`).

**Solo tres estados de la plataforma mueven el pedido:**

| Estado de Skydropx | Efecto en `Pedido` |
|---|---|
| `picked_up` | Confirma `DESPACHADO` si no lo estaba |
| `delivered` | `ENTREGADO`, y encadena `RECAUDO_PENDIENTE` si es contraentrega |
| `in_return` | `RECHAZADO_EN_ENTREGA`, libera inventario, registra el motivo |

`created`, `in_transit`, `last_mile`, `delivery_attempt`, `delivered_to_branch`,
`retained`, `exception`, `canceled` y `destroyed` se registran como eventos y no
cambian el estado del pedido. Los cuatro últimos además levantan una alerta: son
los casos en que el paquete se queda quieto y nadie se entera hasta que reclama
el comprador.

**El webhook no es la única verdad.** `TareaConciliacionEnvios` consulta el
seguimiento de los envíos despachados sin evento reciente y aplica el resultado
con la misma lógica, compartida con el webhook. Mismo patrón que
`TareaConciliacionWompi`, y por un motivo que aquí es legal y no solo operativo:
**el retracto y el plazo de entrega se cuentan desde la entrega**, así que un
`delivered` perdido corre plazos que el sistema no está contando.

**Qué ve el comprador.** `GET /api/v1/pedidos/{id}/seguimiento` —sin sesión, con
el correo haciendo de token— devuelve estado del pedido, transportadora, número de
guía, plazo estimado y los eventos. **No** devuelve el identificador de tarifa, el
costo real del flete ni la comisión de recaudo: eso es margen.

**Las notificaciones las manda TecnoSport.** Skydropx puede avisarle al comprador
por WhatsApp y por correo; no se activa. Ampliaría el tratamiento de sus datos a un
canal de un tercero sin autorización específica, y pondría dos remitentes diciendo
cosas parecidas.

## Contraentrega

Es el método con más riesgo operativo del sistema, y el diseño lo refleja.

**Disponibilidad.** No se ofrece siempre. El servidor decide si aparece, según:

- **Cobertura del destino, según la cotización** (`ADR-0023`). Un destino admite
  contraentrega si al menos una de las tarifas cotizadas admite recaudo. Ya no hay
  tabla propia de cobertura cargada a mano: mantenerla era mantener a mano una
  copia peor de un dato que el proveedor ya da.
- **Monto máximo.** Configurable en `CONTRAENTREGA_MONTO_MAXIMO`. Un celular de
  cuatro millones contra entrega es una pérdida esperando ocurrir. Se conserva
  aunque el proveedor tenga el suyo: un límite ajeno puede cambiar sin avisar, y
  el negocio puede querer un techo más bajo.
- **Categorías excluidas.** Configurable. La recomendación de arranque es excluir
  celulares por encima del monto máximo y aceptar el resto.
- **Historial del comprador.** Si un correo o un teléfono ya rechazó pedidos en la
  entrega, no se le ofrece más. Se registra, no se olvida.

Estas reglas son de negocio y viven en el dominio, en un caso de uso
`MetodosDePagoDisponibles`. **El frontend no decide nada de esto**: pide la lista
al servidor y muestra lo que le devuelvan.

**Solo se ofrece con envío a domicilio**, y ahora también **solo si hubo
cotización**: sin tarifa no hay envío, y sin envío no hay recaudo.

**El valor a recaudar es el total del pedido, con el flete incluido**, porque
desde `ADR-0021` el envío se cobra aparte. Sale de `Pedido.total()`, nunca del
cliente.

**Solo efectivo.** Las transportadoras no aceptan otro medio en el recaudo, y eso
se le dice al comprador **antes** de que elija el método, no en el correo de
confirmación cuando ya no puede cambiar de opinión.

**Flujo.**

1. El cliente elige contraentrega y confirma el pedido. No se cobra nada.
2. El pedido queda en `CONFIRMADO_CONTRAENTREGA` y **reserva inventario sin
   vencimiento por tiempo**.
3. Verificación antes de despachar: contacto por WhatsApp o llamada. Queda
   registrado quién verificó y cuándo. Un pedido contraentrega no verificado no
   se despacha. Esta regla no la toca el proveedor: es la que más pérdida evita.
4. Se despacha con recaudo. La guía la emite Skydropx y lleva el valor a cobrar.
5. Entregado: pasa a `RECAUDO_PENDIENTE`. Devuelto: pasa a
   `RECHAZADO_EN_ENTREGA`, se libera el inventario y se registra el motivo.
6. La plataforma reporta el dinero cobrado y dispersado, y pasa a
   `RECAUDO_CONCILIADO`.

**El recaudo pendiente es visible en el panel.** Un pedido entregado hace veinte
días sin conciliar es plata en la calle, y el sistema tiene que gritarlo, no
esconderlo en un reporte. En la práctica, es un filtro sobre el listado de
pedidos que ya existe: `GET /api/v1/admin/pedidos?estado=RECAUDO_PENDIENTE`
devuelve solo esos, ordenados por más antiguo primero (lo más urgente
arriba) — no un endpoint ni una pantalla aparte.

**La conciliación manual se queda.** `POST /api/v1/admin/pedidos/{id}/recaudo`
sigue existiendo aunque el webhook reporte el cobro: un recaudo que el proveedor
nunca reporte tiene que poder cerrarse igual, y el dinero se concilia contra el
extracto del banco, no contra la pantalla de un tercero.

**Costo.** El recaudo tiene una comisión de la transportadora, y las
transportadoras cobran además un seguro obligatorio sobre el valor declarado. Se
registra en el pedido como costo real, separado del flete, para que el margen del
pedido sea verdadero y no una estimación optimista — en `Envio` (`ADR-0013`), que
nace en el despacho con la transportadora y la guía, y que se completa con la
comisión al conciliar. Ese dato hoy se puede escribir pero ningún endpoint lo
devuelve todavía: un pendiente explícito para cuando se retome el panel
administrativo.

`[[ CONFIRMAR EN EL CONTRATO CON SKYDROPX: límites mínimo y máximo del recaudo
—la ayuda pública reporta COP 2.000 y COP 2.000.000—, porcentaje de comisión,
seguro obligatorio sobre el valor declarado y plazo de dispersión del dinero. ]]`

## Transferencia manual

Se muestran los datos de la cuenta y una referencia única. El pedido queda en
`PAGO_PENDIENTE` con reserva de 24 horas, no de 30 minutos. El administrador
concilia el comprobante en el panel. Vencido el plazo sin comprobante, se libera.

El valor a transferir es el total del pedido, flete incluido: no se pide una
transferencia por la mercancía y otra por el envío.
