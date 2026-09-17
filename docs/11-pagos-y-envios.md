# Pagos y envíos

## Métodos de pago

| Método | Proveedor | Cuándo entra el dinero |
|---|---|---|
| Tarjeta débito y crédito | Wompi | Al aprobar |
| PSE | Wompi | Al aprobar |
| Nequi | Wompi | Al aprobar |
| Bancolombia a la mano y botón Bancolombia | Wompi | Al aprobar |
| Transferencia manual | Ninguno | Al conciliar el comprobante |
| **Contraentrega** | Transportadora con recaudo, vía Skydropx | Días después de la entrega |

**Qué se ofrece no lo decide esta tabla, lo decide
`tecnosport.wompi.metodos.habilitados`** (`WOMPI_METODOS_HABILITADOS`). Es la
lista de lo que la *cuenta* de Wompi tiene activado, que no es lo mismo que lo
que el código sabe procesar. `MetodosDePagoDisponibles` parte de ahí y
`CrearPedido` lo exige otra vez antes de crear el pedido — el servidor no se fía
de que el cliente haya consultado la lista (regla dura #7)— y responde `409
METODO_DE_PAGO_NO_HABILITADO`, que no es el `CONTRAENTREGA_NO_DISPONIBLE`: uno es
"para ningún pedido", el otro "para este". Desde la Fase 3 y hasta el 14 de
septiembre de 2026 no existía esa distinción: se ofrecía el enum entero. Ver
`ADR-0029`.

### Addi

**No se ofrece, y hay dos motivos distintos que conviene no mezclar.**

El de negocio (14 de septiembre de 2026): Addi estudia la activación con el
sitio ya en línea, así que no puede estar el día del lanzamiento. Ofrecerlo
antes sería prometer un medio de pago que no se puede honrar, y **sin que nada
reventara**: la URL del Web Checkout hospedado no le manda a Wompi el método
elegido —Wompi pinta su propia lista y el comprador vuelve a elegir allí— así
que el comprador habría pagado con tarjeta un pedido grabado como Addi.

El técnico, encontrado al revisar lo anterior: **esta tabla decía que Addi lo
provee Wompi, y no es cierto.** La documentación pública de Wompi consultada el
14 de septiembre de 2026 no lista Addi entre sus medios; lo que Wompi ofrece en
esa familia es `BANCOLOMBIA_BNPL` ("Compra y Paga Después Bancolombia", cuatro
cuotas) y `SU_PLUS`. Addi es un proveedor aparte, con su propia integración. Así
que `MetodoPago.ADDI` sigue marcado como método de pasarela en el enum y
`CrearIntentoDePago` lo enrutaría a Wompi, donde no existe. Hoy eso no puede
pasar —la configuración no lo habilita y `CrearPedido` lo rechaza— pero el
modelo está mintiendo mientras nadie lo toque.

**TODO (dato de negocio, no lo inventes):** decidir qué se hace con
`MetodoPago.ADDI`. Las dos salidas razonables son integrar Addi directamente
cuando lo aprueben —y entonces deja de ser un método de pasarela— o quitar el
valor del enum y ofrecer en su lugar el BNPL de Bancolombia, que sí llega por
Wompi y por tanto por la configuración que ya existe. Mientras se decide, el
valor queda y no se ofrece.

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
- **El método elegido y el medio cobrado son dos hechos distintos, y los dos se
  guardan.** El Web Checkout hospedado no recibe el método que el comprador
  eligió en nuestro checkout: Wompi pinta su propia lista y el comprador vuelve
  a elegir allí. Así que `pedido.metodo_pago` es una intención, no un hecho.
  `pago.medio_reportado_pasarela` guarda el `payment_method_type` que Wompi
  reporta —crudo, tal como él lo nombra— por webhook o por conciliación, y se
  queda con lo último que la pasarela dijo; un evento que no lo trae no borra lo
  que ya se sabía. No pisa el método elegido: machacarlo borraría la única prueba
  de que el sitio ofreció una cosa y cobró otra. `MediosDeWompi` traduce los
  valores que este sitio ofrece y devuelve nulo para el resto — "no sé traducir
  esto" no es "esto no coincide", y quien pregunte tiene que distinguirlos antes
  de afirmar una discrepancia.
  **Hoy nadie compara los dos.** Se guarda la evidencia; ninguna pantalla ni
  ningún informe la lee todavía y `MediosDeWompi` no tiene un solo llamador en
  producción. Es deuda declarada, no un olvido: comparar sin haber decidido qué
  hacer con la discrepancia sería una alerta sin dueño.
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

**Cuántas de esas cotizan de verdad es otra cosa, y conviene saberlo antes de
prometer.** En la cuenta de pruebas cotizan cuatro —Servientrega, Coordinadora,
Envía y 99 minutes—, Inter Rapidísimo responde `no_coverage`, y **Coordinadora,
que es la más barata, no puede emitir**: su contador de remisiones está atascado y
falla siempre, a cualquier hora (`docs/13` §6.10). Un selector por precio la elige
sola, así que la emisión reintenta excluyendo las transportadoras que ya fallaron
para ese pedido. El catálogo real de producción hay que volver a medirlo con las
credenciales de producción; nada de esto es una lista fija.

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
- **El envío se crea con la tarifa elegida, y solo con ella**:
  `POST /api/v2/shipments` con `rate_id`. **`quotation_id` no es un campo del
  envío**, aunque lo diga cualquier resumen de la API (`docs/13` §6.4). Va por
  **v2** porque v2 siempre devuelve un arreglo de envíos, y en Colombia ninguna
  transportadora admite multipaquete: un pedido de dos variantes son dos guías
  (`ADR-0031`). La relectura, en cambio, es por **v1** —
  `GET /api/v1/shipments/{id}`—, porque `GET /api/v2/shipments/{id}` no existe.
- **La creación no devuelve la guía.** Responde `202` con el envío aceptado y el
  saldo ya cobrado; el número aparece minutos después y hay que ir a buscarlo, y
  a veces lo que aparece es la muerte del envío con el saldo devuelto entero. Por
  eso un pedido no se marca despachado con la respuesta de creación (`ADR-0033`).
- **Seguimiento**: `GET /api/v1/shipments/tracking/{guia}/{transportadora}`, más
  el webhook (`ADR-0022`). La transportadora va con el **código de la
  plataforma**, no con su nombre visible —"99 minutes" es `ninetynineminutes`— y
  ese código **no se deriva del nombre**: viene en la respuesta del envío, y por
  eso se guarda al emitir. Una guía tecleada a mano en el panel no lo tiene y no
  se puede conciliar (`docs/13` §6.8).
- **Recolección en la dirección del negocio**: `POST /api/v1/pickups`. **No se
  usa, y no por decisión nuestra**: el conector de la transportadora responde
  `422 ECONNREFUSED at PICKUP` en los ocho intentos, repartidos en tres días y
  tres horas distintas, mientras `GET /pickups/coverage` sí devuelve fechas reales
  con el mismo envío (`docs/13` §6.11 y §6.14). El endpoint está vivo y valida —un
  envío al que le falta el barrio del destino falla antes, en la dirección—, así que
  lo caído es el conector de la transportadora y no la recolección entera. Hasta que eso cambie, **la recolección se
  programa a mano en el panel de Skydropx** y el despacho termina en "alguien
  lleva los paquetes".
- **El barrio del destino decide si esa guía se podrá recoger.** `Direccion.barrio`
  es opcional en el checkout y así se queda (`ADR-0021`, segunda corrección):
  exigirlo le cobraría fricción a cada comprador de hoy por una capacidad que
  todavía no existe. La contrapartida queda escrita para el día que el conector
  vuelva: **una guía sin barrio de destino se recoge a mano**, aunque todas las
  demás se programen por API.

**El host de pruebas está confirmado: `sb-pro.skydropx.com`**, y no por lectura
sino porque es el único de los candidatos que autentica con las credenciales del
sandbox —`api-pro` y `pro` responden `invalid_client`—. Contra él se cotizó, se
emitieron guías de verdad y se comprobó la firma del webhook.

`TODO: confirmar el host de producción.` La documentación pública muestra
`pro.skydropx.com` y el panel indica `api-pro.skydropx.com`; **no se puede
comprobar sin credenciales de producción**, que es justo lo que falta. Va en
`SKYDROPX_URL_BASE`, nunca incrustado.

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

**El valor declarado tiene piso y techo, y los dos son de la plataforma.**
Skydropx valida `declared_amount` **por bulto** en el rango
[COP 10.000, COP 5.000.000] y rechaza la cotización **entera** si un solo bulto se
sale, medido contra la cuenta por los dos extremos (`docs/13` §6.4 y §6.13). Los
dos se aplican en `ArmadorDeBultos`, y no se aplican igual:

- **Abajo se eleva al mínimo.** Un cable de 8.000 se declara en 10.000; no le
  quita nada al comprador y la transportadora responde por más, no por menos
  (`ADR-0035`). Se paga por unidad: tres cables declaran 30.000 contra 24.000
  facturados.
- **Arriba no se recorta.** Un artículo que vale más de 5.000.000 **no se
  despacha a domicilio**, y el checkout lo dice nombrando el artículo: declararlo
  en cinco millones dejaría el resto sin asegurar y esa diferencia la pondría el
  negocio si el paquete se pierde (`ADR-0036`). El carrito que lo lleve cae a
  recogida en el punto, entero: este sistema no tiene pedidos parciales.

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

Los movimientos del paquete se guardan todos, en orden, como `EventoSeguimiento`
de `Envio` — nada se sobrescribe (`ADR-0022`).

**El webhook avisa; no trae el evento** (`ADR-0032`). Llega firmado a
`POST /api/v1/envios/webhook` y la firma se verifica antes de mirar nada, pero su
cuerpo **no tiene identificador de evento ni fecha** —lo que trae es `data.id`,
que es el del paquete—, y esos dos datos sostienen la idempotencia del rastro y
los plazos legales. Así que el webhook saca el número de guía y `ConciliarGuia`
—**el mismo objeto** que usa la tarea programada— consulta el rastreo y aplica.
Los dos caminos no se parecen: son el mismo código con distinto disparador.

**Solo tres estados de la plataforma mueven el pedido:**

| Estado de Skydropx | Efecto en `Pedido` |
|---|---|
| `picked_up` | Confirma `DESPACHADO` si no lo estaba |
| `delivered` | `ENTREGADO`, y encadena `RECAUDO_PENDIENTE` si es contraentrega |
| `in_return` | `RECHAZADO_EN_ENTREGA`, libera inventario, registra el motivo |

`created`, `in_transit`, `last_mile`, `delivery_attempt`, `delivered_to_branch`,
`retained`, `exception`, `canceled`, `destroyed` y `error` se registran como
eventos y no cambian el estado del pedido. **Son trece estados y no doce**: el
canal del webhook tiene su propio vocabulario y trae uno más que el del rastreo
—`error`—, medido en un evento de prueba del panel (`docs/13` §6.9).

**Los cinco últimos piden ojo humano, y desde el 17 de septiembre de 2026 alguien
los mira.** `exception`, `retained`, `canceled`, `destroyed` y `error` dejan el
paquete quieto sin hacer avanzar el pedido: si nadie los mira, el comprador se
entera antes que el negocio. Salen en la **bandeja de revisión** del panel, junto
con las dos situaciones de la emisión que comprometen saldo, y se vacía con un
**acuse** que guarda quién miró y qué anotó (`ADR-0034`). El acuse no resuelve
nada: solo deja escrito que alguien miró. Y no es una mordaza — una guía acusada
**vuelve** a la bandeja si le llega un evento posterior al acuse, comparado contra
nuestro reloj y nunca contra la fecha que pone la transportadora. Lo que lleve más
de 24 horas sin mirar se avisa por correo.

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
- **Rango del monto.** Configurable en `CONTRAENTREGA_MONTO_MINIMO` y
  `CONTRAENTREGA_MONTO_MAXIMO`, y **se comparan contra el total del pedido, con el
  flete dentro** (`adr/0023`): es lo que el mensajero cobra en la puerta, no la
  mercancía sola. El techo existe porque un celular de cuatro millones contra
  entrega es una pérdida esperando ocurrir; **el piso existe porque la
  transportadora no recauda por debajo de cierto valor**, y ofrecerlo ahí sería
  prometer un medio de pago que nadie puede ejecutar. Los dos valores del 14 de
  septiembre de 2026 —COP 2.000 y COP 2.000.000— son los que **reporta la ayuda
  pública de Skydropx**, no un contrato firmado: el marcador de abajo sigue
  abierto. El negocio puede querer un techo más bajo que el del proveedor, y por
  eso el valor es propio y no se lee de la plataforma.
- **Categorías excluidas.** Configurable en `CONTRAENTREGA_CATEGORIAS_EXCLUIDAS`, y
  **vacía a propósito**. Ver abajo: la regla de lo tecnológico es por precio, no por
  línea, y la implementa el techo. Esta lista queda para el día que alguna línea no
  deba ir contra entrega **a ningún precio**, que hoy no es el caso de ninguna.

### La regla de lo tecnológico es por precio, no por categoría

Decisión de negocio del 14 de septiembre de 2026, y conviene dejarla escrita con su
consecuencia técnica porque se implementó mal una vez:

> Lo tecnológico **por encima de COP 2.000.000** no va contra entrega. Un celular
> **igual o por debajo** de esa cifra sí puede ir.

El primer intento excluyó la línea `CELULARES` entera, y eso bloqueaba también los
dos celulares del catálogo (1.299.900 y 1.499.900) — justo los que el negocio sí
quiere despachar contra entrega. **La regla es por precio**, así que la implementa
`CONTRAENTREGA_MONTO_MAXIMO`, que ya vale 2.000.000: nada que pase de esa cifra
califica, sea un celular o una camiseta, y no hace falta ninguna lista.

**Dónde la aproximación no es exacta, y hay que saberlo.** El techo se compara contra
el **total del pedido**, y la regla del negocio habla de **un artículo**. Difieren en
un carrito mezclado: un celular de 1.500.000 más ropa por 600.000 suma 2.100.000 y el
techo lo rechaza, aunque ningún artículo pase de 2.000.000. El error va del lado
seguro —se ofrece contraentrega de menos, nunca de más— y tiene sentido por sí mismo:
lo que el mensajero carga en efectivo es el total, no el artículo más caro. Si algún
día el negocio quiere la regla estrictamente por artículo, hay que llevarla al dominio
como una regla sobre las líneas, no como un tope sobre la suma.

**Y esto ya se probó con el catálogo real, no en teoría.** El 14 de septiembre de
2026, el mismo día, lo tecnológico se amplió a relojes, audífonos, cargadores, cables
de cargador, power banks, consolas, parlantes, computadores, tablets y proyectores.
Entraron como **categorías** de la línea `TECNOLOGIA` (antes `CELULARES`), y **la
regla de contraentrega no se tocó**: sigue siendo el techo por precio. Con la regla en
una lista de categorías habrían sido diez entradas nuevas que alguien tenía que
acordarse de escribir, y olvidar una era cuestión de tiempo.
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

**El dinero no llega el día de la entrega, y eso cambia cómo se lee la espera.**
Skydropx retira el recaudo de dos maneras: a **créditos de la plataforma**,
inmediato y sin comisión pero solo gastable en envíos, o a **cuenta bancaria**,
con comisión financiera y disponible **los jueves**. Un pedido entregado un
viernes pasa casi una semana en `RECAUDO_PENDIENTE` sin que nada esté mal, así
que cualquier alerta sobre ese estado tiene que contar jueves, no días. ⚠️ Es lo
que reporta la ayuda pública de Skydropx, una sola fuente y no un contrato
(`docs/13` §3); **dónde cae el recaudo sigue sin decidirse** y es una decisión
contable, no técnica.

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
—la ayuda pública reporta COP 2.000 y COP 2.000.000, y **esas son las cifras que
el sistema ya está usando** desde el 14 de septiembre de 2026, sin contrato que las
respalde: si el contrato real trae un tope menor, las guías se rechazarán al
emitirlas con el pedido ya confirmado y el inventario reservado—, seguro
obligatorio sobre el valor declarado y plazo de dispersión del dinero. ]]`

El **porcentaje de comisión sale de esta lista** (14 de septiembre de 2026): no es
un dato que bloquee código. La cifra real la pone la transportadora en cada
liquidación y se teclea al conciliar (`ConciliarRecaudoComando.comisionRecaudo`),
que es lo correcto — un porcentaje fijo en configuración sería una suposición sobre
algo que varía envío a envío. Sigue siendo un dato de contrato que conviene conocer,
pero para saber si el negocio pierde plata, no para poder desplegar.

## Transferencia manual

Se muestran los datos de la cuenta y una referencia única. El pedido queda en
`PAGO_PENDIENTE` con reserva de 24 horas, no de 30 minutos. El administrador
concilia el comprobante en el panel. Vencido el plazo sin comprobante, se libera.

El valor a transferir es el total del pedido, flete incluido: no se pide una
transferencia por la mercancía y otra por el envío.
