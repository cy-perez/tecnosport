# Capacidades de Skydropx y qué podemos personalizar

Verificación previa a la Fase 7, hecha el 10 de septiembre de 2026 contra la
documentación pública. Existe por la **regla dura #9**: la Fase 7 se decidió con
tres ADR escritos sobre una lectura parcial de la API, y antes de escribir la
primera línea hay que saber qué ofrece la plataforma de verdad, qué queda en
nuestras manos y qué no se puede saber sin la cuenta.

Este documento **no decide nada**. Levanta el inventario, marca la confianza de
cada dato y lista las decisiones que la verificación destapó. Lo que se decida
va a un ADR, como siempre.

## 0. Cómo leer la confianza de cada dato

La documentación de Skydropx se sirve en una página que arma el navegador, y dos
secciones —**Webhooks** y el cuerpo de **POST /shipments**— no llegan en el HTML
que se puede descargar. Eso obliga a distinguir de dónde salió cada cosa, porque
parte de lo que sigue lo resumió un modelo a partir de un documento truncado y
**un ejemplo JSON verosímil no es un ejemplo JSON real**. Es exactamente el error
que ya costó una sesión con el vector de firma de Wompi.

| Marca | Qué significa |
|---|---|
| ✅ | Aparece explícito en la documentación oficial, y lo confirmó más de una consulta |
| ⚠️ | Una sola fuente, o fuente de terceros, o dos fuentes que se contradicen |
| ❌ | No se pudo obtener. **No se implementa hasta tenerlo de la cuenta real** |

Fuentes consultadas: la documentación de la cuenta colombiana
(`app.skydropx.com/co/es-CO/api-docs`), la mexicana (`/es-MX/api-docs`), la de
`pro.skydropx.com/api-docs` —a donde redirige `docs.skydropx.com`—, las páginas
comerciales y de ayuda de `skydropx.com.co`, y un SDK no oficial de terceros. Las
páginas de ayuda (`ayuda.skydropx.com.co`, `clientes.skydropx.com`) responden 403
a una petición automatizada: lo suyo se obtuvo por buscador y por eso va ⚠️.

**Actualización del 15 de septiembre de 2026 (§6.4):** las dos fuentes que aquí se
dan por inalcanzables sí se leen **con un navegador de verdad**, y traen cosas que
ninguna otra tiene. La documentación de `sb-pro.skydropx.com/es-CO/api-docs` es una
sola página que JavaScript arma entera —los cuarenta y cinco endpoints y las cuatro
guías rápidas están en el DOM— y el centro de ayuda colombiano vive en
`help.skydropx.com.co/subcategorias-cda/api`, donde el `403` es a `fetch`, no al
navegador. Ojo con una trampa: pedirle un artículo con `fetch` desde la propia
página devuelve una plantilla genérica, siempre la misma, sin el contenido del
artículo. Hay que navegar a cada uno.

## 1. Inventario de la plataforma

Lo que la API expone, agrupado por para qué sirve. ✅ en todo el bloque: la lista
de endpoints es lo único que las tres consultas devolvieron igual.

**Autenticación.** OAuth 2.0 con credenciales de cliente.
`POST /api/v1/oauth/token`, más `revoke` e `introspect`. Token de **2 horas**,
límite de **2 peticiones por segundo**.

**Cotización.** `POST /api/v1/quotations` y `GET /api/v1/quotations/{id}`, con
`is_completed` y el arreglo `rates`. Existe una **v2** de la creación.

**Envío.** `POST /api/v1/shipments` (y v2), `GET /api/v1/shipments{/id}`,
`POST /api/v1/rate-shipments` para crear sin cotización previa (y su v2),
`POST /api/v1/shipments/{id}/cancellations` y
`POST /api/v1/shipments/{id}/protect`. Catálogos de apoyo:
`carrier_services`, `consignment_notes` y `packagings`.

**Recolección.** `GET /api/v1/pickups/coverage`, `POST /api/v1/pickups`,
`GET /api/v1/pickups{/id}` y `POST /api/v1/pickups/reschedule`.

**Seguimiento.** `GET /api/v1/shipments/tracking/{tracking_number}/{carrier_name}`
y `POST /api/v1/shipments/tracking` para reportar eventos de flota propia.

**Direcciones.** CRUD de `address_templates` y
`POST /api/v1/address_templates/{id}/verify_by_carriers`, que valida una
dirección contra las transportadoras.

**Puntos de oficina.** `GET /api/v1/office_points`, las oficinas disponibles para
una tarifa.

**Finanzas.** `GET /api/v1/finance/credits` (saldo) y
`GET /api/v1/finance/extra-charges` (cobros extra, paginados).

**Órdenes.** CRUD de `orders`, con `labels`. Es el modelo de la plataforma para
pedidos importados desde una tienda; **nosotros no lo necesitamos**, porque el
pedido es nuestro y solo le pedimos a Skydropx la guía.

## 2. Qué podemos personalizar, tramo por tramo

### 2.1 Cotización

| Palanca | Estado | Qué permite |
|---|---|---|
| Origen y destino | ✅ | `address_from` y `address_to` por cotización |
| Paquetes | ✅ | El arreglo se llama **`parcels`**, y admite **multipaquete** |
| Elegir la tarifa | ✅ | La respuesta trae todas las tarifas; el criterio es nuestro |
| Ver si la tarifa admite recaudo | ✅ | Cada tarifa trae `cash_on_delivery` |
| Plazo estimado | ✅ | Cada tarifa trae `days` |
| Campos exactos de la dirección | ✅ | Medidos el 11 de septiembre (§6) y confirmados contra el OpenAPI el 15 (§6.4) |
| Valor declarado | ✅ | `declared_amount` **dentro de cada `parcel`**, mínimo 10.000 y máximo 5.000.000 (§6.4) |

Lo que confirma `ADR-0021`: la cotización **es asíncrona** —se crea y se sondea
hasta `is_completed`—, las tarifas valen 24 horas, y la elección de la más
económica es nuestra y no de la plataforma.

Lo que `ADR-0021` no contempló: **multipaquete**. Un pedido de varias líneas
puede ir como un bulto con el peso sumado o como varios `parcels`, y la respuesta
cambia el flete. Es una decisión abierta (§5).

### 2.2 Recolección — **el tramo que ningún ADR contempla**

Este es el hallazgo grande. `ADR-0022` describe el despacho como "se pide la guía
y se guarda", y ahí se acaba. Pero una guía emitida no hace que nadie pase por el
paquete: para eso está `/pickups`, y es un tramo entero —con cobertura, fecha,
ventana horaria y reprogramación— que hoy no está ni en el modelo, ni en el
panel, ni en el plan de la fase.

| Palanca | Estado | Qué permite |
|---|---|---|
| Consultar días disponibles | ✅ | `GET /pickups/coverage` responde `200` con fechas desde que el envío lleva barrio (§6.10, §6.11) |
| Programar una recolección | ⛔ | `POST /pickups` responde `422 ECONNREFUSED at PICKUP` en los ocho intentos, tres días y tres horas distintas: el conector de la transportadora está caído, no es la hora (§6.11, §6.14). El endpoint sí responde y valida: con otro envío falla antes, en la dirección (§6.14) |
| Reprogramar | ✅ | `POST /pickups/reschedule` |
| Consultar el estado | ✅ | `GET /pickups/{id}` |
| Cuerpo exacto de la petición | ✅ | Confirmado (§6.4) y ejercido contra el sandbox (§6.6): `total_weight` entero y el envío en `success` |
| Si es obligatoria | ⚠️ | Una fuente dice que no, que la alternativa es dejar el paquete en oficina |
| Qué transportadoras la soportan | ✅ | Lo dice **`pickup`** en cada tarifa: `true` en Coordinadora, Servientrega e Inter Rapidísimo; `false` en 99 minutes y Envía, que recogen por soporte (§6.4, §6.6) |
| Consultar fechas disponibles | ✅ | Era el mismo `coverage` de arriba: respondía `422` con mensaje vacío porque **las guías se habían emitido sin barrio**, no por un defecto suyo (§6.10) |

> **Resuelto el 15 de septiembre (§6.4): gana la segunda, la del envío suelto.** La
> documentación oficial declara `pickup { reference_shipment_id, packages, total_weight,
> scheduled_from, scheduled_to }`, y `GET /pickups/coverage` exige un `shipment_id`, así
> que la recolección va **después** de emitir la guía. Se deja el párrafo porque la
> diferencia entre las dos formas —una recogida diaria para todo el día contra una
> recogida por envío— sigue siendo la que manda en el diseño del panel.

Las dos formas que se encontraron para el cuerpo de `POST /pickups` **no son
compatibles entre sí**:

- Una agrupa por dirección guardada y lista de envíos:
  `address_template_id`, `scheduled_date`, `time_window_start`/`end`,
  `shipment_ids[]`.
- La otra —SDK de terceros— va por envío suelto:
  `reference_shipment_id`, `packages`, `total_weight`,
  `scheduled_from`/`scheduled_to`.

Ninguna se implementa hasta verla en la cuenta real. La diferencia no es
cosmética: la primera obliga a tener direcciones guardadas como recurso de la
plataforma y permite una recogida diaria para todos los pedidos del día; la
segunda ata una recogida a un envío.

Y hay un detalle del negocio que conviene mirar de frente: el origen es
**Cra. 26C # 38B-31, apto. 401**, que es donde también se recoge. Un apartamento
en un cuarto piso no es un muelle de carga, y la ventana horaria de la
transportadora tiene que coincidir con que haya alguien. Eso vuelve la
reprogramación una función real del panel, no un adorno.

### 2.3 Envío y guía

| Palanca | Estado | Qué permite |
|---|---|---|
| Crear la guía desde una tarifa | ✅ | **Solo `rate_id`**, por `POST /api/v2/shipments`. ~~`quotation_id` + `rate_id`~~: `quotation_id` **no es un campo del envío** (§6.4), y v2 es la que devuelve un arreglo de envíos, que es lo que exige el multienvío (§6.10) |
| Crear sin cotizar antes | ✅ | `rate-shipments`, útil para reexpedir |
| Cancelar una guía | ✅ | `cancellations` |
| Asegurar el envío | ✅ | `protect`, por envío |
| Tipo de empaque | ✅ | Catálogo `packagings` |
| Campos de contraentrega | ✅ | No hay ninguno que declare el monto: se pide en la cotización con dos booleanos y vuelve en `on_delivery_amount` / `on_delivery_status` (§6.4) |
| Qué devuelve (rótulo, guía) | ✅ | `master_tracking_number` en el envío; `tracking_number` y `label_url` **por paquete**, en `included` (§6.4) |

Dos endpoints que los ADR no usan y deberían:

- **`cancellations`.** Hoy un pedido cancelado después de emitir la guía dejaría
  una guía viva y cobrable. `CancelarPedido` no sabe de Skydropx.
- **`protect`.** `ADR-0023` dejó como pendiente "seguro obligatorio sobre el
  valor declarado". Ahora se sabe que el seguro es **una llamada aparte y por
  envío**, es decir, una decisión nuestra con un costo por pedido, no una
  condición del contrato.

### 2.4 Seguimiento

| Palanca | Estado | Qué permite |
|---|---|---|
| Consultar por guía | ✅ | `GET /shipments/tracking/{guia}/{transportadora}`, donde la transportadora es el **código de la plataforma** y no su nombre visible —"99 minutes" es `ninetynineminutes`—, y el código no se deriva del nombre: viene en la respuesta del envío (§6.8) |
| Distinguir "sin eventos" de "guía que no existe" | ⚠️ | No se puede: los dos son `404`. Una guía tecleada a mano, sin código de transportadora, se salta y **se cuenta** en el registro de la tarea, en vez de contarse como "sin novedad" (§6.8) |
| Webhook de eventos | ✅ | Sección leída el 14 (§6.1) y ejemplos de cuerpo confirmados el 15 (§6.2, §6.4). Ojo: `data.id` es el **paquete** |
| Cabecera y algoritmo de firma | ✅ | `Authorization: HMAC <firma>`, HMAC-SHA512 sobre los bytes crudos, hex en minúsculas (§6.1) |
| Lista y nombre de los eventos | ✅ | Los doce de `ADR-0022`, en el mismo orden, declarados en el enum del OpenAPI (§6.3) |

La consulta por guía ✅ sostiene `TareaConciliacionEnvios` tal como `ADR-0022` la
planteó, y es la parte que **sí** se puede construir sin resolver el webhook. No
es poca cosa: significa que el seguimiento puede funcionar entero, con retraso,
antes de tener una sola respuesta de Skydropx sobre la firma.

### 2.5 Lo que podemos personalizar y hoy no estamos usando

- **`verify_by_carriers`**: validar la dirección de entrega contra la
  transportadora antes de cobrar. Una dirección mala es un pedido que vuelve, y
  hoy el checkout la acepta tal como se escriba.
- **`office_points`**: entregar en una oficina de la transportadora. Más barato
  que el domicilio y ya aparece en `ADR-0022` como el estado
  `delivered_to_branch`, que hoy se registra y no hace nada. Sería una tercera
  forma de entrega, junto al domicilio y la recogida en nuestro punto.
- ~~**`finance/extra-charges`**: los cobros extra que la transportadora aplica
  después.~~ **Construido el 18 de septiembre de 2026.** Es el mecanismo por el cual **un peso mal
  declarado se reliquida**, que es justo el riesgo que `docs/02-modelo-datos.md` menciona al
  prohibir inventar pesos, y hasta ese día el sobrecosto era invisible: se descuenta del crédito y el
  flete que el pedido guarda sigue siendo el de la tarifa. Ahora una tarea pregunta cada 24 horas por
  una ventana de 30 días y avisa de cada cobro **una sola vez** — el endpoint no devuelve ningún
  identificador del cargo, así que la identidad se compone (§6.16). Lo que **no** hace todavía es
  entrar en el margen del pedido: eso exige decidir si el sobrecosto vive en la guía o en el envío, y
  va con el panel administrativo.
- ~~**`finance/credits`**: el saldo.~~ **Construido el 17 de septiembre de 2026.** Si la
  cuenta se queda sin crédito no hay guías, y hasta ese día nadie se enteraría hasta que un
  despacho fallara — pasó, con la cuenta en COP 388. Ahora una tarea lo mira cada doce horas y
  avisa por debajo de 50.000, con una distinción que importa: `no se pudo preguntar` no manda
  ningún correo, porque un proveedor caído media hora no es una cuenta sin fondos.

## 3. La contraentrega, como la plantea Skydropx

Lo que cambia respecto de lo que suponía `ADR-0023`:

- ⚠️ **Hay que solicitar el servicio; no viene activo.** Existe un trámite
  ("Solicitar el servicio de pago contra entrega") que es requisito antes de que
  cualquier tarifa devuelva recaudo. Es un prerrequisito del negocio, anterior a
  todo el código.
  **Matizado el 15 de septiembre (§6.5): en este sandbox ya está activo** —la
  cotización con recaudo devuelve `on_delivery_amount` con monto—. El trámite
  seguirá haciendo falta en producción; lo que ahora se sabe es **cómo
  comprobarlo**: si `on_delivery_amount` vuelve `null` con el recaudo pedido, el
  servicio no está habilitado en esa cuenta.
- ⚠️ **Se elige qué se recauda:** solo el precio del producto, o el producto más
  el flete. Confirma la decisión de `ADR-0023` —recaudar `Pedido.total()`, flete
  incluido— y ahora se sabe que es una opción configurable y no la única forma.
- ⚠️ **El dinero recaudado se retira de dos maneras**, y esto es nuevo:
  - a **créditos Skydropx**: inmediato y **sin comisión**, gastable en envíos;
  - a **cuenta bancaria**: con **comisión financiera**, y el saldo queda
    disponible **los jueves**.
- ✅ La cobertura sale de la tarifa (`cash_on_delivery` por tarifa), que es
  exactamente lo que `ADR-0023` decidió al retirar la tabla propia.
- ❌ Mínimo, máximo, porcentaje de comisión y seguro obligatorio: **no
  confirmados**. La cifra de COP 2.000 / COP 2.000.000 que cita `ADR-0023` no se
  volvió a encontrar en fuente oficial y hay que tratarla como no verificada.

La consecuencia dura del retiro semanal cae sobre el estado `RECAUDO_CONCILIADO`:
el dinero **no llega el día de la entrega**, llega el jueves siguiente y menos una
comisión. Un pedido entregado un viernes pasa casi una semana en
`RECAUDO_PENDIENTE` sin que nada esté mal. Cualquier alerta que se construya
sobre ese estado tiene que contar jueves, no días.

## 4. Qué le cambia esta verificación a los ADR ya escritos

| ADR | Qué sigue en pie | Qué hay que revisar |
|---|---|---|
| `0021` cotización | Todo: asíncrona, 24 h, tarifa elegida por el servidor, `fail-closed` | ~~Falta decidir multipaquete; los campos de dirección siguen sin confirmar~~ Resueltos los dos. Lo que queda es el mínimo de 10.000 por bulto y que la emisión se va por **v2** (§6.4) |
| `0022` seguimiento | La conciliación programada, que se sostiene sola con el tracking por guía | **Corregido el 16 de septiembre** con seis enmiendas: el `202` que no es despacho, la forma real del endpoint de conciliación, los eventos al revés y sin texto, las varias guías por pedido, la recolección y la firma aún sin evento real. ~~Los doce estados y la firma no se pudieron re-confirmar~~: los doce, confirmados (§6.1, §6.3) |
| `0023` contraentrega | La cobertura por tarifa y el recaudo del total | El servicio hay que **solicitarlo** —y ahora se sabe reconocer cuándo no está activo: `on_delivery_amount` en `null` (§6.4)—; el retiro es semanal y con comisión; el máximo de 5.000.000 tiene fuente y la comisión sigue sin confirmar |

Ninguno se contradice de frente. `0022` es el que queda corto, y no por estar
equivocado sino por no haber mirado que entre emitir una guía y que el paquete se
mueva hay un paso con nombre propio.

## 5. Decisiones abiertas, para tomar antes de codificar

1. **Recolección programada o entrega en oficina.** Determina si la Fase 7 gana
   un agregado `Recoleccion`, una pantalla de panel y una cuarta tarea
   programada, o si el despacho termina en "alguien lleva los paquetes".
   **Sigue abierta, pero ya no por falta de datos (§6.4):** el cuerpo está
   confirmado, cada tarifa declara si admite recolección y de qué forma
   (`pickup`, `pickup_automatic`, `pickup_ocurre`, `pickup_via_support`), y se
   sabe que **99 minutos y Envía solo recogen por soporte**, mientras
   Coordinadora, Inter Rapidísimo y Servientrega sí responden por API. Con
   corte a las 12:00 y sin fines de semana.
   **Ejercida contra el sandbox el 15, el 16 y el 17 de septiembre (§6.6, §6.7, §6.10,
   §6.11)**: el endpoint valida, exige el envío en `success` y exige el barrio del origen,
   que solo llega si se mandó en la cotización.
   **Y con el barrio puesto, la decisión dejó de estar en nuestras manos**: `POST /pickups`
   responde `422 ECONNREFUSED at PICKUP` en los **nueve** intentos, en **cuatro días** y cuatro
   horas distintas, con cuatro envíos distintos (§6.11, §6.14, §6.17). El conector de la transportadora está caído y no hay
   fecha. **Hoy la recolección se programa a mano en el panel de Skydropx**, y esto no se
   puede cerrar construyendo.
   ~~Dos cosas que la decisión ya puede dar por ciertas: **la cobertura de fechas no
   se puede ofrecer** (su endpoint no responde)~~ **La cobertura sí se puede ofrecer**:
   `GET /pickups/coverage` responde `200` con fechas reales desde que el envío lleva barrio
   (§6.10). Lo que se mantiene es que **99 minutos y Envía no recogen por API** — y Envía es
   la más barata de las que **sí emiten** en esta cuenta, o sea la que el selector por precio
   acaba eligiendo. Si el despacho elige siempre la más barata, va a acabar pidiendo las
   recolecciones por fuera de la API.
2. ~~**Un bulto o varios.**~~ **Decidido el 11 de septiembre de 2026: un `parcel`
   por variante.** Es lo que el modelo ya sabe —cada variante tiene su `Paquete`
   con peso y medidas reales— y evita inventar las dimensiones de una caja
   combinada. Pesa además que el peso sumado se saldría del tope de 8 kg de
   Envía y dejaría transportadoras fuera. Con él se decidió el valor declarado:
   **el total de lo que va en cada bulto**, no el mínimo ni el 2.500 por omisión,
   porque la transportadora responde hasta lo declarado.
3. **Asegurar los envíos** (`protect`), y con qué criterio. Un celular no es una
   camiseta. Con precio desde el 15 de septiembre (§6.4): cuesta un fijo más un
   porcentaje del valor declarado.
4. **Validar la dirección** con `verify_by_carriers` antes de cobrar, o no.
5. **Entrega en oficina** como tercera forma de entrega, o no en esta fase. La API
   quedó confirmada el 15 de septiembre (§6.2) y ~~ninguna transportadora del sandbox la
   ofrece hoy, así que la decisión sigue abierta sin poder probarse~~ **se volvió a medir el
   17 con las tarifas vivas (§6.12)**: las cuatro que cotizan declaran
   `office_delivery: false` y su catálogo de puntos responde `200` con cero puntos. Ya no es
   una suposición sobre tarifas muertas, y con eso **la decisión se puede tomar: no se
   construye**, porque sería una pantalla a la que nadie puede llegar. Queda escrita una
   advertencia para el día que alguna transportadora la ofrezca: la lista de oficinas de
   Inter Rapidísimo incluye oficinas de ciudades vecinas, así que la elegida hay que
   cotejarla contra el DANE del pedido antes de aceptarla.
6. ~~**Dónde cae el recaudo**: créditos sin comisión o banco con comisión los
   jueves. Es una decisión contable, no técnica.~~ **Decidido el 18 de septiembre de 2026
   (`ADR-0043`), y la respuesta no es ninguna de las dos: la cuenta tiene las dos, y cada
   envío registra por cuál entró.**

   Lo que la pregunta daba por hecho era falso, y medirlo cambió la forma: **las dos
   modalidades son maneras de retirar el saldo acumulado, no un campo del envío** (§3). El
   cuerpo de la guía no lleva nada que diga dónde cae el dinero, así que una pantalla que
   "eligiera" por envío sería una intención que nadie ejecuta — el mismo error que el método
   de pago elegido por el comprador, que resultó ser una intención (`docs/09`, 2026-09-14).

   Por eso la modalidad se pide **al conciliar**, que es cuando quien concilia la está viendo
   en el panel. La única regla que el dominio comprueba es la que sí es suya: **los créditos
   no cobran comisión**, así que una conciliación a créditos con un número encima se rechaza.

   **No desbloquea la conciliación automática**: sigue faltando el vocabulario de
   `on_delivery_status` (§6.17). Lo que gana es que, cuando ese dato aparezca, la comisión ya
   no será el segundo bloqueo.
7. ~~**Cancelar la guía** cuando se cancela un pedido ya despachado.~~ **Decidido el 17 de
   septiembre de 2026.** El endpoint y su cuerpo quedaron confirmados el 15 (§6.4); lo que
   faltaba —cuándo se dispara y quién lo autoriza— se cerró mirando el grafo de estados y no
   la API. **`EstadoPedido` no permite `DESPACHADO → CANCELADO`**: de `DESPACHADO` solo salen
   `ENTREGADO` y `RECHAZADO_EN_ENTREGA`. O sea que el enunciado de esta decisión describía un
   camino que el dominio niega, y por eso llevaba tres sesiones sin poder cerrarse.

   Lo alcanzable hoy es otra cosa, y es un agujero de verdad: la guía se emite estando
   `EN_PREPARACION`, `EN_PREPARACION → CANCELADO` **sí** es una transición válida, y
   `CancelarPedido` no toca `Envio` ni `EmisionDeGuia` en ninguna línea. Cancelar desde el
   panel un pedido con la guía ya emitida deja **una guía viva y cobrable**, y el paquete
   puede recogerse igual. Ahí se dispara la cancelación —dentro de `CancelarPedido`— y la
   autoriza quien ya autoriza cancelar: no hace falta un permiso nuevo para deshacer algo que
   se acaba de hacer.

   **El grafo no se toca.** Un paquete que ya salió tiene dos salidas modeladas
   —`RECHAZADO_EN_ENTREGA` y `DEVUELTO`— y abrir `DESPACHADO → CANCELADO` crearía un estado
   terminal para algo que sigue moviéndose. Y **cancelar la guía no puede tumbar la
   cancelación del pedido**: si la plataforma no responde, el pedido se cancela igual, el
   dinero se devuelve igual, y la guía que quedó viva va a la bandeja de revisión. Un
   comprador sin reintegro porque un proveedor no contestó es peor que una guía huérfana que
   alguien anula a mano.
8. ~~**v1 o v2** en cotizaciones y envíos.~~ **Decidido el 15 de septiembre de 2026:
   `POST /api/v2/shipments`** (§6.4). No es preferencia: v2 siempre devuelve un arreglo
   de envíos, y en Colombia ninguna transportadora admite multipaquete, así que un pedido
   de dos variantes son dos guías que v1 no puede devolver.
9. ~~**El modelo de `Envio` frente al multienvío**: un `Envio` por bulto, uno con varias
   guías, o consolidar en un bulto y perder las medidas reales.~~ **Decidido el 16 de septiembre
   de 2026 (`ADR-0031`): un envío, varias guías.** Cada guía lleva su paquete, su costo y su
   propio rastro de eventos, porque ninguna transportadora colombiana admite multipaquete y cada
   bulto se mueve solo. Esta línea llevaba dos días marcada como abierta con la decisión ya
   tomada y escrita en un ADR (§6.3, §6.4).
10. ~~**Qué se hace con el bulto que declara menos de 10.000**, que no cotiza y tumba la
    cotización entera (§6.4).~~ **Decidido el 17 de septiembre de 2026 (`ADR-0035`): se eleva
    al mínimo asegurable**, en `ArmadorDeBultos` y no en el mapeador, para que el bulto que
    circula por `application` diga lo que de verdad se declara.

## 6. Lo que se cerró con la cuenta real

### Estado al 14 de septiembre de 2026: qué está en manos de Skydropx

Léase esto antes de volver a probar nada contra el sandbox. Lo que sigue **ya
se investigó hasta el fondo que permite la cuenta** y el detalle está en §6.1;
repetir las pruebas no va a cambiar el resultado, porque el fallo está del lado
de Skydropx y **la solicitud ya se les envió el 14 de septiembre de 2026**.

> **Corregido el 15 de septiembre en §6.4, y es la lección más cara de la fase.**
> Dos de las filas que esta tabla da por "bloqueadas por Skydropx" eran nuestras:
> el valor declarado iba en el campo equivocado. La frase de arriba —"repetir las
> pruebas no va a cambiar el resultado"— **es justo la que hay que desconfiar**:
> lo que no cambia el resultado es repetir la misma prueba. Lo destapó variar algo
> que nunca se había variado.

| Tema | Estado | Qué falta y de quién depende |
|---|---|---|
| Firma del webhook | ✅ Resuelto con la documentación oficial | Implementar HMAC‑SHA512 sobre los bytes crudos, cabecera `Authorization: HMAC <firma>`; comprobar contra un evento real cuando haya guía. **Nuestro.** |
| DHL en el panel | ✅ Resuelto: solo internacional | Nada. No aplica al negocio. |
| Servientrega, Envía, Coordinadora sin tarifa | ✅ **Resuelto el 15 de septiembre, y era nuestro** | No era de ellos: `declared_amount` va **dentro de cada `parcel`** y el mapeador lo mandaba fuera, con otro nombre. Con el campo en su sitio las tres cotizan (§6.4). ~~**Retirar la solicitud enviada el 14 de septiembre.**~~ **Retiro pedido el 21 de septiembre**, junto con los otros dos asuntos abiertos: `docs/tramites/2026-09-19-skydropx.md`. Sin respuesta todavía |
| Inter Rapidísimo `to_f >= 25` | ✅ **Resuelto el 15 de septiembre** | Tampoco era la verificación de origen: era el mismo valor declarado ausente. Con el campo bien puesto responde `no_coverage` (§6.4). La plantilla `535bd77b-fce2-46f0-9354-56b9c42fba5f` sigue en `process` y ya no bloquea nada. |
| Créditos del sandbox | ⚠️ **Se agotaron otra vez el 16 de septiembre** | Skydropx depositó **49.000 COP a mano** el 15 (`transaction_source: Skydropx`, etiqueta `USO_INTERNO`, comentario "para realizar peruebas"); nunca funcionó la vía de autoservicio. Las emisiones de prueba lo bajaron a **388 COP**, por debajo de la tarifa más barata con recolección por API (5.991). **Bloquea la recolección (§6.7).** |
| Guía por `POST /shipments` | ✅ **Funciona. Emitida el 15 de septiembre** | Guía `873837506712` de Servientrega, por el camino que `ADR-0021` diseñó: `quotation_id` + `rate_id`. El `422 declared_amount` que parecía bloquearlo **es de la tarifa de 99 minutes**, no del endpoint (§6.3). |
| `422 declared_amount` en tarifas de 99 minutes | ⚠️ Acotado, y esquivable | Con el mismo cuerpo, una tarifa de Servientrega da `202` y una de 99 minutes da `422`. Es de esa transportadora. **Nuestro**: no elegir esa tarifa, o preguntarles (§6.3). |
| Guía por `POST /rate/shipments` | ⛔ No se usa | Emitió la guía `3838859118`, pero recotiza y agrega un recargo de recaudo de 8.925 que nadie pidió, un 85 % más caro (§6.2). Con `POST /shipments` funcionando, ya no hace falta. |
| Webhook real y eventos de seguimiento | ✅ **Ciclo completo capturado** | `auto_advance` avanzó la guía `picked_up → in_transit → last_mile → delivered`, uno por minuto. La forma de los eventos está medida (§6.3). |

Mientras Skydropx responde, lo que sí se puede hacer sin tocar el sandbox:
implementar la verificación HMAC con pruebas de vector propio, decidir el
tratamiento del valor declarado bajo 10.000 COP, y decidir si el origen pasa a
ser una plantilla de dirección con identificador (§6.1, Inter Rapidísimo).

**Sesión del 11 de septiembre de 2026 contra el sandbox**, con las credenciales
de la cuenta. Todo lo que sigue se comprobó pidiendo cotizaciones de verdad: no
hay una sola línea deducida de la documentación. Las respuestas capturadas —
recortadas, no reescritas— viven como fixtures en
`MapeadorCotizacionSkydropxV1Test`.

### Confirmado

- ✅ **El host de pruebas es `sb-pro.skydropx.com`.** Es el único de los tres
  candidatos que autentica: `api-pro.skydropx.com` y `pro.skydropx.com` devuelven
  `invalid_client` con estas credenciales. El panel dice que el de producción es
  `api-pro.skydropx.com`, y eso **queda sin comprobar** hasta tener credenciales
  de producción.
- ✅ **El token acepta form-encoded y JSON**, devuelve `expires_in: 7200` y
  `scope: default`. Lo que ya estaba implementado siguiendo el RFC es correcto.
- ✅ **El cuerpo de la cotización va envuelto en `quotation`.** Plano da 400.
- ✅ **`postal_code` es el código DANE de cinco dígitos**, no el postal real. Un
  postal de seis dígitos devuelve `422 "no existe"`. Ninguna fuente lo decía, y es
  el hallazgo que más caro habría costado adivinar.
- ✅ **`area_level1` (departamento) y `area_level2` (ciudad) son obligatorios**;
  `area_level3`, `street1`, `name` y `phone` no lo son para cotizar. Los nombres
  se **normalizan**: "Bogotá, D.C." y "Bogotá" caen en la misma cotización, así que
  las grafías de DIVIPOLA que ya manda el frontend sirven tal cual. Por eso el
  origen ganó `ORIGEN_DEPARTAMENTO` y `ORIGEN_CIUDAD`.
- ✅ **El peso va en kilos.** Comprobado por contradicción: mandando `1000` las
  seis transportadoras responden `max_weight debe ser menor que o igual a
  60 / 150 / 200 / 8 / 500 / 25`. Esos son, de paso, los topes reales por
  transportadora — y **Envía/paquete terrestre se cae en 8 kg**.
- ✅ **Cobran peso volumétrico.** Un 30×25×10 de 1 kg real se cotizó como 3 kg.
- ✅ **El flete es `total`, no `amount`.** La diferencia son los `extra_fees`, el
  seguro entre ellos: `amount` 18.356 contra `total` 19.616, con
  `extra_fees: [{code: "insurance", value: 1260}]`. Cobrar `amount` regalaría la
  diferencia en cada envío.
- ~~✅ **`declared_value` va en cada `parcel` y por omisión queda en COP 2.500.**
  `declared_amount` es obligatorio a nivel de cotización, aparte.~~
  **Falso desde el 14 de septiembre, corregido el 15 (§6.4): el campo que se lee es
  `declared_amount` y va dentro de cada `parcel`.** `declared_value` y el
  `declared_amount` de cotización se ignoran los dos. Era cierto cuando se midió el 11;
  dejó de serlo cuando Skydropx movió el campo.
- ✅ **Seis transportadoras en el sandbox**: Inter Rapidísimo, Servientrega,
  Coordinadora, Envía (mercancía y paquete terrestre) y 99 minutes.
- ✅ **Los montos vienen como cadena y los tipos alternan** entre una tarifa y la
  siguiente: `weight` es `"0.0"` en una y `3` en otra. Cualquier lectura con tipo
  fijo se rompe con la tarifa de al lado.
- ✅ **`vat_fee` existe por tarifa** y en el sandbox llega en `"0.0"`. No alcanza
  para cerrar el dato de negocio del IVA del flete, pero sí dice dónde mirarlo.

### Dos trampas que no estaban en ninguna documentación

- ⚠️ **Skydropx deduplica cotizaciones por contenido.** El mismo cuerpo devuelve
  el mismo `id` —comprobado cuatro veces seguidas— con el mismo resultado. Un
  fallo transitorio de una transportadora **queda congelado** contra ese carrito y
  esa dirección: reintentar no lo arregla. Y la petición repetida **no se vuelve a
  validar**, así que un cuerpo inválido puede responder 201 solo porque uno
  parecido pasó antes.
- ⚠️ **Las transportadoras responden distinto en momentos distintos.** Con el
  mismo cuerpo se obtuvieron tarifas en una sesión y `tariff_price_not_found` en
  otra. Servientrega y Envía lo devuelven de forma constante. **Resuelto en parte
  el 14 de septiembre** (§6.1): no es un plan sin activar, es la API de cada
  transportadora rechazando la petición que Skydropx le arma, y el error viene
  escrito en `error_messages`.

### Sigue sin confirmarse

- ❌ **El host de producción**, hasta la primera cotización con credenciales de
  producción.
- ✅ **La cobertura de contraentrega, resuelta el 11 de septiembre de 2026 por otra
  vía.** No hay un campo por tarifa que la declare —eso sigue siendo cierto— pero
  **pedir la cotización con `cash_on_delivery: true` sí discrimina**: sin él
  ninguna tarifa se queja de recaudo; con él, las que no lo admiten se caen con
  restricciones propias (`declared_amount debe ser mayor que o igual a 10000` en
  Coordinadora y Envía, `5000` en Servientrega, `max_weight debe ser menor que o
  igual a 1`) y las que sí sobreviven, al mismo precio. Comprobado con 99 minutes
  dentro de Medellín: 10.540 con recaudo y sin él.

  **Sobrevivir a una cotización con recaudo es la señal de cobertura**, y es de lo
  que depende `MetodosDePagoDisponibles` desde la Fase 7, paso 6.
- ✅ **El nombre del campo del monto a recaudar: no existe, resuelto el 15 de
  septiembre (§6.4).** El monto no se declara — sale calculado en
  `on_delivery_amount` = valor declarado + flete si `recipient_pays_shipping`. Lo que
  sigue, escrito el 11, describe bien la búsqueda y la conclusión a la que llegó:
- ❌ **El nombre del campo del monto a recaudar.** Se probaron diez grafías
  —`on_delivery_amount`, `cash_on_delivery_amount`, `collection_amount`,
  `amount_to_collect`, `cod_amount`, `collect_amount`, `value_to_collect`,
  `total_to_collect`, `cash_on_delivery_value`, `payment_amount`—, en la
  cotización y dentro del bulto, y también `cash_on_delivery` como objeto.
  Ninguna quedó reflejada: `on_delivery_amount` siempre vuelve `null`. La
  conclusión es que **ese dato no se declara al cotizar**, sino al crear el
  envío, que es el paso 7.
- ⚠️ **Los límites del recaudo, parcialmente.** Pidiendo `cash_on_delivery: true`
  aparecieron los primeros mínimos con fuente: **valor declarado ≥ 10.000 en
  Coordinadora y Envía, ≥ 5.000 en Servientrega**. La comisión, el máximo y el
  seguro siguen sin confirmar, y la cifra de COP 2.000 / COP 2.000.000 que cita
  `ADR-0023` sigue sin aparecer en ninguna fuente.
- ✅ **La forma de `POST /shipments`, medida el 12 de septiembre de 2026.** El
  cuerpo va envuelto en `shipment` y lleva ~~`quotation_id`,~~ `rate_id`,
  `address_from`, `address_to` y `parcels`. Lo dijo el propio 422 al mandarle
  solo los dos identificadores, y trae **dos exigencias que no estaban en
  ninguna parte**:

  - Las direcciones piden además **`email` y `reference`**, las dos obligatorias
    y en los dos extremos. El correo del comprador ya lo tenemos; `reference`
    —una referencia para encontrar el sitio— no se pide hoy en el checkout, y el
    origen tampoco tiene correo configurado.
  - Cada bulto pide **`package_type` y `package_content`**: qué tipo de empaque
    es y qué va dentro, en texto.

  > **`quotation_id` no va, y este párrafo es el origen del error**: lo desmintió el
  > OpenAPI en §6.4 y lo confirmó la emisión real en §6.10. Se coló porque aquel `422`
  > enumeraba los campos que el envío **hereda** de la cotización, no los que hay que
  > mandarle. Un mensaje de error es una pista sobre qué falta, no un esquema.

  **Lo que se decidió el 14 de septiembre de 2026** para los tres datos que esto
  dejó abiertos (el detalle y el porqué, en `docs/09-plan-de-arranque.md`, paso 7):

  | Campo | Qué se manda |
  |---|---|
  | `address_to.reference` | El `indicaciones` del pedido, que sigue siendo opcional; si viene vacío, `Sin indicaciones adicionales` |
  | `address_from.email` | `contacto@tecnosport.co`, ya implementado como `ORIGEN_CORREO` |
  | `package_content` | Genérico por línea de catálogo, **y el mapa vive en `ContenidoDeclarado` (`domain/envio`), no en esta tabla** — coincide con el contenido real para sostener una reclamación, sin anunciar en la etiqueta qué va dentro |

  ~~`package_type` sigue sin decidirse porque **no es un dato de negocio sino un
  valor del catálogo de Skydropx**, y su lista de valores válidos no se ha podido
  leer sin emitir una guía.~~ **El catálogo se leyó el 15 de septiembre sin emitir
  nada (§6.5)**: son 59 códigos de embalaje de la ONU y el que aplica es **`4G`,
  caja de cartón**. Sigue sin ser un dato de negocio; es una elección de operación,
  y ya está tomada.

  **Por qué el mapa de `package_content` ya no se escribe aquí.** Esta tabla lo
  tuvo, y decía `CELULARES` → "Equipo de telefonía móvil". Ese mismo 14 de
  septiembre, `V38` renombró la línea a `TECNOLOGIA` y le colgó diez categorías
  más; aplicado tal cual, **un proyector habría viajado declarado como telefonía
  móvil**, que es justo lo que la decisión existía para evitar. Un documento no se
  entera de un renombre. `ContenidoDeclarado` es un `switch` exhaustivo **sin
  `default`**, así que una línea nueva no compila hasta que alguien decida qué
  dice su etiqueta, y el atajo de agregar un `default` lo atrapa su prueba. El
  relato completo está en `docs/09-plan-de-arranque.md`, "La decisión que
  envejeció en un día".

- ⛔ **No se pudo emitir ninguna guía: la cuenta no tiene créditos.** El intento
  con el cuerpo completo respondió
  `422 No tienes los créditos suficientes para este envío. Agrega créditos y
  continúa.` No se creó nada ni se consumió saldo. Junto a ese mensaje aparece
  `Valor declarado es obligatorio`, que **no** cede con ninguna de siete grafías
  —`declared_value` y `declared_amount`, en el envío y en el bulto, más
  `insurance` y `protect`—; tiene la forma de un error de la transportadora, así
  que lo más probable es que sea ruido de una validación previa que no llega a
  ejecutarse sin saldo.

  **Cuánto falta, medido el 12 de septiembre:** el panel muestra 1.000 de saldo
  y **la guía más barata que la cuenta puede cotizar cuesta 9.540** —99 minutes,
  un sobre de 20×15×2 y 100 gramos dentro de Medellín, que es el piso: la única
  transportadora que responde hoy y su tarifa mínima—. Se intentó emitir esa
  misma y devolvió el mismo error, así que con 1.000 no se puede emitir ni una.
  ~~No existe endpoint de saldo: se probaron siete rutas y las siete dan 404.~~
  **Corregido el 14 de septiembre:** sí existe, `GET /api/v1/finance/credits`,
  y responde `{"balance": 1000.0, "currency": "COP"}`. Las siete rutas del día 12
  no eran esa. `GET /api/v1/transaction_stats` muestra el movimiento: un depósito
  de 1.000 con origen `sandbox_registration` del 11 de septiembre.

  **Cerrado el 14 de septiembre de 2026, del único modo posible:** se agotó la
  vía de autoservicio (§6.1, "Créditos del sandbox") y **se envió la solicitud a
  Skydropx** pidiendo unos 100.000 COP de prueba o la acreditación del pago que
  Mercado Pago aprobó. Sin eso no hay guía, y sin guía no hay webhook que firmar
  ni evento que mapear: el resto del paso 7 de la Fase 7 está topado por la
  cuenta, no por el código.

- ✅ **La firma del webhook, confirmada el 14 de septiembre de 2026 en la
  documentación oficial** (sección *Webhooks* de `sb-pro.skydropx.com/es-CO/api-docs`,
  que el navegador arma con JavaScript y por eso no llegaba en la descarga; el
  texto está en el HTML servido, no en el OpenAPI). Detalle en §6.1. Lo que
  sigue pendiente es **comprobarla contra un evento real**, que exige una guía, y
  la guía exige créditos.
- ❌ **El cuerpo de `POST /pickups`**: sigue sin confirmarse hasta que exista un
  envío real.
- ❌ **La comisión financiera del retiro a banco.** Es comercial, no técnica: va
  por el ejecutivo de cuenta.

### 6.1 Sesión del 14 de septiembre de 2026: las cuatro preguntas para Skydropx

Se habían acumulado cuatro preguntas para el soporte de Skydropx. Antes de
mandarlas se volvió a leer la documentación —esta vez el OpenAPI completo, que
está en `https://sb-pro.skydropx.com/es-CO/api-docs.json`, y el texto de la
página, que sí trae la sección de webhooks— y se corrieron treinta y dos
cotizaciones nuevas contra el sandbox variando una cosa a la vez. Lo que sigue
es lo que cambia.

**Dos de las cuatro no hay que preguntarlas: la respuesta ya estaba escrita.**

#### La firma del webhook — resuelta, sin preguntar

La documentación oficial dice, literalmente:

- La cabecera por omisión es **`Authorization`**, y **su nombre se configura**
  en el panel (entre 3 y 25 caracteres, sin espacios). La variable
  `SKYDROPX_CABECERA_FIRMA` que ya existe es exactamente lo que hacía falta.
- Dos modos: `Authorization: Bearer <token>` con un token estático que da
  Skydropx (lo llaman "menos seguro"), o **`Authorization: HMAC <firma>`**.
- La firma es **HMAC con SHA-512** (cita el RFC 6234), con la clave secreta
  propia, **sobre el cuerpo crudo de la petición —"bytes exactos, sin
  formato"— y codificada en hexadecimal en minúsculas**.
- Se activa en el panel, en **Conexiones > Webhooks**. La URL debe ser HTTPS.
- El cuerpo es JSON:API — `data.type` es `"packages"` para los eventos de
  envío, con `attributes.status` (`delivered`, `in_return`…),
  `tracking_number`, `tracking_url_provider`, `label_url`, `returned` y
  `returned_status`, y `relationships.shipment.data.id`. Los dos últimos
  **siempre vienen**, aunque el envío no esté en retorno. También llegan
  eventos de tipo `orders`, `quotation`, `rate`, `extra_charges` y `pickups`,
  así que el lector tiene que **filtrar por `data.type`** antes de buscar la
  guía. Y aviso explícito: durante un retorno, las suscripciones **siguen
  disparando el estado operativo real** (`in_transit`, `last_mile`), no
  `in_return`; el retorno se lee en `returned: true`.
- Por privacidad el evento no trae todo: hay que seguir el `links.related`
  para el detalle.

Con eso `VerificadorFirmaEnvioPendiente` ya puede dejar de rechazar todo:
`Mac.getInstance("HmacSHA512")` sobre los bytes crudos, comparación en tiempo
constante, prefijo `HMAC ` recortado. La prueba contra un evento real sigue
siendo obligatoria antes de producción —el cuerpo se recibe como `String` y la
codificación de los bytes importa—, pero ya no hay nada que inventar.

**Implementado el 14 de septiembre de 2026** en `VerificadorFirmaEnvioHmac`, y
dos cosas que aparecieron al escribirlo:

- **El secreto no existía en ninguna parte.** `PropiedadesWebhookEnvio` solo
  llevaba el nombre de la cabecera; faltaba `SKYDROPX_SECRETO_WEBHOOK`, que
  `docs/07-infra-gcp.md` ya listaba y nadie había conectado. Va con marcador de
  desarrollo, como las credenciales: mientras valga el marcador, **el verificador
  sigue rechazando todos los eventos** — la diferencia es que ahora falta un
  secreto del panel y no un algoritmo, y eso es una variable de entorno.
- **La codificación del cuerpo era un fallo esperando.** Llegaba como
  `@RequestBody String`, y con `application/json` sin `charset` la decodificación
  la elige el convertidor de Spring: si no fuera UTF-8, volver a codificar esa
  cadena para el HMAC daría bytes distintos de los firmados en cuanto el evento
  trajera una tilde. El controlador recibe ahora `byte[]` y decodifica UTF-8
  explícitamente. Es el mismo género del `getWriter()` en ISO-8859-1 que ya
  mordió en la Fase 4 (`apps/api/CLAUDE.md`).

**El valor esperado de la prueba sale del RFC 4231**, que publica vectores de
HMAC-SHA-512 —caso 2: clave `Jefe`, datos `what do ya want for nothing?`—, y no
de calcularlo con el mismo `Mac` que usa el adaptador: así las dos partes no se
equivocan juntas. Comprobado cambiando el algoritmo a SHA-256 a propósito:
cuatro de las once pruebas fallan.

#### DHL — resuelta, sin preguntar

`GET /api/v1/shipments/carrier_services` —la ruta real del catálogo; la de la
sección 1 sin el prefijo `shipments/` da 404— lista los siete servicios de la
cuenta, y el de DHL es **`International Worldwide` con `is_national: false`**.
Los otros seis son nacionales: Coordinadora Standard, Envía Paquete Terrestre,
Envía Mercancía Terrestre, Inter Rapidísimo Standard, 99 minutes Next day y
Servientrega Standard. Se comprobó por los dos lados: una cotización nacional
con `requested_carriers: ["dhl"]` responde `found_carriers: ["dhl"]` y **cero
tarifas**, y una internacional a Miami sí la trae —con
`CARRIER_RESPONSE_ERROR ... status code 401 ... at DUTIES_AND_TAXES`, que en el
sandbox es esperable—. DHL figura en el panel porque la cuenta lo tiene para
exportar; TecnoSport no exporta. **No hay nada roto.**

De paso: `requested_carriers` es el campo oficial para acotar la cotización a
unas transportadoras, y la respuesta lo confirma en
`quotation_scope.{carriers_scoped_to, found_carriers, not_found_carriers}`.

**Las otras dos sí van a Skydropx, y ahora con evidencia en vez de con una
sospecha.**

#### Servientrega, Envía y —novedad— Coordinadora: el error lo escribe la transportadora

`tariff_price_not_found` es, según el OpenAPI, "tarifa sin precio", y hoy llega
acompañado de `error_messages` que el día 11 no venían:

| Transportadora | Estado | `error_messages` |
|---|---|---|
| Servientrega Standard | `tariff_price_not_found` | `CARRIER_RESPONSE_ERROR: External carrier API service error: status code 400 reason: The request is invalid. at SHIPPING` |
| Envía Paquete Terrestre | `tariff_price_not_found` | a veces vacío, a veces `... status code 400 reason: Falta Valor_Declarado. at SHIPPING` |
| Envía Mercancía Terrestre | `not_applicable` | `longer_side debe ser mayor que 45`, `max_weight debe ser mayor que 9` (restricción propia: es para bultos grandes); con 40×30×25 y 12 kg pasa a `Falta Valor_Declarado` |
| Coordinadora Standard | `not_applicable` | `CARRIER_RESPONSE_ERROR: External carrier API service error: La valoración de la guía es menor a la valoración mínima por guía del producto` |

Los tres mensajes vienen de la API de la transportadora, no de una validación
de Skydropx, y **ninguno cambia con nada que esté en nuestras manos**: se probó
con valor declarado de 10.000, 25.000, 50.000, 100.000 y 1.000.000; con
`declared_value` como número, como cadena y ausente del bulto; con
`package_protected: true`; con todos los campos de dirección (nombre,
teléfono, correo, referencia, barrio) en los dos extremos; con destino Bogotá,
Medellín y Cali; con el endpoint v2. Siempre igual.

La pista fuerte es Coordinadora: **el 11 de septiembre cotizó 19.616 con un
valor declarado de 2.500** (la captura vive en `MapeadorCotizacionSkydropxV1Test`)
y hoy dice que un valor de 1.000.000 es "menor al mínimo". Que Envía diga a la
vez "falta valor declarado" apunta a lo mismo: **entre el 11 y el 14 de
septiembre el sandbox dejó de reenviar el valor declarado a las
transportadoras**. Encaja con otro cambio del mismo intervalo: el 11 se cotizaba
con 2.500 y hoy `POST /quotations` responde
`422 {"errors":{"declared_amount":["El valor declarado debe ser mayor o igual a 10000"]}}`
—el mínimo asegurable de 10.000 que el centro de ayuda ya mencionaba y que ahora
se valida en la entrada—. Y en el eco de la cotización el bulto vuelve como
`{"package_protected": true, "declared_value": "10000.0", "protection_value": 0}`:
si lo que viaja a la transportadora es `protection_value`, viaja en cero. Es una
hipótesis; el diagnóstico es de ellos.

Lo de Servientrega es distinto: `The request is invalid` con estado 400 es la
API de Servientrega rechazando la petición entera, y con destino Cali responde
`no_coverage` limpio, así que la ruta sí se evalúa. Eso huele a credenciales o
a contrato de Servientrega sin configurar para esta cuenta de sandbox.

**Consecuencia para el código, ya:** el mínimo de 10.000 en `declared_amount`
es una regla de entrada. `MapeadorCotizacionSkydropxV1` manda
`valorDeclaradoTotal()` tal cual, y un pedido de una media de 8.000 pesos
recibiría un 422 y se quedaría sin envío a domicilio. Hay que decidir si el
valor declarado se eleva al mínimo asegurable —declarar más de lo que vale no
es mentir a la transportadora, es asegurar por más— o si ese pedido se ofrece
solo con recogida. Es dato de negocio y va en el ADR, no aquí.

#### Inter Rapidísimo y `to_f debe ser mayor que o igual a 25`

El mensaje es un error de Rails con el nombre del atributo perdido: `to_f` es
la conversión a decimal, y lo que se estaba validando era `algo.to_f >= 25`.
Se verificó que **no depende de nada del paquete ni del destino**: falla igual
con 0,1 kg y con 26 kg, con 20×15×2 y con 40×30×25, con valor declarado de
10.000 y de 1.000.000, a Bogotá, a Medellín y a Cali, por v1 y por v2. El
`weight` de su tarifa vuelve siempre `"0.0"`, o sea que la restricción se
evalúa **antes** de calcular el peso: es una precondición de la cuenta, no del
envío.

Lo que sí es distinto en Inter Rapidísimo, y solo en ella: su tarifa es la
única que trae **`requires_origin_verification: true`**, y el centro de ayuda
colombiano tiene dos artículos que lo explican —"Cómo crear envíos con Inter
Rapidísimo vía API" y "Cómo activar Inter Rapidísimo en mi cuenta"—: **antes de
cotizar o crear un envío hay que guardar la dirección de origen como
`address_template` y pedir su verificación**, la transportadora valida la
cobertura **en hasta dos días hábiles**, y desde entonces se cotiza con
`address_from.address_template_id`.

Se hizo ese trámite en la sesión: se creó la plantilla
`535bd77b-fce2-46f0-9354-56b9c42fba5f` ("TecnoSport origen (prueba)", tipo
`from`, con la dirección real de despacho) y se pidió
`verify_by_carriers` con `["interrapidisimo"]`; respondió `202` y la plantilla
quedó con `verified_carriers: [{carrier_name: "interrapidisimo", status:
"pending_to_send"}]`. Cotizar de inmediato con el `address_template_id` dio el
mismo `to_f`, que es lo esperado con la verificación pendiente. **Hay que
volver a cotizar el 16 de septiembre o después**: si con el origen verificado
Inter Rapidísimo cotiza, el `to_f` era la verificación pendiente mal
enunciada; si sigue igual, es un error de ellos y va con la evidencia de arriba.

Nota para el diseño: si la cotización de Inter Rapidísimo exige una plantilla
de origen verificada, el origen deja de ser solo cinco variables de entorno y
pasa a ser también **un recurso en Skydropx con un identificador**, que habrá
que configurar (`SKYDROPX_ORIGEN_TEMPLATE_ID` o equivalente) y mandar en
`address_from`. Eso toca `OrigenDespacho` y el mapeador; se decide en el ADR
cuando la verificación responda.

#### Otras dos cosas que el OpenAPI aclaró de paso

- **`POST /shipments` en sandbox acepta `auto_advance: true`**, que "simula la
  progresión automática del tracking" (`created → picked_up → in_transit → …`).
  Es la forma de ver webhooks de verdad sin esperar a que un paquete se mueva:
  en cuanto haya créditos, una guía con `auto_advance` dispara el ciclo entero.
- El rastreo por guía es `GET /api/v1/shipments/tracking?tracking_number=…&carrier_name=…`
  —con parámetros de consulta, no en la ruta como decía la sección 1— y
  devuelve una lista de eventos con `status`, `description`,
  `event_description`, `location` y `date`. `event_description` "coincide con
  la enviada por el webhook", que es lo que permite que la conciliación y el
  webhook escriban el mismo `EventoSeguimiento`.

#### Qué se le pregunta a Skydropx, entonces

1. Servientrega, Envía y Coordinadora: los tres `error_messages` de la tabla,
   con los identificadores de cotización `addd6512-fbbc-4a96-9ea5-c5e6aba534ed`
   (Bogotá, 1 kg, declarado 10.000) y `cb4923ce-826d-4632-968a-0c96d605c6f4`
   (declarado 1.000.000), y la cotización del 11 de septiembre en que
   Coordinadora sí respondió, `19526bde-1c4e-4a3f-9f0e-3f2b7a51c9d2`. La
   pregunta concreta: **¿el sandbox reenvía el valor declarado a las
   transportadoras?**, y ¿Servientrega está configurada para esta cuenta?
2. Inter Rapidísimo: si `to_f debe ser mayor que o igual a 25` es la
   verificación de origen pendiente, que lo digan y de paso arreglen el
   mensaje; si no lo es, qué atributo es. Se adjunta la plantilla
   `535bd77b-fce2-46f0-9354-56b9c42fba5f` en `pending_to_send`.
3. Créditos de prueba para el sandbox. **Se intentó la vía de autoservicio y
   falló del lado de ellos** (ver abajo): la recarga por Mercado Pago en modo
   prueba muere en `payment_creation_failed`. Se pide con la evidencia de esa
   operación.

DHL y la firma del webhook **no se preguntan**.

#### Créditos del sandbox: hay una vía sin pasar por soporte

Verificado el 14 de septiembre de 2026 en el propio panel
(`sb-pro.skydropx.com`, con el selector de país en **Colombia**; en México el
saldo aparece como `$0.00 MXN` y las opciones de pago son las mexicanas):

- Por API no existe ninguna ruta que abone saldo: en finanzas solo hay
  `GET /finance/credits` y `GET /finance/extra-charges`. El único movimiento de
  la cuenta es el depósito de 1.000 con origen `sandbox_registration`.
- El centro de ayuda describe la recarga por PSE, Mercado Pago o tarjeta con
  comisión y factura, y no menciona créditos de prueba.
- **Pero el botón "Agregar créditos" del sandbox funciona contra el ambiente de
  pruebas de Mercado Pago.** Los tres métodos —PSE, Mercado Pago y Tarjetas—
  cargan el SDK de Mercado Pago con la llave pública
  `TEST-8c2eb7a4-98f5-42e7-b6e2-23506cc95a84`, y las llaves `TEST-` de Mercado
  Pago son, por definición, las de su sandbox: no mueven dinero real. La página
  además declara `window.env = "sandbox"`. El depósito mínimo es de 10 COP, la
  comisión que muestra es 1,1 % (PSE y Mercado Pago) o 2,55 % (tarjeta) más
  IVA, y pide datos de facturación después de tres cargas o al superar
  104.748 COP.

La vía, entonces, es: panel sandbox en Colombia → "Agregar créditos" →
**Tarjetas** → cantidad (con 100.000 alcanza para varias guías) → pagar con una
**tarjeta de prueba de Mercado Pago Colombia**, que están publicadas en su
documentación (`mercadopago.com.co/developers/es/docs/your-integrations/test/cards`):
Mastercard `5254 1336 7440 3564`, CVV `123`, vencimiento `11/30`, y el nombre
del titular **`APRO`**, que es el que fuerza la aprobación. Con `OTHE` se
simula un rechazo. Después se comprueba con `GET /api/v1/finance/credits`.

**Se hizo, y no funcionó.** El 14 de septiembre de 2026 a las 11:01 (hora del
checkout, UTC−4) se intentó una recarga de 100.000 COP por "Tarjetas" con la
Mastercard de prueba y titular `APRO`. Mercado Pago abrió su Checkout Pro de
sandbox (`sandbox.mercadopago.com.co`, `liveMode: false`, la misma llave
`TEST-8c2eb7a4…`) y terminó en la pantalla "No pudimos procesar tu pago" con
`payment_status: failed` y **`payment_status_detail: payment_creation_failed`**:
el pago no fue rechazado por la tarjeta, **no llegó a crearse**. Datos de la
operación: `preference_id 1143081151-e255d976-fff1-48b7-a4a7-718196b243f9`,
`external_reference b38267b1-3183-4c00-9531-7d4bddf14797`, `collector_id
1143081151`, `processing_mode aggregator`, `payment_method_id master`.

El porqué está en la documentación de Mercado Pago, no en la nuestra: la
compra de prueba con Checkout Pro se hace **iniciando sesión con un usuario de
prueba comprador**, y las cuentas de prueba —vendedor y comprador— las crea el
dueño de la aplicación, que es Skydropx. La integración de Skydropx manda al
comprador como invitado con la llave `TEST-` de su cuenta real, que es justo la
combinación que la comunidad de Mercado Pago reporta como "falla al crear el
pago" con este mismo mensaje. No hay nada que se pueda cambiar desde nuestro
lado: ni la llave, ni la preferencia, ni el usuario de prueba son nuestros.

**Segundo intento, con la Visa de prueba `4013 5406 8274 6260`: Mercado Pago
lo dio por acreditado y Skydropx no lo registró.** Revisado a las 10:13 de
Colombia, minutos después del pago: `GET /api/v1/finance/credits` sigue en 1.000, `transaction_stats`
sigue con un único movimiento (el depósito inicial) y la pantalla
Administración > Finanzas > Movimientos del panel muestra lo mismo: ni
depósito acreditado, ni pendiente, ni rechazado. El pago existe en el sandbox
de Mercado Pago y no existe en Skydropx.

Eso ubica el fallo en el tramo que va de Mercado Pago a Skydropx —el aviso de
pago o la verificación del pago en su callback— y ese tramo es enteramente de
ellos. La primera vía (Mastercard, `APRO`) falló al crear el pago; la segunda
creó y aprobó el pago y no se acreditó. Las dos van en el mensaje a Skydropx,
con el número de operación de Mercado Pago del segundo intento.

De paso, el panel muestra en Facturación "Hay un error con tus datos de
facturación" con botones Editar y Reintentar: los datos que se intentaron
registrar no pasaron. No es prerrequisito de la recarga —la piden después de
tres cargas o al superar 104.748 COP—, pero conviene decirlo en el mismo
mensaje por si en su lado sí bloquea el abono.

Dos datos más que el panel dejó a la vista, y que la API no decía:

- El formulario de cotización acota el **valor declarado entre 10.000 y
  5.000.000 COP**. Es el rango que hay que respetar en `declared_amount`, y el
  tope es el primero con fuente para el máximo de `ADR-0023`.
- La contraentrega tiene dos interruptores: "Contra entrega" (la transportadora
  recauda el valor declarado) e **"Incluir costo del envío"** (el destinatario
  paga el flete al recibir). Es la elección "solo producto" o "producto más
  flete" de la sección 3, y confirma que **lo que se recauda es el valor
  declarado**, así que el valor declarado del pedido contraentrega tiene que
  ser el total a cobrar, no el mínimo asegurable.

### 6.2 Sesión del 15 de septiembre de 2026: llegó el saldo, y la entrega en oficina tiene forma

Dos cosas pasaron el mismo día y no son la misma cosa: **Skydropx acreditó los
créditos** que se le pidieron el 14, y el negocio recibió de ellos una
**infografía** con el procedimiento para crear un envío a oficina de la
transportadora. Lo primero desbloquea el paso 7; lo segundo es documentación de
panel, no de API, y hay que leerla como tal.

#### El saldo, y lo que no llegó con él

`GET /api/v1/finance/credits` responde `{"balance": 50000.0, "currency": "COP"}`.
En `transaction_stats` el movimiento es un depósito de **49.000** del 15 de
septiembre a las 10:35, con `transaction_source: Skydropx`, etiqueta
`USO_INTERNO` y el comentario "para realizar peruebas". O sea: **lo depositó el
soporte a mano**, no la recarga por Mercado Pago, que nunca funcionó.

**Lo que la misma solicitud pedía y no se resolvió**: las transportadoras. Se
volvió a cotizar Medellín → Bogotá y Medellín → Medellín, y los cuatro errores
del día 14 vuelven idénticos —Inter Rapidísimo con `to_f debe ser mayor que o
igual a 25`, Coordinadora con `La valoración de la guía es menor a la valoración
mínima`, Envía Mercancía con sus restricciones de bulto grande, y Servientrega y
Envía Paquete sin mensaje—. **La única tarifa viva sigue siendo 99 minutes**,
10.540 dentro de Medellín, sin cobertura a Bogotá.

Lo único que sí se movió: la plantilla de origen pasó de `pending_to_send` a
**`process`** para `interrapidisimo`. La verificación avanza; el `to_f` no.

**Consecuencia de presupuesto, y conviene decirla en números:** 50.000 dividido
entre 10.540 son **cuatro guías**, todas de 99 minutes y todas dentro de
Medellín. No hay margen para gastarlas en experimentos.

#### La entrega en oficina, o "envío Ocurre", en la API

✅ **Confirmado en el OpenAPI oficial** (`sb-pro.skydropx.com/es-CO/api-docs.json`,
descargado ese día). Es un mecanismo **uniforme**, igual para las seis
transportadoras, en tres piezas:

| Pieza | Dónde | Qué dice |
|---|---|---|
| `office_delivery` y `office_pickup` | **Cada tarifa** de la cotización (v1 y v2) | Booleanos. Si la tarifa admite que el destinatario recoja en sucursal, o que el remitente la entregue en sucursal |
| `GET /api/v1/office_points` | `rate_id` obligatorio, `direction` (`delivery` \| `pickup`, por omisión `delivery`) y `limit` (15) | Las sucursales aplicables a **esa** tarifa, filtradas por cobertura y **ordenadas de la más cercana al destino**. Devuelve por punto: `id`, `name`, `street1`, `street2`, `postal_code`, `area_level1`, `area_level2`, `area_level3`, `latitude`, `longitude` y `short_address` |
| `office_delivery` / `office_delivery_point_id` | Cuerpo de `POST /shipments` (v1 y v2) | El booleano en `true` **exige** el identificador del punto |

Es notablemente mejor que la contraentrega: aquí la cobertura **se declara por
tarifa** en vez de deducirse de que la tarifa sobreviva. No hay que adivinar.

**Y no se puede ejercer hoy, medido:** las seis tarifas vuelven con
`office_delivery=false` y `office_pickup=false`, y `GET /office_points` con la
tarifa de 99 minutes responde `{"data":[],"meta":{"total":0}}` en las dos
direcciones. Sin `rate_id` responde `400`. En las cinco tarifas que fallan el
`false` no prueba nada —nunca llegaron a tarifar—, pero en 99 minutes sí: es un
mensajero urbano de un día, no tiene red de sucursales.

~~**Las cuatro transportadoras que tienen oficinas son exactamente las cuatro que
no cotizan.**~~ **Corregido el 17 de septiembre en `§6.12`**: esa premisa era de
cuando cinco de seis tarifas fallaban por un defecto nuestro. Con las tarifas vivas,
las cuatro declaran `office_delivery: false` y su catálogo de puntos responde vacío.
La conclusión no cambia —una tercera forma de entrega en el checkout sería una
pantalla a la que nadie puede llegar— pero ahora se sabe por qué.

#### La infografía: es del panel, no de la API

⚠️ **Fuente: infografía de Skydropx, septiembre de 2026.** Describe el
diligenciamiento **a mano en el panel**, y por eso cada transportadora se ve
distinta aunque la API sea una sola:

| Transportadora | Lo que pide la infografía |
|---|---|
| Inter Rapidísimo | Seleccionar "ENTREGA EN OFICINA"; se despliega la lista de oficinas disponibles |
| Coordinadora | Escribir la dirección exacta de la oficina elegida en los datos de destino |
| Servientrega | Dirección de la oficina **+ "Reclamo en oficina"** |
| Envía | Dirección exacta del punto principal de la transportadora en el destino **+ "ENTREGA EN OFICINA"** |

Dos lecturas que importan para el código, el día que esto se construya:

- **Solo Inter Rapidísimo aparece con una lista de oficinas.** Para las otras
  tres la infografía dice "escriba la dirección", que es justo lo que hace quien
  **no** tiene catálogo de sucursales. Encaja con que `office_points` exija
  `rate_id`: puede que para esas tres el catálogo venga vacío incluso cuando
  coticen, y entonces "entrega en oficina" no sería una opción de la API sino
  una dirección que alguien escribe. **No está confirmado y no se puede
  confirmar hasta que coticen.**
- **La advertencia de Inter Rapidísimo es una trampa de corrección, no un
  consejo.** La infografía dice que el listado "te muestra oficinas del destino
  y también de destinos aledaños". Pintar la lista tal cual y dejar elegir
  llevaría el paquete a otra ciudad con la guía correcta. Se puede atrapar en
  código: cada punto trae `postal_code` y `area_level2`, así que la oficina
  elegida se coteja contra el DANE y la ciudad del pedido, y la que no coincida
  no se ofrece. Es del mismo género que "el servidor no confía en el cliente"
  (regla dura #7) aplicado a una lista que viene de un tercero.

#### La primera guía real, y el segundo bloqueo que los créditos destaparon

Con saldo se emitió la primera guía del proyecto. Existe, está pagada y tiene
etiqueta: envío `2d1f6540-0b02-412e-a095-6592f15b0d30`, **guía `3838859118`** de
99 minutes, `label_url` a un PDF real, `workflow_status: success`,
`payment_status: paid`. Costó 19.465 y el saldo quedó en 30.535.

Y salió por el camino equivocado, que es lo que hay que contar.

> **Corregido el mismo día, en §6.3: `POST /shipments` no está bloqueado.** Lo que
> sigue se midió contra la única tarifa que cotizaba —99 minutes— y el `422` es de
> esa transportadora, no del endpoint. Se deja escrito porque el razonamiento
> equivocado es la lección.

**`POST /shipments` parecía bloqueado.** Con `quotation_id` y `rate_id`, que es
como `ADR-0021` diseñó la fase, la respuesta era siempre la misma:

```
422 {"errors":{"declared_amount":["Valor declarado es obligatorio"]}}
```

Se probaron **quince variantes** y ninguna la mueve: el valor en el paquete y en
el envío; como `declared_value` y como `declared_amount`; como número y como
cadena; dentro y fuera del sobre `shipment`; con `package_protected` más
`protection_value`; con `insurance`; con `consignment_note`; con `quotation_id` y
sin él; por v1 y por v2. Siempre el mismo 422, palabra por palabra.

**Y el dato sí está.** El eco de `GET /quotations/{id}` devuelve
`packages: [{..., "package_protected": true, "declared_value": "250000.0",
"protection_value": 0}]`. No es que falte el valor declarado: es que el validador
del envío mira otra cosa. Es la misma familia del `Falta Valor_Declarado` que
Envía devuelve desde el 14, y apunta al mismo sitio.

**Esto corrige a §6, a medias.** Ahí quedó escrito que el `Valor declarado es
obligatorio` "tiene la forma de un error de la transportadora, así que lo más
probable es que sea ruido de una validación previa que no llega a ejecutarse sin
saldo". **La primera mitad de esa frase era la correcta**: sí tiene la forma de un
error de la transportadora, porque lo es (§6.3). Lo que estaba mal era descartarlo
como ruido.

**El camino que sí emite: `POST /api/v1/rate/shipments`**, que cotiza y crea en
una sola llamada. Cuerpo confirmado: `sync_label_creation`, `timeout`, y dentro
de `quotation` el `carrier` (`{name: "ninetynineminutes", service_name:
"nextday"}`), las dos direcciones completas, `parcels` y `declared_amount`.

**Cambiarse a él no es gratis, y el precio está medido.** Recotiza por su cuenta
y le agregó un `extra_fee` de `cash_on_delivery` de **8.925 que nadie pidió**.
Mismo día, misma transportadora, mismo servicio, mismo paquete y mismo destino:

| Camino | Flete | Recargo de recaudo | Total |
|---|---|---|---|
| `POST /quotations` | 10.540 | `cash_on_delivery: 0` | **10.540** |
| `POST /rate/shipments` | 10.540 | `cash_on_delivery: 8.925` | **19.465** |

Un **85 % más caro**, sobre un envío que no es contraentrega. Si la integración
se mudara a ese endpoint para esquivar el bloqueo, el checkout seguiría cobrando
el flete de la cotización y el negocio pagaría el otro. **No se migra.**

#### Tres cosas que solo se supieron emitiendo

- **Un `408` de Skydropx no significa que no pasó nada.** El primer intento fue
  con `sync_label_creation: true` y `timeout: 20`: respondió
  `408 {"error": "Tiempo de espera excedido"}` y **el envío se había creado
  igual** —guía `1935079383`, 19.465 descontados del saldo—. Setenta y dos
  segundos después quedó `cancelled` / `payment_status: refunded`, sin
  `error_detail`, y el saldo volvió entero. Salió gratis de milagro.
  **Reintentar a ciegas sobre un timeout emite dos guías y cobra dos veces.** Con
  `sync_label_creation: false` la llamada responde `201` con la `label_url` ya
  lista, y es la forma que hay que usar.
- **El teléfono va sin indicativo.** `+573138816711` responde
  `400 {"address_from":{"phone":["no es válido"]}}` en los dos extremos;
  `3138816711` pasa. **Toca `ORIGEN_TELEFONO`**, que hoy vale `+573138816711` en
  `application.yml`: sirve para cotizar y rompe al emitir.
- **`package_type: "4G"` y `package_content` se aceptan tal cual**, y las tildes
  viajan bien (`"Electrónica y accesorios"` vuelve intacto). El `declared_value`
  del bulto se guarda como `declared_amount` en el paquete del envío, y el
  `postal_code` vuelve normalizado a ocho dígitos (`05001000`).

#### Lo que parecía no tener forma, y sí la tenía

Aquí se escribió que `LectorEventoDeEnvio` y `ConsultorDeSeguimiento` "siguen sin
un evento real que copiar" y que "no se gastan más créditos en esto". **Las dos
cosas resultaron falsas el mismo día**: la forma del webhook está documentada (ver
más abajo) y el ciclo de seguimiento se capturó entero con una guía de Servientrega
(§6.3).

#### Lo que la documentación sí aclaró, y que el OpenAPI solo no decía

Antes de escribirle a Skydropx se releyó la página de documentación completa
—`sb-pro.skydropx.com/es-CO/api-docs`, el **HTML**, no el `api-docs.json`— y la
diferencia fue grande: la prosa de cada endpoint trae descripciones que el JSON
no tiene. **Dos de los pendientes del paso 7 dejan de estarlo.**

- ✅ **La forma del cuerpo del webhook está documentada.** La sección *Webhooks*
  trae ejemplos completos para `type: "packages"` en tres variantes —con orden,
  sin orden y en retorno— más órdenes, cotización, tarifa, cargos extra y
  recolecciones. El cuerpo es
  `data.attributes` con `status`, `tracking_number`, `tracking_url_provider`,
  `label_url`, `event_description`, `returned` y `returned_status`, y
  `data.relationships.shipment.data.id` para amarrarlo a nuestro envío.
  **Y declara que la estructura es fija**: "los campos `returned` y
  `returned_status` nunca se omiten".
  Esto **corrige** lo que el plan de arranque daba por cierto —"lo que les falta
  es la forma del cuerpo, y esa no la dice ninguna especificación; hay que ver un
  evento"—. Sí la dice. `LectorEventoDeEnvio` se puede escribir ya, con fixtures
  de la documentación, y comprobarse contra un evento real cuando lo haya.
- ⚠️ **El retorno tiene una regla propia que hay que modelar.** Cuando un envío
  va de vuelta, `status` se queda en `in_return` **todo el trayecto** y el
  rastreo real viaja en `returned_status`. Leer solo `status` deja la devolución
  como un estado congelado.
- ✅ **`unique_shipment` es la llave de idempotencia, y resuelve la trampa del
  `408`.** La documentación: "cachea la respuesta para el `rate_id` y la replica
  en reintentos con el mismo `rate_id`, evitando envíos duplicados. Devuelve
  `200 OK` con el payload original en replay, `409 Conflict` si una solicitud
  previa aún está en proceso. El cache vive 96 horas". Es exactamente el remedio
  del timeout que creó una guía y cobró: **el adaptador de emisión lo manda en
  `true` siempre**, y entonces reintentar un `408` es seguro.
- ✅ **`auto_advance` es solo de sandbox y avanza cada minuto**:
  `created → picked_up → in_transit → last_mile → delivered`. "En producción se
  ignora". Confirma los estados y la cadencia.
- ⚠️ **La plantilla de dirección no se hereda de la cotización al envío.** Dice,
  con todas las letras, que aunque la cotización se haya creado con
  `address_template_id`, **no** pasa al envío: hay que volver a mandarlo en
  `address_from`, y de la cotización solo se heredan `country_code`,
  `postal_code` y `area_level1/2/3`. Importa el día que Inter Rapidísimo exija la
  plantilla verificada: se manda en los dos sitios o no sirve.
- ⚠️ **`further_information` existe, y puede que sea el campo de las
  indicaciones.** "Información adicional para la entrega (máximo 70 caracteres).
  Se imprime en la guía cuando está habilitado para la paquetería." El 14 de
  septiembre se decidió mandar `indicaciones` en `reference`; esto abre la
  pregunta de si no va mejor aquí —o en los dos—. **No se cambia sin decidirlo**,
  y el tope de 70 caracteres es una restricción que hoy el checkout no aplica.
- ✅ **`declared_value` del bulto es del seguro y es opcional**: "Si tiene seguro
  se pone el valor a asegurar". O sea que **no** es el `declared_amount` que el
  422 exige.

#### Y lo que ninguna fuente explica

**`declared_amount` no aparece en ninguna parte del cuerpo documentado de
`POST /shipments`.** Ni en el OpenAPI ni en la prosa del HTML: los campos del
envío son `rate_id`, `unique_shipment`, `original_shipment_id`, `auto_advance`,
`printing_format`, `include_order_detail`, los cuatro de oficina, `address_from`,
`address_to` y `packages`; y los del bulto son `package_number`,
`package_protected`, `declared_value`, `consignment_note`, `package_type` y
`products`. **`declared_amount` no está en esa lista**, y "Valor declarado es
obligatorio" **tampoco está en el catálogo de errores** de la propia página, que
sí enumera los 400, 401, 403, 404, 422 y 429 posibles.

Se buscó fuera: el centro de ayuda colombiano responde `403` a una petición
automatizada, el artículo "Validaciones que debes conocer" de `help.skydropx.com`
no menciona el valor declarado, y los clientes y ejemplos de terceros que se
encontraron son de la API v2 vieja o de `orders`, no de este endpoint. **Nada lo
explica.** Va en el mensaje a Skydropx.

### 6.3 El bloqueo no era un bloqueo: era una transportadora (2026-09-15, segunda parte)

Lo que §6.2 dejó escrito —"`POST /shipments` está bloqueado por Skydropx"— **es
falso**, y conviene contar cómo se llegó a esa conclusión equivocada porque el
error se puede repetir.

**Cómo se equivocó.** Durante días la única tarifa viva del sandbox fue 99
minutes. Todas las pruebas de emisión se hicieron contra ella —quince variantes
del valor declarado, v1 y v2, con y sin `quotation_id`— y las quince dieron el
mismo `422 declared_amount: "Valor declarado es obligatorio"`. Con una sola
transportadora en la muestra, "el endpoint está roto" y "esta tarifa está rota"
son indistinguibles. **Se variaron todos los campos del cuerpo y nunca la
transportadora**, que era la variable que importaba.

**Cómo se destapó.** Leyendo el centro de ayuda colombiano
(`help.skydropx.com.co/articulos-cda/envios-con-inter-rapidisimo-via-api`) se
probó una cotización distinta y, por casualidad, esa cotización trajo viva una
tarifa de **Servientrega** —que llevaba desde el 14 sin cotizar—. El mismo cuerpo
que venía fallando respondió **`202`**.

**Cómo se confirmó, gratis.** Se repitió el experimento cambiando **solo** la
tarifa: mismo cuerpo, mismo origen y destino, mismos bultos.

| Tarifa elegida | Respuesta de `POST /api/v1/shipments` |
|---|---|
| Servientrega Standard | **`202`**, envío `eb69a24f-4faa-46b8-aa28-d56d292e4a84`, guía `873837506712` |
| 99 minutes Next day | `422 {"errors":{"declared_amount":["Valor declarado es obligatorio"]}}` |

Un `422` no cuesta saldo, así que la confirmación salió en una sola llamada.

**Lo que esto cambia:**

- ✅ **El camino de `ADR-0021` funciona.** Cotizar, elegir tarifa y crear el envío
  con `quotation_id` + `rate_id` emite una guía de verdad. No hay que rediseñar
  nada, y **no hay que migrar a `rate/shipments`** —que sigue descartado por el
  recargo de 8.925 de §6.2—.
- ⚠️ **El `422` es de la tarifa de 99 minutes**, no del endpoint. Es lo único que
  queda para preguntarle a Skydropx de este tema, y es mucho menos grave: se
  esquiva no eligiendo esa tarifa. Ojo: hoy 99 minutes es la que **más veces
  cotiza**, así que si el despacho elige siempre la más económica, se va a topar
  con ella seguido.
- ⚠️ **Las transportadoras fallan de forma intermitente, no permanente.**
  Servientrega cotizó a las 19:52 y no cotizaba a las 19:30 ni a las 20:05, con
  el mismo cuerpo. Lo que §6 llamó "responden distinto en momentos distintos"
  sigue vigente y es más importante de lo que parecía: **no se puede concluir
  "esta transportadora no sirve" de una sola cotización**.
- ❌ **Descartado: el valor declarado no es lo que tumba a las transportadoras.**
  Se cotizó el mismo envío con el bulto en 2.500, 10.000, 50.000 y 250.000, y con
  el campo ausente. **Las tarifas vivas fueron idénticas en los cinco casos.** La
  hipótesis de §6.1 —que el sandbox dejó de reenviar el valor declarado— no se
  sostiene con esta medición.

#### El ciclo de seguimiento, capturado entero

La guía se creó con `auto_advance: true` y el sandbox la movió sola, un evento
por minuto, exactamente como dice la documentación. Consultando
`GET /api/v1/shipments/tracking?tracking_number=873837506712&carrier_name=servientrega`:

```
19:53:17  picked_up   description=null  event_description=""
19:54:18  in_transit  description="Paquete en tránsito - Guadalajara"
19:55:19  last_mile   description="Paquete en ruta de entrega local - Guadalajara"
19:56:21  delivered   description=null  event_description=""
```

La forma de la respuesta, que es la que `ConsultorDeSeguimiento` tiene que leer:

```json
{"data": [{"id": "0c9a2d6d-…", "type": "shipment_event",
           "attributes": {"description": "…", "location": null,
                          "date": "2026-09-15T19:54:18-05:00",
                          "status": "in_transit",
                          "event_description": "…"}}]}
```

Cuatro cosas que solo se ven mirando eventos reales:

- **Vienen del más nuevo al más viejo.** Procesarlos en orden de llegada invierte
  la historia.
- **`description` y `event_description` llegan vacíos en `picked_up` y
  `delivered`** —`null` y `""`—. Un lector que exija texto se cae en el evento
  más importante de los cuatro.
- **`location` llegó `null` en los cuatro.** No se puede contar con él.
- **`created` no genera evento.** El paquete pasa por ese estado pero el rastreo
  empieza en `picked_up`; la lista de eventos no es la historia completa del
  paquete.
- El texto del sandbox dice "Guadalajara" para un envío entre Medellín y
  Medellín: son datos de relleno mexicanos, no un error nuestro.

#### Los doce estados, confirmados por fin

El OpenAPI declara el enum completo de `status`, y son **exactamente los doce de
`ADR-0022`, en el mismo orden**:

```
created, picked_up, in_transit, last_mile, delivery_attempt,
delivered_to_branch, delivered, exception, in_return, canceled,
destroyed, retained
```

`EstadoEnvio` los tiene los doce y coinciden uno a uno. Lo que §2.4 marcaba como
❌ ("los doce de `ADR-0022` no se pudieron re-confirmar") queda ✅.

#### Un hallazgo nuevo que toca el diseño: varios bultos son varias guías

`shipment_creation_type` viene en cada tarifa y decide qué crea `POST /shipments`.
Medido con el mismo envío variando el número de bultos:

| Bultos | `shipment_creation_type` | Total |
|---|---|---|
| 1 | `single` | 10.540 |
| 2 | **`multishipment`** | 21.080 |
| 3 | **`multishipment`** | 31.620 |

Y `GET /shipments/carrier_services` dice `multi_packages_enabled: false` en los
**siete** servicios de la cuenta. O sea: en Colombia **ninguna transportadora
admite multipaquete**, y la decisión del 11 de septiembre —"un `parcel` por
variante"— significa que **un pedido de dos variantes genera dos guías, cada una
con su número y su cobro**.

Eso choca con el modelo: `Envio` guarda un `tracking_number` por pedido. **No se
arregla aquí**; queda anotado como la decisión que hay que tomar antes de escribir
el despacho, y las salidas visibles son tres —un `Envio` por bulto, un `Envio` con
varias guías, o consolidar en un solo bulto y perder las medidas reales—.

#### Dos datos sueltos que la documentación cerró

- ✅ **El host de producción es `api-pro.skydropx.com`**, dicho por el bloque de
  credenciales de la propia documentación. Era un ❌ de §6 desde el 11 de
  septiembre. Sigue sin comprobarse con credenciales de producción, pero ya no es
  una suposición.
- ✅ **El correo de integración es `api@skydropx.com`**, no el `hola@skydropx.com`
  de la sección de webhooks. Lo dice el bloque de credenciales: "¿Necesitas ayuda
  con tu integración? Escríbenos a api@skydropx.com".

#### Lo que sigue sin respuesta

- **Por qué la tarifa de 99 minutes exige `declared_amount`** y en qué campo lo
  querría. Es lo único que queda de este tema.
- **Por qué `rate/shipments` cobra un recargo de recaudo no pedido.**
- **`label_url` nunca apareció** en la guía de Servientrega, ni con el envío en
  `delivered`. Sí apareció en la de 99 minutes emitida por `rate/shipments`. No se
  sabe si es del simulador de `auto_advance` o de la transportadora.
  **Desmentido el 16 de septiembre (§6.7)**: la guía de Servientrega de ese día sí
  lo trajo. Sigue sin saberse qué lo decide.
- **Inter Rapidísimo y su `to_f >= 25`**, con la plantilla de origen en `process`.

### 6.4 La documentación leída entera, y el campo que faltaba (2026-09-15, tercera parte)

Se recorrió con el navegador toda la documentación —las cuatro guías rápidas y los
cuarenta y cinco endpoints de `sb-pro.skydropx.com/es-CO/api-docs`, que es la página
que arma JavaScript y no llega en una descarga— y los artículos del centro de ayuda
colombiano, `help.skydropx.com.co/subcategorias-cda/api`. El propósito era juntar lo
que había que preguntarle a Skydropx. **Casi nada había que preguntarlo, y una de las
preguntas era un error nuestro.**

#### El valor declarado va dentro del bulto, y se llama `declared_amount`

Lo dicen dos fuentes independientes:

- El OpenAPI lo lista bajo `parcels[]`, junto a `length/width/height/weight`, marcado
  `Required`. Se comprobó contra el DOM y no por la sangría del texto: `declared_amount`
  cuelga al mismo nivel que `weight`, y `cash_on_delivery` y `requested_carriers` un
  nivel más arriba.
- El artículo *Cómo crear envíos con Inter Rapidísimo vía API*, con todas las letras:
  "Recuerda incluir el campo `declared_amount` (valor declarado) en pesos colombianos
  **dentro de cada paquete**".

El mapeador mandaba `declared_value` en el bulto y `declared_amount` al nivel de la
cotización. **Los dos se ignoran**, así que a la transportadora le llegaba cero.

**Medido el 15 de septiembre con cinco cuerpos idénticos salvo por ese campo**
(`tools/sonda-valor-declarado.mjs`; cotizar no consume saldo). Valor declarado 250.000,
un bulto de 30×25×10 y 1 kg:

| Transportadora | Como se mandaba (`declared_value` en el bulto) | Con `declared_amount` en el bulto |
|---|---|---|
| Servientrega Standard | `tariff_price_not_found` | **27.350** a Bogotá · **12.050** en Medellín |
| Envía Paquete Terrestre | `tariff_price_not_found` | **16.050** a Bogotá · **8.950** en Medellín |
| Coordinadora Standard | `not_applicable`: "La valoración de la guía es menor a la valoración mínima" | **20.456** a Bogotá · **11.384** en Medellín |
| Inter Rapidísimo | `not_applicable`: `to_f debe ser mayor que o igual a 25` | `no_coverage` limpio |
| 99 minutes Next day | 10.540 en Medellín, sin cobertura a Bogotá | 19.465 en Medellín, sin cobertura a Bogotá |
| Envía Mercancía Terrestre | restricciones de bulto grande | iguales (`longer_side > 45`, `max_weight > 9`) |

**La deduplicación por contenido, que era una trampa, sirvió de instrumento.** Tres
cuerpos que solo diferían en esos campos —con `declared_amount` en el bulto, con los dos
campos, y sin el `declared_amount` de cotización— devolvieron **el mismo `id` de
cotización**. Si para Skydropx son el mismo cuerpo, los campos que los distinguen no los
lee nadie. De ahí sale, sin ambigüedad, que `declared_value` y el `declared_amount` de la
cotización sobran los dos.

**Qué corrige esto, sección por sección:**

- **§6, "Sigue sin confirmarse"**: donde dice que `declared_value` va en cada `parcel` y
  que `declared_amount` es obligatorio a nivel de cotización, es al revés.
- **§6.1**: la hipótesis era "entre el 11 y el 14 de septiembre el sandbox dejó de
  reenviar el valor declarado". Iba bien encaminada —el valor no llegaba— pero el
  diagnóstico apuntó al lado equivocado. **Y conviene decir que no fue un error de
  siempre**: el 11 de septiembre Coordinadora cotizó 19.616 con `declared_value` en 2.500,
  y la validación de `declared_amount ≥ 10000` **aparece el 14**. Lo que pasó, con toda
  probabilidad, es que Skydropx movió el campo y nosotros no lo seguimos. Es el género de
  cambio que hay que vigilar en un proveedor, no un descuido del primer día.
- **§6.2 y §6.3**: el `to_f debe ser mayor que o igual a 25` de Inter Rapidísimo **no era
  la verificación de origen pendiente**. Era el valor declarado ausente. Con el campo en
  su sitio, Inter Rapidísimo contesta `no_coverage`, que es una respuesta honesta.
- **§6.3**, "Descartado: el valor declarado no es lo que tumba a las transportadoras":
  esa medición varió `declared_value`, un campo muerto. No descartó nada.

**Consecuencias, y no son cosméticas:**

1. **El mínimo de 10.000 se valida por bulto**, no por pedido: un bulto declarado en
   8.000 devuelve `422 "El valor declarado debe ser mayor o igual a 10000"` y tumba la
   cotización **entera**. Con un `parcel` por variante, un artículo barato dentro de un
   pedido caro deja al pedido sin envío a domicilio. Es el dato de negocio que §6.1 dejó
   abierto, ahora con su forma exacta.
2. **La tarifa más económica cambia de dueño.** Dentro de Medellín, Envía a 8.950
   desplaza a 99 minutes, que con el valor declarado real sube a 19.465. Y 99 minutes es
   una de las dos transportadoras **sin recolección por API** (ver abajo): el criterio
   "la más barata" ahora elige otra cosa, y elige mejor.
3. **La solicitud que se le envió a Skydropx el 14 de septiembre por estas tres
   transportadoras hay que retirarla.** La respuesta era nuestra.

**Arreglado el 15 de septiembre** en `MapeadorCotizacionSkydropxV1`: el campo se mueve al
bulto y se eliminan los dos muertos. Se fue con ellos
`CotizacionEnvio.valorDeclaradoTotal()`, que existía solo para alimentar el campo de
cotización. La prueba que importa es `noSeMandanLosCamposQueElProveedorIgnora`: el fallo
original **no producía ningún error** —la cotización respondía `201` y las tarifas
simplemente no venían—, así que sin un guardián explícito la regresión volvería a ser
invisible.

#### Recolección: el tramo que §2.2 dejó con dos cuerpos incompatibles

✅ **Gana la forma "por envío"**, y la otra no existe:

```
POST /api/v1/pickups
{ "pickup": { "reference_shipment_id", "packages", "total_weight",
              "scheduled_from", "scheduled_to" } }
```

La documentación añade: "evita cancelaciones por duplicidad agrupando los paquetes con el
mismo origen y fecha en una sola recolección". Y `GET /api/v1/pickups/coverage` exige
`shipment_id`, así que **la recolección va después de emitir la guía**, no antes: eso fija
el orden del despacho. Reprogramar tiene tope de **14 días**
(`reschedule_remaining_days` en la respuesta).

⚠️ **Y hay una regla operativa que ninguna API declara**, del artículo *Consideraciones
para recolecciones vía API*:

| Transportadora | Recolección |
|---|---|
| Coordinadora, Inter Rapidísimo, Servientrega | **Por API**: hay que llamar a `/pickups` desde la integración |
| **99 minutos y Envía** | **Solo por soporte**, escribiéndole a Skydropx |

Antes de las 12:00 se agenda el mismo día; después, el siguiente hábil. No hay
recolección sábados, domingos ni festivos.

✅ **Cada tarifa lo dice por su cuenta**, y eso sí se puede programar: `pickup`,
`pickup_automatic`, `pickup_package_min`, `pickup_ocurre` (en origen o en sucursal) y
`pickup_via_support`. La decisión abierta #1 ya no depende de preguntarle a nadie.

#### Contraentrega: el campo que no existía porque no hacía falta

❌ → ✅ **El nombre del campo del monto a recaudar.** No hay ninguno. En la petición de
cotización solo existen dos booleanos —`cash_on_delivery` y `recipient_pays_shipping`—, y
el monto sale calculado en la respuesta:

- `on_delivery_amount`: "Monto total a cobrar al destinatario (**valor declarado** + costo
  de envío si aplica)".
- `on_delivery_status`: `pending` / `collected` / `failed` / `cancelled`, y el centro de
  ayuda publica qué significan: cobrado, en proceso con la transportadora, no se pudo
  entregar, guía anulada.

Las diez grafías que se probaron el 11 de septiembre no fallaron por estar mal escritas:
**ese dato no se declara**. Y hay una segunda lectura que explica el `null`: la
documentación dice que `cash_on_delivery` "solo está disponible cuando la feature está
habilitada para la cuenta", así que `on_delivery_amount: null` con recaudo pedido
significa **servicio no activado** —el trámite de §3—, no campo inexistente.

**`on_delivery_status` le cambia la vida a `RECAUDO_CONCILIADO`**: el estado del cobro se
consulta por API, guía por guía, y no hay que deducirlo del extracto del jueves.

Dos reglas de negocio del artículo *Cómo crear un envío contra entrega*: el recaudo es
**solo en efectivo** y **el paquete se entrega sellado**, sin abrir antes de pagar
(excepción: Inter Rapidísimo, con su política "Pago en Casa"). Las dos hay que decirlas en
el checkout. Y confirma lo que ya suponía §6.1: **lo que la transportadora recauda es el
valor declarado**, que es además la base del seguro obligatorio.

#### Emisión, multienvío y v2

✅ **Decisión #8 resuelta: v2.** La documentación de `POST /api/v2/shipments` lo dice
directo: "a diferencia de V1, este endpoint **siempre retorna un arreglo de envíos**. Para
envíos únicos o multipaquete, el arreglo contiene un elemento. Para tarifas multienvío,
contiene un envío por paquete". Con `multi_packages_enabled: false` en los siete servicios
de la cuenta, en Colombia todo pedido de dos variantes es multienvío: v1 no tiene forma de
devolver eso.

✅ **`shipment_creation_type` tiene tres valores, no dos**: `single` (un bulto),
`multipackage` (un envío con varios paquetes) y `multishipment` (**un envío por paquete**).
Y el artículo *Qué es Multienvíos* remata lo que eso significa: cada paquete es un envío
independiente, **se cancela por separado y se rastrea por separado**.

✅ **Dónde vive cada número**, que explica el `label_url` fantasma de §6.3: el envío trae
`master_tracking_number`, y **cada paquete trae el suyo** —`tracking_number`,
`tracking_url_provider`, `tracking_status`, `label_url` y `declared_amount`— en el
`included` de la respuesta. La guía de Servientrega no venía sin etiqueta: se estaba
mirando el nivel equivocado.

✅ **`declared_amount` no es campo del cuerpo de `POST /shipments`**, ni en v1 ni en v2 —lo
que §6.3 ya sospechaba—, y **`quotation_id` tampoco**: el único identificador documentado
es `rate_id`. Mandarlo no estorba, pero no es lo que ata el envío a la cotización.

⚠️ **`company` es obligatorio en las dos direcciones** al crear el envío, y no estaba en la
lista de §6. `reference` en cambio solo es obligatorio en el origen.

✅ **Cancelar** (decisión #7): `POST /shipments/{id}/cancellations` con
`{reason, shipment_id}`; responde `status` y `success`, y `422 "El envío no se puede
cancelar"` cuando ya no hay nada que hacer.

✅ **Asegurar** (decisión #3): `protect` cobra **un fijo más un porcentaje** del valor
declarado (`fixed_cost`, `percentage`, `total` en la respuesta) y devuelve `422` si el
valor declarado se sale del rango. Ya se puede poner precio a la decisión.

#### Webhooks: dos precisiones sobre lo de §6.1

- ⚠️ **`data.id` es el identificador del *paquete*, no del envío**; el envío va en
  `data.relationships.shipment.data.id`. Con multienvío eso significa **un evento por
  guía**, y el lector tiene que amarrar por la relación, no por `data.id`.
- ✅ **Existe un webhook de `quotation` con `status: completed`** que trae los `rates` en
  `relationships`. Es una alternativa al sondeo de `is_completed` para los usos que no sean
  el checkout —el checkout es síncrono y el sondeo se queda—, y conviene saber que está.
- ✅ **`extra_charges` trae `real_weight`, `original_weight` y `discrepancy_weight`**: es la
  reliquidación por peso mal declarado de §2.5, medible y con número.

#### Defectos de la documentación de Skydropx, para no perder el tiempo

- El artículo *Códigos de tipos de empaques (`package_type`)* apunta a un PDF de Google
  Drive que **está roto** ("el archivo que has solicitado no existe"). La lista real se
  pide por API: `GET /api/v1/shipments/packagings`, paginado, con `code` y `name` (el
  ejemplo es `4G` = Box, `total_count: 5`). **`package_type` sigue sin decidirse**, pero ya
  se sabe que la vía es esa y no buscar más documentación.
- El artículo *Códigos de paqueterías (`requested_carriers`)* del centro de ayuda
  **colombiano** publica la lista de **México** —Estafeta, Paquetexpress, verificación con
  INE—. No sirve aquí: los códigos buenos salen de `GET /shipments/carrier_services`.
- Los ejemplos del artículo de Inter Rapidísimo contradicen lo medido: escriben el teléfono
  con indicativo (`573109876543`) y el campo de plantilla como `template_id` cuando el
  OpenAPI dice `address_template_id`. **Gana lo medido.**
- La entrega en oficina, en Colombia, **no se hace con `office_delivery`**: el artículo
  *Cómo hacer el envío a una oficina* dice que se escribe "Reclame en oficina" en la
  referencia y la dirección de la oficina en el destino, con listados por transportadora en
  hojas de cálculo. Encaja con que `office_points` devuelva vacío (§6.2): el mecanismo de la
  API existe y aquí nadie lo usa.

#### Lo que sigue abierto después de todo esto

- ~~**Qué se hace con el bulto que declara menos de 10.000.**~~ **Cerrado el 17 de septiembre
  de 2026 con `ADR-0035`: se eleva al mínimo.** Agrupar obligaba a inventar las dimensiones de
  una caja combinada; ofrecer solo recogida castigaba un pedido de 400.000 por un cable de 8.000.
  El `TODO` del mapeador murió con la decisión. ~~**Lo que abrió**: el rango tiene otro extremo
  —el panel lo acota entre 10.000 y 5.000.000 (§6.5)— y arriba nadie ha medido si la API lo
  valida.~~ **Medido el mismo día (§6.13): la API valida un techo de 5.000.000 exactos, con un
  `422` simétrico al del mínimo y también por bulto.** Recortar no es simétrico a elevar
  —declarar un celular de ocho millones en cinco deja el resto sin asegurar y esa diferencia la
  pone el negocio—, así que se decidió lo contrario: ese artículo **no va a domicilio y el
  checkout lo dice nombrándolo** (`ADR-0036`).
- ~~**`package_type`**: pedir el catálogo a `GET /shipments/packagings` y elegir.~~
  Catálogo leído (§6.5): 59 códigos de embalaje de la ONU, y el que aplica es `4G`,
  caja de cartón. Queda como elección de operación, no como incógnita.
- ~~**Por qué la tarifa de 99 minutes exige `declared_amount` al emitir** (§6.3).~~
  Resuelto el mismo día (§6.5): era el mismo valor declarado mal puesto. Con la
  cotización corregida, esa tarifa emite `202`.
- **Cuánto esperar a una tarifa en `pending`** cuando la cotización ya volvió
  `is_completed` (§6.5). Decisión de ADR con un tope en segundos.
- ~~**Cerrar la recolección** (§6.6): qué campo es "address2" y programar una de verdad.
  Cuesta una emisión, y hay que hacerla **en horario hábil**.~~ **Las dos mitades se
  resolvieron y ninguna era de la hora**: "address2" es el **barrio**, y viaja por la
  cotización, no por el envío (§6.7, §6.10); programar una de verdad **no depende de
  nosotros**, porque el conector responde `ECONNREFUSED` también en horario hábil (§6.11).
- **Si el valor declarado del pedido contraentrega incluye el flete** (§6.5).
  `recipient_pays_shipping` no lo suma; declararlo sube también el seguro.
- **El recargo de recaudo no pedido de `rate/shipments`** (§6.2). Sigue descartado el
  endpoint, así que es curiosidad, no bloqueo.
- ~~**El modelo de `Envio` frente al multienvío**: un `Envio` por bulto, uno con varias guías,
  o consolidar. Sin decidir~~ **Decidido en `ADR-0031`: un `Envio` con varias `GuiaEnvio`**,
  cada una con su transportadora, su costo y su propio rastro de eventos. Lo que lo decidió es
  que cada guía se recoge, se entrega y se cobra sola — el costo vive en la guía y no en el
  envío, porque repartirlo a mano sería inventarlo.

### 6.5 Los dos pendientes que dejó §6.4, medidos (2026-09-15, cuarta parte)

§6.4 cerró el valor declarado y dejó dos cosas dichas a medias: si la contraentrega
está activa y dónde cubre, y si el `422` de 99 minutes al emitir era el mismo error.
Las dos se midieron. **Las dos eran el mismo error.**

#### La contraentrega está activa, y ya no es solo Medellín

Sondas gratis (`tools/sonda-recaudo.mjs`), con el valor declarado ya en su sitio y
250.000 de mercancía:

- ✅ **El servicio está habilitado en la cuenta.** Pedida con `cash_on_delivery: true`,
  la cotización responde `cash_on_delivery: true` y **`on_delivery_amount: "250000.0"`**.
  Eso corrige lo que §3 daba por hecho —"hay que solicitar el servicio; no viene
  activo"— al menos en este sandbox, y da la señal para distinguirlo: con el servicio
  apagado, `on_delivery_amount` volvería `null`.
- ✅ **El monto recaudado es el valor declarado**, exactamente. Confirma por medición lo
  que decía el centro de ayuda.
- ⚠️ **`recipient_pays_shipping: true` no cambia el monto**: `on_delivery_amount` sigue
  siendo 250.000, no 250.000 más el flete. O el cálculo se hace al emitir, o el campo no
  hace nada en la cotización. **Consecuencia para `ADR-0023`**, que decidió recaudar
  `Pedido.total()` con flete incluido: eso no se consigue con ese booleano, se consigue
  **declarando el total como valor declarado**. Que es coherente con todo lo demás —el
  valor declarado es lo que se recauda y lo que se asegura— pero hay que decirlo y
  decidirlo, porque declarar el flete como mercancía también sube el seguro.
- ✅ **El recaudo no cobra recargo en la cotización**: la tarifa de 99 minutes vale 19.465
  con recaudo y sin él. El sobrecosto de 8.925 de §6.2 es cosa de `rate/shipments`, no del
  recaudo.

**Cobertura medida**, que es lo que le faltaba al paso 6 del plan de arranque:

| Destino | Sobreviven con recaudo | Se caen |
|---|---|---|
| Medellín | **Envía Paquete 8.950 · Coordinadora 11.384 · 99 minutes 19.465** | Servientrega (`tariff_price_not_found`), Inter Rapidísimo (`no_coverage`) |
| Bogotá | **Envía Paquete 16.050 · Coordinadora 20.456** | Servientrega, 99 minutes (`no_coverage`) |

O sea: **la contraentrega ya se puede ofrecer fuera de Medellín**, con dos
transportadoras a Bogotá. Y Servientrega, que sí cotiza sin recaudo (12.050 y 27.350), se
cae en cuanto se pide con recaudo: es la señal de cobertura de §6 funcionando como se
esperaba, ahora sobre una muestra que no es de una sola transportadora.

#### Una trampa nueva: `is_completed: true` no significa que todas contestaron

Medido de casualidad y confirmado a propósito. Una cotización volvió `is_completed: true`
con la tarifa de 99 minutes en **`pending`**; al releer esa misma cotización un minuto
después, la tarifa estaba en `price_found_external` con precio.

**`ADR-0021` sondea hasta `is_completed` y ahí se planta**, y `MapeadorCotizacionSkydropxV1`
descarta toda tarifa sin `success: true`. Juntando las dos cosas, **una transportadora
lenta se pierde en silencio**: esta vez la que faltaba era la más cara y no cambiaba nada,
pero nada garantiza que la próxima no sea la más barata. La cotización no miente —la
tarifa aparece después— y el checkout no la ve.

No se arregla aquí. Es una decisión con dos filos: esperar a que no quede ninguna
`pending` alarga el checkout contra un proveedor que ya es lento, y no esperar cobra de
más. **Va al ADR**, con el tope de segundos que el checkout tolere.

#### El `422` de 99 minutes era el mismo bug

Probado con permiso, porque emitir cuesta: se cotizó con el valor declarado en el mínimo
(10.000) y un sobre de 20×15×2 con 100 gramos, para que la tarifa fuera la más barata
posible, y se emitió con la tarifa de 99 minutes —la misma que quince veces respondió
`422 declared_amount: "Valor declarado es obligatorio"`—.

**`202`.** Envío `d9911391-d2d3-4998-a64c-b7229b428e17`, guía **`1543555745`**, 9.897. El
saldo pasó de 18.485 a **8.588**.

Queda entonces que el envío hereda el valor declarado del bulto de la cotización, y que
con la cotización mal armada el paquete salía sin él: 99 minutes lo exigía y Servientrega
lo toleraba. **No hay nada que preguntarle a Skydropx sobre esto**, y el `TODO` de §6.3
—"por qué la tarifa de 99 minutes exige `declared_amount`"— se cierra.

Dos cosas más que solo se ven emitiendo, y que corrigen a §6.3:

- ✅ **`label_url` sí aparece.** En la respuesta `202` vienen `master_tracking_number` y
  `label_url` en `null`, con `workflow_status: in_progress`. Al releer el envío,
  `workflow_status: success` y ya están los dos: guía `1543555745` y una URL de etiqueta
  real. Lo de §6.3 —"`label_url` nunca apareció"— era leer demasiado pronto, no un fallo
  de la transportadora. **El despacho no puede dar por buena la respuesta de creación: hay
  que releer el envío o esperar el webhook.**
- ✅ **`package_type: "4G"` se acepta y vuelve en el paquete**, junto a
  `declared_amount: 10000.0`.

#### `package_type`: el catálogo, por fin

`GET /api/v1/shipments/packagings` responde **59 tipos en tres páginas**, y son los códigos
de embalaje de la ONU, no una lista de Skydropx. Los que le sirven al negocio son dos:

| Código | Nombre |
|---|---|
| **`4G`** | Caja de cartón |
| `5H4` | Saco (bolsa) de película de plástico |

El resto son bidones de acero, jaulas, cajas de madera contrachapada y envases compuestos.
**Deja de ser una incógnita de la API y pasa a ser una elección de operación**: lo que
TecnoSport despacha va en caja de cartón, salvo que alguien decida mandar algo en bolsa.

### 6.6 La recolección, ejercida a medias (2026-09-15, quinta parte)

`§2.2` la llamó "el tramo que ningún ADR contempla" y `§6.4` encontró su cuerpo en la
documentación. Faltaba ejercerlo, y se intentó de punta a punta con
`tools/sonda-recoleccion.mjs`: cotizar, emitir con una transportadora que recoja por
API, pedir cobertura, programar y consultar.

**No se cerró**, y el motivo no está en nuestro lado. Pero el endpoint contestó lo
suficiente como para que quede poco por adivinar.

#### Lo que `POST /pickups` validó de nuestro cuerpo

Todo esto salió gratis: un `422` no cuesta saldo.

| Respuesta | Qué enseña |
|---|---|
| `422 total_weight: ["debe ser un entero"]` | El peso total va en **kilos enteros**, aunque el OpenAPI lo declare `["number", "string"]`. Mandar `0.1` no pasa. Un sobre de 100 gramos se programa como 1 |
| `422 base: ["Some carriers are missing credentials..."]` | Con la guía de 99 minutes. **Es la versión en API del "solo por soporte"** de `§6.4`: la cuenta no tiene credenciales de recolección de esa transportadora. El código puede distinguirlo sin codificar nombres |
| `422 base: ["Shipper address2 not valid: null"]` | Con una guía de Servientrega cuya dirección de origen iba incompleta. **Falta un campo de la dirección del envío**, y los únicos que el envío guarda en `null` son `apartment_number` y `area_level3` |
| `422 reference_shipment: ["El estado del envío no es exitoso"]` | **La recolección exige el envío en `success`.** El `202` de creación no basta |

✅ Y por tarifa, la cotización ya dice quién recoge por API: `pickup` viene **`true` en
Coordinadora, Servientrega e Inter Rapidísimo** y **`false` en 99 minutes y Envía**,
que son exactamente las dos del "por soporte". Coincide con el centro de ayuda sin
tener que leerlo. Ojo: `pickup_via_support` vuelve `false` en las seis, así que **el
campo que discrimina es `pickup`**, no ese.

⛔ **`GET /pickups/coverage` no sirvió ni una vez.** Cuatro guías distintas —una en
error, dos en `success`, de tres transportadoras— y siempre
`422 {"success": false, "message": null}`, con el mensaje vacío que su propia
especificación promete llenar. No es obligatoria para programar, pero hoy no se puede
usar para ofrecer fechas.

> **Corregido el 17 de septiembre (§6.10, §6.11): el `422` era nuestro.** Las cuatro
> guías se habían emitido **sin barrio**, y el barrio viaja por la cotización. Con
> `area_level3` puesto, `GET /pickups/coverage` responde `200` con fechas reales. La
> cobertura sí se puede ofrecer; lo que no responde es `POST /pickups`.

#### ~~Por qué no se cerró: las transportadoras, de noche, no emiten~~ Por qué no se cerró, y la causa que se escribió aquí era falsa

Tres emisiones, tres muertes **minutos después del `202`**, y las tres con el saldo
devuelto entero:

| Transportadora | `error_detail` |
|---|---|
| Coordinadora (2 intentos) | `500 ... llave duplicada viola restricción de unicidad «agw_remisiones_idx_codigo_remision» ... (codigo_remision)=(93202421647)` — **el mismo código de remisión para dos envíos distintos**: su contador está atascado |
| Servientrega | `500 {"error":""} at LABEL_NUMBER` — un quinientos vacío |

Servientrega había emitido bien esa misma mañana y 99 minutes una hora antes. Eran las
22:30. ~~**La intermitencia que `§6.3` anotó y subestimó tiene, con toda probabilidad,
horario**: de noche los sistemas de las transportadoras no responden. Conviene programar
las pruebas de emisión en horario hábil.~~

> **Falso, y costó dos sesiones creerlo. Desmentido el 16 y el 17 de septiembre
> (§6.10, §6.11).** Las dos muertes de esta tabla tienen cada una su causa, y ninguna es
> la hora:
>
> - **Coordinadora**: su contador de remisiones está atascado y devuelve el mismo
>   `codigo_remision` a las 22:30 y a las 18:35. Es **determinista** — en este sandbox no
>   puede emitir nunca, y es además la tarifa más barata de la cuenta, o sea la que un
>   selector por precio elige sola (§6.10).
> - **Servientrega**: emitió bien en horario hábil. Lo que no responde es su conector de
>   **recolección**, `ECONNREFUSED at PICKUP`, también a las 10:20 de un jueves (§6.11).
>
> La lección vale más que el dato, y por eso se deja la frase tachada en vez de borrarla:
> ***"falló de noche" no es una causa, es una coincidencia con una sola observación
> detrás.*** Este documento la escribió dos veces —aquí y en la recolección— y las dos
> veces mandó a la sesión siguiente a "reintentar en horario hábil", que no arregla nada.

**Saldo: intacto, 8.588.** Las tres fallidas se reembolsaron solas.

#### La consecuencia grande, y no es de la recolección

**Un `202` puede terminar en `workflow_status: error` varios minutos después**, con
`payment_status: refunded` y el motivo en `error_detail`. Pasó tres veces en una noche.

Eso le pone una condición al despacho que ningún ADR contempla: **un pedido no se marca
despachado con la respuesta de creación**. Hay que esperar el estado terminal —releyendo
el envío o por el webhook— ~~y tener una rama para `error` que devuelva el pedido a la cola~~
en vez de dejarlo con una guía que no existe y que nadie va a recoger. Es el mismo
género del `408` de `§6.2`, pero al revés: allí el `408` había creado la guía; aquí el
`202` no la creó.

> **La condición se cumplió en `ADR-0033`, y la "cola" resultó no existir.** El pedido no
> sale de `EN_PREPARACION` hasta que la guía viva, así que una emisión que muere **nunca
> movió nada**: no hay cola a la que devolverlo ni inventario que tocar. Lo que parecía el
> trozo caro de este paso desapareció al poner la transición donde iba.

#### Lo que falta, y cuesta una sola emisión

1. **Qué campo es "address2"**, entre `apartment_number` y `area_level3`. La guía que
   los llevaba llenos nunca llegó a `success`, así que no se pudo volver a preguntar.
2. **Programar de verdad** una recolección y leerla (`GET /pickups/{id}`), que es lo
   único del tramo que sigue sin verse funcionar.

Las dos se cierran con **una guía viva de Servientrega o Coordinadora**, emitida en
horario hábil. Con 8.588 de saldo alcanza.

> **Se hizo al día siguiente y no bastó (§6.7).** La guía vivió, la recolección volvió a
> pedir el `address2`, y resultó que el campo que falta —el barrio— **no se puede mandar
> en el envío**: viaja por la cotización. Lo que sí quedó cerrado es cuál es el campo.
>
> **Y el punto 2 no se cierra con saldo (§6.10, §6.11).** Con el barrio puesto, la
> cobertura responde `200` con fechas y `POST /pickups` sigue respondiendo
> `422 ECONNREFUSED at PICKUP` — ocho intentos, tres días, tres horas (§6.11, §6.14). Esta lista pedía una
> emisión; lo que falta es que el proveedor levante su conector.

### 6.7 La guía viva, y el barrio que falta (2026-09-16, sexta parte)

`§6.6` dejó dos pendientes y dijo que se cerraban "con una guía viva de Servientrega
o Coordinadora, emitida en horario hábil. Con 8.588 de saldo alcanza". Se hizo, a las
09:23 de un miércoles. **La guía vivió. La recolección no, y el saldo se acabó.**

#### La emisión de día funciona, y tiene un estado más del que se creía

| Momento | `workflow_status` | Guía |
|---|---|---|
| `202` de creación | `in_progress` | `null` |
| ~40 s después | `creation_waiting` | `null` |
| 2 min 22 s después | `success` | `2269401749` |

**`creation_waiting` no aparece en la documentación de Skydropx ni se había visto en
ninguna sonda.** La de recolección lo trataba como terminal —cortaba en cuanto el estado
dejaba de ser `in_progress` o `pending`— y por eso el primer intento del día pareció otro
fracaso de transportadora cuando lo que pasaba era que la sonda no había esperado. Ya
está arreglado, junto con el tope del bucle: 40 vueltas, porque dos minutos no le caben a
veinte.

Eso refuerza lo que `§6.6` sacó de las tres muertes nocturnas, y le pone número: **la
espera entre el `202` y el estado terminal dura minutos y pasa por tres estados no
terminales** —`in_progress`, `pending`, `creation_waiting`—. El despacho no puede
marcarse con la respuesta de creación.

~~Y de paso, la intermitencia de `§6.6` queda confirmada por el otro lado: **de día, a la
primera y sin reintentos.**~~ Se emitió con Servientrega, forzada, porque era la que había
emitido bien de día; Coordinadora estaba 2.209 más barata y probablemente habría servido,
pero su contador de remisiones venía atascado esa noche y no era el día de averiguarlo.

> **Esa lectura era la trampa, y aquí se ve entera.** Una emisión de día que sale bien no
> confirma ninguna intermitencia horaria: confirma que esa emisión salió bien. La sospecha
> sobre el contador de Coordinadora ya estaba escrita en este mismo párrafo —"venía
> atascado"— y aun así la conclusión que se llevó la sesión siguiente fue la de la hora.
> **Se midió el 16 y el 17 (§6.10, §6.11): Coordinadora falla siempre y el conector de
> recolección está caído también a las 10:20 de un jueves.**

Y un dato suelto que se llevó por delante un pendiente de `§6.3`: **esta guía sí
trajo `label_url`** —`https://sb-pro.skydropx.com/s/s?id=…`—, cuando la del 15 no
lo trajo nunca, ni con el envío en `delivered`. Las dos son de Servientrega y las
dos por `POST /shipments`. Qué lo decide sigue sin saberse; lo que ya no se puede
decir es que esa transportadora no lo devuelve. Quien escriba el despacho **no
puede dar por hecho el rótulo**.

#### `Shipper address2` es el barrio, y por el envío no se puede mandar

`POST /pickups` sobre la guía viva respondió otra vez
`422 base: ["Shipper address2 not valid: null"]`. La hipótesis de `§6.6` —que faltaba
`apartment_number` o `area_level3`— era la buena a medias: **el envío se emitió con los
dos campos puestos y aun así falló**, porque uno de los dos nunca llegó.

Lo que guardó el envío, releído:

```
apartment_number: "401"      ← se mandó y quedó
area_level3:      null       ← se mandó "La Milagrosa" y se perdió
street_number:    (ni existe en la respuesta)
```

De ahí sale el nombre por descarte: **`address2` no puede ser `apartment_number`**, que
iba lleno cuando el error dijo `null`. Es `area_level3`, el barrio, que Servientrega
exige para recoger y que el envío no tiene.

Y el motivo de que se pierda está en el OpenAPI, no en un fallo: **`address_from` solo
declara `address_template_id`, `street1`, `name`, `company`, `phone`, `email`,
`reference`, `further_information` y `tax_id_number`.** Ni `area_level3`, ni
`street_number`, ni `postal_code`, ni `area_level1/2`. Todo lo demás que se le mande **se
descarta sin un error**: el `202` llega igual, la guía se emite igual, y el campo queda
en `null`. Es la misma familia del `declared_amount` fuera de sitio de `§6.4` —un campo
en el lugar equivocado que nadie rechaza— y la tercera vez que este proveedor cobra el
silencio más caro que un `422`.

La puerta del barrio es la otra, y ya estaba escrita en `§6.4` sin que se hubiera
conectado con esto: de la cotización se heredan `country_code`, `postal_code` y
**`area_level1/2/3`**. Nuestra cotización mandaba ciudad y departamento y nunca el
barrio. `tools/sonda-recoleccion.mjs` ya lo manda —origen `La Milagrosa`, destino
`Boston`—, y las seis tarifas siguen cotizando igual con el campo puesto.

Dos caminos se descartaron, gratis, antes de llegar ahí:

- **La plantilla de dirección por omisión no interviene.** Se marcó
  `535bd77b-…` como `default: true` y `POST /pickups` falló idéntico: el `shipper` de
  la recolección sale de la dirección del envío, no de la configuración de la cuenta.
  Se devolvió a `false`.
- **`verify_by_carriers` no sirve para esto.** Con `["servientrega","coordinadora"]`
  responde `422 {"error": "CARRIER_VERIFICATION_NOT_ENABLED"}`. La verificación de
  dirección existe para Inter Rapidísimo y nadie más; no hay forma de validar el origen
  contra la transportadora sin emitir.

⛔ Y `GET /pickups/coverage` volvió a responder `422 {"success": false, "message": null}`
con una guía en `success` recién nacida. **Van cinco guías distintas y cinco veces lo
mismo.** Ya no hay versión de "es que el envío no estaba listo" que lo sostenga.

#### Qué queda, y por qué no se puede hacer hoy

Queda **una sola comprobación**, y es la misma para los dos pendientes de `§6.6`: emitir
con el barrio en la cotización, ver si el envío lo hereda en `area_level3`, y si lo
hereda, programar la recolección y leerla. La sonda ya está lista para hacerlo de un
tirón.

**No alcanza el saldo.** La emisión de hoy costó 8.200 y dejó la cuenta en **388**; la
tarifa más barata con recolección por API es Coordinadora a 5.991. Los créditos se
pidieron el 14 de septiembre y siguen sin respuesta, así que el tramo de recolección
queda bloqueado por saldo, no por conocimiento — que es un sitio mucho mejor del que
estaba ayer.

### 6.8 El rastreo, medido y escrito (2026-09-16, séptima parte)

`§6.3` capturó el ciclo de seguimiento de la guía `873837506712` y dio por hecho que
con eso `ConsultorDeSeguimiento` se podía escribir. Casi: faltaba una pregunta que
nadie se había hecho, y la respuesta cambia el modelo. **Leer el rastreo no cuesta
saldo**, así que las cuatro variantes se probaron gratis con `tools/sonda-rastreo.mjs`.

#### `carrier_name` es obligatorio, y es el código de la plataforma

| Variante | Respuesta |
|---|---|
| `?tracking_number=873837506712&carrier_name=servientrega` | **200**, cuatro eventos |
| sin `carrier_name` | `404 "No se encontró eventos de rastreo para ese número de guía."` |
| `carrier_name=Servientrega` (el nombre visible) | `404`, el mismo |
| `/shipments/tracking/{guia}/{carrier}` en la ruta | `404 Not Found` |

Dos cosas de ahí:

- ❌ **La forma de la ruta que anotaba `ADR-0022` no existe.** Es con parámetros de
  consulta, como ya decía `§6.1`. La sección 1 de este documento estaba equivocada.
- ⚠️ **El código no se deriva del nombre.** Los seis de la cuenta, leídos de
  `GET /shipments/carrier_services`: `coordinadora`, `dhl`, `envia`,
  `interrapidisimo`, `ninetynineminutes`, `servientrega`. Cuatro se normalizarían
  solos; **"99 minutes" → `ninetynineminutes` no lo adivina nadie**, y "Envía Paquete
  Terrestre" es el servicio, no la transportadora.

**Y eso chocaba con el modelo**: `GuiaEnvio.transportadora` es texto libre que teclea
una persona en el panel. Consultar con eso devuelve 404 siempre, y el 404 —lo de
abajo— es indistinguible de "todavía no hay eventos": la conciliación habría
registrado "sin novedad" para despachos que nadie estaba mirando. `GuiaEnvio` gana
`codigoTransportadora`, opcional, que llenará el adaptador de emisión; las guías
tecleadas a mano se saltan y **se cuentan** en el resultado de la corrida. La razón
de que sea opcional y no obligatorio es de fondo: una guía escrita a mano puede no
existir en Skydropx, porque quien despacha pudo emitirla en la web de la
transportadora. Lo que decide si se puede conciliar no es quién la lleva, es si la
emitimos nosotros.

#### Un 404 es "todavía no hay eventos", no un fallo

De las cuatro guías emitidas, **solo la que se creó con `auto_advance` tiene rastro**.
Las otras tres —incluida `2269401749`, la guía viva del 16— responden 404. Una guía
recién emitida está exactamente en ese caso, así que el 404 va a ser lo habitual y el
adaptador lo trata como lista vacía sin ruido.

#### Y un detalle que desarma la opción que se había descartado

`description` y `event_description` **no son el mismo texto**: el segundo es el
primero en minúsculas ("Paquete en tránsito - guadalajara"). Importa porque al
diseñar el lector del webhook se evaluó derivar la llave de idempotencia de ese campo
para que los dos caminos convergieran; con los textos divergiendo entre endpoints, esa
salida era peor de lo que parecía. El evento se identifica por el `id` del rastreo,
que es un UUID y viene siempre.

#### Y la contradicción del retorno no era una contradicción

`§6.1` y `§6.4` decían cosas opuestas sobre la devolución: una que las suscripciones
siguen disparando el estado operativo real y no `in_return`, otra que `status` se queda
en `in_return` todo el trayecto. Releída la documentación entera, **las dos frases están
ahí y hablan de cosas distintas**: lo que se dispara con el estado operativo es la
*suscripción* —por eso no hay que migrar nada para seguir recibiendo eventos durante un
retorno—, y lo que se queda en `in_return` es el `status` del *cuerpo*, con el
movimiento real en `returned_status`. El ejemplo de la documentación lo confirma:
`status: "in_return"`, `returned: true`, `returned_status: "in_transit"`.

Importaba porque `EN_DEVOLUCION` dispara `RechazarEnEntrega`, que libera inventario.
Con `adr/0032` deja de importar por otro motivo: el estado no se lee del aviso.

Con eso, los tres puertos que `§6` dejó fallando cerrado **dejaron de estarlo**. El
lector del webhook se escribió el mismo día con los ejemplos de la documentación
(`adr/0032`), y lo único que seguía cerrado era la firma, esperando el secreto del
panel — que llegó esa misma tarde: `§6.9`.

### 6.9 El webhook, conectado y comprobado (2026-09-16, octava parte)

El secreto del webhook dejó de ser un pendiente. Lo verificado, y lo que costó.

#### El secreto no lo genera Skydropx: es una clave compartida que ponemos nosotros

El panel pide "Clave secreta" y la escribe quien configura la suscripción. Eso cambia
el orden que se había planeado —no hay nada que "copiar del panel"— y cambia la
operación: **el mismo valor vive en Secret Manager y en el panel, y se rota en los dos
o en ninguno.** Se generó con `secrets.token_hex(32)`, 64 caracteres hexadecimales.

Hexadecimal a propósito: sin espacios ni caracteres que un panel pueda recortar o
escapar al copiar.

#### Tres intentos perdidos por un byte, y la causa no era la que parecía

El valor subió mal tres veces seguidas. La primera fue un `
`: **`openssl` en Windows
termina la línea con CRLF**, y `tr -d '
'` se lleva el salto y deja el retorno. Pero
el arreglo no funcionó, y la razón era otra y más grande:

> **`$TEMP` en Git Bash vale `/tmp`, y `gcloud` es un programa de Windows que no lo
> entiende.** `wc` y `python` de MSYS miden un archivo; `gcloud` resuelve esa misma
> ruta contra la unidad actual (`C:\tmp\...`) y sube **otro archivo**. Por eso el
> archivo local medía 64 bytes y el secreto guardado 65, que es lo que no cuadraba.

Lo que lo cerró fue usar una ruta de Windows explícita —`C:/Users/.../Temp/sk.bin`, con
barras normales, que bash y gcloud resuelven igual— y escribir en binario (`'wb'`), sin
pipes ni redirección de por medio. Vale para cualquier herramienta de Windows lanzada
desde Git Bash: `gcloud`, `terraform`, `gradlew.bat`.

Y una trampa de medición encima: **`gcloud secrets versions access` no escribe los bytes
crudos en stdout** —lo dice su propia ayuda— y agrega un salto, así que `access | wc -c`
siempre da uno de más y un secreto correcto se ve idéntico a uno con basura al final. La
medición buena es `--out-file`.

#### La suscripción de esta cuenta: once eventos, todos de paquetes

Los que ofrece el panel: `Created`, `Exception`, `Picked_up`, `In_transit`, `Last_mile`,
`Delivery_attempt`, `Delivered`, `Delivered_to_branch`, `In_return`, `Canceled` y `Error`.
Se suscribieron los once. Tres cosas que esto le dice al código:

- ⚠️ **No hay eventos de `quotation`, `orders`, `rate`, `extra_charges` ni `pickups`.** La
  documentación los describe y el panel no los ofrece, así que el desvío "evento de otro
  tipo" del lector es hoy una defensa y no un camino que se recorra. El filtro por
  `data.type` sigue siendo necesario: sin él, el identificador que trae dentro un evento
  ajeno se leería como una guía.
- ⚠️ **Faltan dos de los doce estados: `destroyed` y `retained`.** No hay evento que los
  empuje, así que sólo aparecen cuando la conciliación programada pregunte —hasta seis
  horas después—. Y son dos de los cuatro que `adr/0022` marca como "piden ojo humano":
  el paquete quieto y nadie enterado. La red por debajo existe; el aviso no.
- ⚠️ **`Error` no es uno de los doce estados de seguimiento.** Es, casi seguro, el
  `workflow_status: error` de §6.6 — la emisión que muere minutos después con la guía
  reembolsada. Si lo es, el paso de la emisión tiene ahí el aviso que necesitaba para
  devolver el pedido a la cola sin esperar a la conciliación. **Hay que confirmarlo
  disparando ese evento de prueba y leyendo el cuerpo**, que es gratis.

#### El botón de prueba del panel: un evento real, sin gastar saldo

El panel manda eventos de prueba por tipo, con cuerpo real y firmados. Es la verificación
que `§6` daba por imposible sin emitir una guía, y no cuesta nada.

Disparando `In_return` contra dev, dos veces, la API respondió `200` —responde 200 siempre
(`adr/0022`)— y el registro dejó la prueba de lo que importa:

```
WARN c.t.a.p.envio.EnvioWebhookControlador : Evento de Skydropx para una guía que no es nuestra
```

Esa línea se escribe **después** de la puerta de la firma: el HMAC-SHA512 sobre los bytes
crudos cuadró con el secreto del panel, la cabecera `Authorization` con el prefijo `HMAC `
se leyó bien, y el lector sacó del cuerpo la guía `2943368350` —de la cuenta de ellos, no
nuestra—. La cabecera quedó en `Authorization`, el valor por omisión.

✅ **Los nombres del panel coinciden con los códigos que mapea el rastreo**, medido en el
cuerpo del evento de prueba: `"status": "delivery_attempt"`. Se dudó de ese —una `t` de
diferencia habría hecho que un intento de entrega se descartara en silencio al conciliar— y
la diferencia estaba en cómo se transcribió la lista, no en la plataforma. Vale la anotación
porque el modo de fallo es real: los códigos de un tercero se cotejan contra la fuente, no
contra una lista copiada a mano.

#### `error` es un estado trece, y ~~hoy~~ se descartaba en silencio

El cuerpo del evento de prueba de `Error` resolvió la pregunta abierta, y no como se
esperaba:

```json
"data": { "type": "packages",
          "attributes": { "status": "error", "tracking_number": "4889168485",
                          "returned": false, "returned_status": null } }
```

Es un evento **de paquete**, con guía, y su `status` es `error`. O sea que la plataforma usa
al menos **trece** estados y no los doce que `adr/0022` fijó y que
`MapeadorSeguimientoSkydropxV1` mapea uno a uno. `error` no está en esa tabla.

⚠️ **La consecuencia no es que falte una entrada en un mapa.** Un estado desconocido se
descarta *con su evento*, a propósito y bien —traducirlo al más parecido movería pedidos por
una corazonada—, pero **se descarta sin dejar rastro**: no hay log ni contador. Si el rastreo
devuelve eventos con `status: error`, `ConciliarGuia` no encuentra nada aplicable y responde
"sin novedad", que es exactamente lo que respondería una guía que va perfecta. Un envío
fallido y un envío tranquilo se ven iguales desde el registro y desde el panel.

Lo que **no** está medido, y hay que decirlo: el cuerpo de arriba es un evento de prueba
sintético del panel. Que un `workflow_status: error` de §6.6 —la emisión que muere minutos
después y se reembolsa— dispare además este evento de paquete es **plausible y no
comprobado**: encaja con que el panel lo liste entre los suscribibles, y ~~se confirma el día
que una emisión real vuelva a morir~~. Tampoco está medido si `GET /tracking` devuelve eventos
con ese `status` o si `error` vive sólo en el canal del webhook.

> **Respondido el 17 de septiembre (§6.12), releyendo las cuatro emisiones que ya habían
> muerto en esta cuenta: ninguna llegó a tener número de guía.** Y el rastreo se consulta por
> número, así que `error` **no puede llegar nunca por el canal de la conciliación** — no hay a
> qué preguntarle. Sólo podría llegar por webhook, y sólo para una guía que ya tuviera número
> antes de morir, que es un caso que no se ha visto. Consecuencia para el código: ninguna, y
> eso era lo que había que comprobar. `FALLIDO` se queda como no terminal y esa elección
> resultó costar cero.

**El descarte dejó de ser mudo el mismo día.** `MapeadorSeguimientoSkydropxV1` escribe ahora una
línea por rastreo con la guía, cuántos eventos se cayeron de cuántos y **qué códigos** no supo
traducir —`[error]`, cuando aparezca—, separando los estados nuevos de los eventos que llegan sin
fecha o sin identificador, porque piden cosas distintas: uno, decidir qué significa un código; el
otro, mirar si cambió la forma de la respuesta. La regla de no adivinar se conserva entera: el
evento se sigue descartando.

**`error` entró al dominio el mismo día, como `FALLIDO`.** El nombre es nuestro —la tabla del
mapeador traduce explícitamente— y `ERROR` en un dominio se lee como una excepción y no como lo que
es: un paquete que no va a moverse. Pide ojo humano, con lo que los estados quietos pasan de cuatro
a cinco, y **no es terminal**: parece una guía muerta, pero que el estado sea final es justo lo que
no se ha medido, y darlo por terminado haría que la conciliación dejara de preguntar por ese envío
para siempre. Seguir preguntando cuesta una llamada por vuelta, acotada por el tope del lote.

⚠️ **Y al ponerlo apareció que nadie mira esos estados.** `EstadoEnvio.exigeRevisionManual()` no lo
llama nada en producción: sólo las pruebas. La pregunta está bien planteada y no tiene quien la
haga —el panel no marca esos envíos y la conciliación no los separa—, así que hoy un paquete
retenido, destruido o fallido se ve igual que uno en tránsito. No es de este paso; queda escrito
porque un método que sólo se prueba a sí mismo parece cubierto y no cubre nada.

> **Cerrado el 17 de septiembre de 2026 con `ADR-0034`**: los cinco estados de envío y las dos
> situaciones de emisión salen en la **bandeja de revisión** del panel, que se vacía con un acuse
> con actor y nota, y a la que una guía acusada **vuelve** si le llega un evento posterior al
> acuse. El aviso de este párrafo es lo que lo encontró, así que sirvió; pero estuvo dos fases
> escrito, y la lección se queda: **un predicado con pruebas verdes y sin llamadas en producción
> pasa cualquier revisión** — la cobertura lo cuenta como cubierto y ArchUnit no tiene nada que
> decir.

~~Lo que **no** se hizo, y queda para el paso de la emisión: que un envío fallido **devuelva el pedido
a la cola**. Eso toca inventario y el grafo del pedido, y es la misma rama del `202` que muere.~~
**La cola no existe (`ADR-0033`)**: el pedido no sale de `EN_PREPARACION` hasta que la guía viva, así
que una emisión que muere no movió nada y no hay nada que devolver ni inventario que tocar. Y una
limitación que conviene saber:
el aviso vive en el registro y no en el resultado de la conciliación, porque contarlo ahí exige que
el puerto `ConsultorDeSeguimiento` devuelva lo descartado además de lo aplicable. Cambiar ese
contrato por un estado cuyo significado todavía no se ha decidido era ponerle el carro a los
caballos.

#### Lo que se vio de paso, cotizando desde el ambiente desplegado

Con las credenciales montadas, dev cotiza de verdad: Medellín, un celular del catálogo
sembrado, **Envía $7.850, un día estimado**. Dos defectos que aparecieron ahí y no son de
este paso:

- ⚠️ **La primera cotización de un contenido nuevo se pasa de la ventana de sondeo.** El
  primer intento devolvió `409 ENVIO_SIN_COBERTURA` a los diez segundos y el repetido
  —mismo cuerpo— `200` en 1,6 s, por la deduplicación por contenido de §6.2. Un comprador
  puede leer "sin cobertura" en un destino que sí la tiene, y acertar reintentando.
- ⚠️ **Una cotización que falla no deja una sola línea en el registro.** El `409` se ve
  igual si no hay tarifas, si las credenciales están mal o si el proveedor no responde. En
  este ambiente eso se distinguió midiendo el tiempo de respuesta, que no es forma.

**Los dos se arreglaron el mismo día.** El puerto `CotizadorEnvio` devuelve ahora
`ResultadoCotizacion` —con tarifas, sin cobertura, o no se pudo cotizar con su motivo— y el
endpoint responde **`503 COTIZACION_NO_DISPONIBLE`** cuando no se pudo preguntar, en vez del
`409` de cobertura. Cada fallo deja además una línea en el registro con el motivo, sin
direcciones ni credenciales dentro.

**La sorpresa fue el frontend: ya estaba bien.** `resumen.page.ts` distinguía desde antes
`sinCobertura()` de `errorCotizacion()`, con dos textos distintos y el comentario de por qué
—"mandar a cambiar una dirección que estaba bien por una caída nuestra sería culpar al
comprador"—. Lo que pasaba es que ese camino **nunca se alcanzaba**, porque el backend
convertía las caídas en 409 y el repositorio las traducía a `null`. No hubo una línea que
tocar en la vitrina: el defecto estaba entero del lado del servidor.

**Verificado en el navegador** (`docs/06-testing.md`: hay cosas que solo se ven abriendo la
pantalla), con el backend local apuntando a un puerto muerto para forzar el fallo:

- A domicilio, con Medellín y dirección completa, el resumen pinta *"No pudimos calcular el
  costo de envío en este momento. Vuelve a intentarlo en unos minutos, o elige recoger tu
  pedido en nuestro punto de Medellín"* — y no el texto de cobertura.
- **La recogida en el punto sigue elegible y completa el paso**: cambiando el tipo de entrega
  el aviso desaparece, el total vuelve a ser el subtotal y "Continuar" lleva a método de pago.
  Era la duda que dejaba abierta mandar el proveedor caído por el 503, y es lo que `adr/0021`
  exige: sin tarifa se vende con recogida.
- A domicilio y sin tarifa, "Continuar" **no pasa**. El botón sigue habilitado a propósito
  —uno deshabilitado sale del orden de tabulación— y es `enviar()` quien no deja seguir.

### 6.10 El saldo volvió, y tres cosas que dábamos por ciertas eran nuestras (2026-09-16, novena parte)

Skydropx recargó el sandbox: **COP 50.388**. Con eso se emitieron tres guías y se cerró lo que
`§6.6` y `§6.7` habían dejado bloqueado "por saldo, no por conocimiento". Resultó que parte sí
era conocimiento, y que **tres fallos que estaban anotados como del proveedor eran nuestros**.
Es la cuarta vez que este proveedor cobra el silencio más caro que un `422`.

Todo lo de esta sección se midió con `tools/sonda-emision-v2.mjs`.

#### El barrio: era `area_level3`, viaja por la cotización, y arregla dos cosas

`§6.7` dejó escrito que "Shipper address2" es `area_level3`, que el envío no lo puede mandar y
que la puerta es la cotización. **Confirmado, y funciona**: cotizando con
`area_level3: "La Milagrosa"` en el origen y `"Boston"` en el destino, el envío emitido los
hereda íntegros —se leen en las dos direcciones del `included`— sin que el cuerpo de
`POST /shipments` los mencione.

Con el barrio puesto:

- ✅ **`POST /pickups` deja de responder `Shipper address2 not valid: null`.** Nuestro cuerpo de
  recolección queda **validado entero**. Lo que falla ahora es el conector de la transportadora
  —`422 base: ["External carrier API service error: Carrier response is empty or timed out,
  status: CONNECTION_ERROR: ECONNREFUSED at PICKUP"]`, tres intentos seguidos a las 18:45—, que
  es un error de ellos y de la hora, no de forma.
- ✅ **`GET /pickups/coverage` responde `200` por primera vez**, y trae fechas de verdad:
  `{"success": true, "carrier": "SERVIENTREGA", "service": "STANDARD", "pickupDates":
  [{"date": "2026-09-17", "startHour": "09:00", "endHour": "18:00"}, {"date": "2026-09-18", …}]}`.

Eso **corrige `§6.6` y `§6.7`**, que lo dieron por roto del lado de Skydropx con todas las
letras: *"cuatro guías distintas y siempre `422 {"success": false, "message": null}`"*, *"van
cinco guías y cinco veces lo mismo; ya no hay versión de 'es que el envío no estaba listo' que lo
sostenga"*. La había, y era la nuestra: **las cinco guías se habían emitido sin barrio**. El
mensaje vacío que su especificación promete llenar sigue siendo un defecto de ellos; la causa
era de este lado.

⚠️ **Y deja una tarea que no es de la emisión**: `Direccion` no tiene barrio. Para que la
recolección funcione en producción hay que pedirlo en el checkout y mandarlo en la cotización.
Hasta entonces la recolección se programa a mano por el panel de Skydropx.

#### Coordinadora no puede emitir en este sandbox, y no es la hora

`§6.6` atribuyó las tres muertes nocturnas del 15 de septiembre a que "de noche los sistemas de
las transportadoras no responden", y `§6.7` lo dio por confirmado al emitir de día con
Servientrega. **Para Coordinadora es falso.** A las 18:35 de un miércoles, en pleno horario
hábil, la emisión murió con:

```
CARRIER_RESPONSE_ERROR · llave duplicada viola restricción de unicidad
«agw_remisiones_idx_codigo_remision» · Ya existe la llave (codigo_remision)=(93202421647)
```

**El mismo número de remisión que el 15 a las 22:30.** Su contador está atascado en ese valor de
forma permanente, así que Coordinadora devuelve el mismo `codigo_remision` para todo envío y
ninguno pasa. No es intermitencia: es determinista. Y es la tarifa **más barata** de la cuenta
—6.663 contra 8.200 de Servientrega—, o sea justo la que `TarifaEnvio.masEconomica` elige sola.
Cualquier prueba de emisión que no fuerce la transportadora se estrella contra ella.

Lo que sí se sostiene de `§6.6`: **el saldo volvió entero** (`payment_status: refunded`), y una
emisión fallida cuesta tiempo, no plata.

#### `POST /api/v2/shipments` devuelve un arreglo, y era verdad

`§6.4` resolvió "decisión #8: v2" leyendo la documentación, y nadie lo había ejercido nunca:
todo lo medido hasta hoy salió de v1. Ejercido:

- ✅ El cuerpo es **el mismo** que v1 —`shipment` con `rate_id`, `unique_shipment`,
  `sync_label_creation`, las dos direcciones y `packages`—. Con `rate_id` inválido los dos
  responden el **mismo** `422`, campo por campo.
- ✅ La respuesta es `202` con **`data` como arreglo**, e `included` al nivel superior con los
  paquetes y las direcciones de todos los envíos, amarrados por
  `relationships.shipment.data.id`.
- ⛔ **`GET /api/v2/shipments/{id}` no existe**: responde `404` con el HTML del sitio. Releer es
  siempre por v1, que devuelve `data` como objeto. O sea que **la integración usa las dos
  versiones a propósito**: v2 para crear, v1 para releer.

#### El multienvío, con dos guías de verdad

Dos bultos, tarifa `multishipment` de Servientrega por 16.400. La respuesta trajo **dos envíos**,
y el dato que importa para `adr/0031`:

| | Envío | Guía | `total` |
|---|---|---|---|
| 1 | `8bf880c9-…` | `2269401763` | **8.200** |
| 2 | `da585a66-…` | `2269401764` | **8.200** |
| | tarifa | — | 16.400 |

**Cada envío trae su propio costo, no el de la tarifa.** El total de la tarifa es la suma. Es
exactamente lo que `GuiaEnvio.costo` guarda y lo que `Envio.costoEnvio()` reconstruye sumando, así
que el modelo de `adr/0031` sale de la medición intacto. Cada guía trae además su propia
`label_url` y su propio `tracking_number`.

⚠️ **Y abre un caso que ningún ADR contempla: el fallo parcial.** Los dos envíos son
independientes y el de Coordinadora demuestra que uno puede morir solo. Un pedido de dos bultos
puede terminar con una guía viva, pagada, y otra muerta. Ver `adr/0033`.

#### Lo que la emisión devuelve, y no había que adivinar

Releyendo por v1 un envío en `success`, todo lo que el despacho necesita viene en la respuesta:

| Dato | Dónde | Ejemplo |
|---|---|---|
| Código de la transportadora | `data.attributes.carrier_name` | `servientrega` |
| Guía | `data.attributes.master_tracking_number` y el `tracking_number` del paquete | `2269401762` |
| Costo real de **esa** guía | `data.attributes.total` | `"8200.0"` |
| Etiqueta | `label_url` del paquete | `https://sb-pro.skydropx.com/s/s?id=…` |
| Motivo del fallo | `data.attributes.error_detail` | `{error_code, error_message, error_message_detail}` |

**`carrier_name` es el dato que `§6.8` dio por perdido.** Ahí quedó escrito que el código de la
transportadora no se puede derivar del nombre visible y que `GuiaEnvio.codigoTransportadora`
quedaba vacío para las guías tecleadas a mano. Para las que emitimos nosotros **no hay que
derivarlo**: lo devuelve la plataforma, con el mismo vocabulario que exige el rastreo.

#### Los tres estados no terminales, y cuánto duran de verdad

`§6.7` midió 2 min 22 s y tres estados no terminales (`in_progress`, `pending`,
`creation_waiting`). Hoy:

| Emisión | Tiempo hasta terminal |
|---|---|
| Servientrega, 1 bulto | ~45 s (`success`) |
| Servientrega, 2 bultos | ~25 s (`success` los dos) |
| Coordinadora | **más de 4 minutos** en `in_progress`, y después `error` |

O sea que la ventana no tiene tope conocido y el camino que más tarda es el que fracasa. Sondear
dentro de la petición del panel no es una opción: la espera es asíncrona o no es.

#### Y la integración, cerrada de punta a punta desde el panel

Con el código escrito, el recorrido completo se hizo en el navegador contra el sandbox real: pedido
creado por el checkout —cotizado en vivo en **7.850 con Envía**—, conciliado, y la guía pedida desde
el botón del panel.

```
20:32:57  Emision solicitada para el pedido … : Envia con 1 envio(s) [22419823-…]
20:33:42  Resolución de emisiones: 1 revisadas, 1 despachadas, 0 fallidas, 0 parciales
```

Cuarenta y cinco segundos entre el clic y el despacho, sin que nadie esperara mirando la pantalla.
Lo que quedó guardado:

| Campo | Valor |
|---|---|
| `transportadora` | `Envia` (el nombre visible, de la tarifa) |
| `codigo_transportadora` | `envia` (el de la plataforma, de la respuesta del envío) |
| `numero` | `034054505967` |
| `costo_envio` | `7850.00` |
| `url_etiqueta` | `https://sb-pro.skydropx.com/s/s?id=…` |

**`codigo_transportadora` es el dato que cierra el círculo de `§6.8`.** Ahí quedó escrito que el
código no se puede derivar del nombre y que las guías se saltarían en la conciliación por no tenerlo.
Las que emitimos nosotros nacen con él, así que la conciliación de `adr/0022` por fin tiene guías que
conciliar.

✅ **Y Envía emite.** Nunca se había probado —todas las emisiones anteriores fueron de 99 minutes,
Servientrega o Coordinadora— y es la más barata de las que quedan vivas después de descartar a
Coordinadora. Es además la que el selector elige solo, porque `TarifaEnvio.masEconomica` no mira la
recolección: `pickup` viene `false` en Envía, así que el día que la recolección por API se conecte
habrá que decidir si el criterio sigue siendo solo el precio.

**Saldo al cierre: COP 10.088**, de los 50.388 que entraron. Se gastaron 8.200 (Servientrega, una
guía), 16.400 (Servientrega, multienvío de dos) y 7.850 dos veces (Envía, el recorrido de punta a
punta antes y después de la revisión adversarial); los 6.663 de Coordinadora volvieron enteros.

El segundo recorrido no era un lujo: la revisión cambió el orden de las escrituras, añadió una
migración y quitó la transacción envolvente del endpoint, así que el primero ya no probaba el
código que quedó. Guía `034054505968`, dos vueltas de la tarea, y las dos columnas nuevas —el actor
y el estado `SOLICITADA` previo al cobro— verificadas en la base.

### 6.11 La recolección no falla por la hora: el conector está caído (2026-09-17, décima parte)

`§6.10` dejó la recolección como lo único abierto del lado del proveedor, con una explicación y un
remedio: *"es un error de ellos y de la hora, no de forma… se cierra reintentando en horario
hábil"*. **La explicación era falsa y el remedio no funciona.**

Reintentado el 17 de septiembre a las **10:20 de un jueves**, en pleno horario hábil, con la sonda
`tools/sonda-recoleccion.mjs` y reusando envíos ya emitidos —o sea sin gastar un peso—:

| Envío | Guía | `GET /pickups/coverage` | `POST /pickups` |
|---|---|---|---|
| `177d1939-…` | `2269401762` | **200**, fechas reales | `422` **ECONNREFUSED at PICKUP** |
| `8bf880c9-…` | `2269401763` | **200**, fechas reales | `422` **ECONNREFUSED at PICKUP** |

Van **cinco intentos**, en dos días distintos, a las 18:45 y a las 10:20, y con dos envíos
distintos. (**Tres más el 17 a las 16:06, con un envío nunca sondeado: §6.14.** Ahí se midió
además que el endpoint sí responde y valida — lo caído es el conector, no la recolección entera.) La cobertura responde `200` con fechas de verdad —`2026-09-18` y `2026-09-21`— así que
el envío es válido para Servientrega y nuestro cuerpo sigue validado entero; lo que no responde es
el conector de la transportadora dentro de Skydropx.

**Es el mismo patrón que Coordinadora.** `§6.6` atribuyó a la hora las muertes nocturnas de
Coordinadora y `§6.10` lo desmintió: su contador de remisiones está atascado. Aquí pasó igual, y
conviene dejar escrita la lección en vez del hecho: *"falló de noche"* no es una causa, es una
coincidencia con una sola observación detrás. Las dos veces costó una sesión entera creerle.

Consecuencias, y ninguna es de código:

- **La recolección se programa a mano por el panel de Skydropx**, como ya decía `§6.10`, y ahora
  sin fecha estimada de que deje de ser así: no depende de nosotros.
- **El criterio de elección de tarifa se queda como está**, solo por precio. Decidir si `pickup`
  debe pesar exige una recolección que funcione para comparar, y no la hay.
- La sonda queda ejercitable sin costo con `ENVIO=<id>`, que es como se midió esto.

### 6.12 Los tres pendientes que quedaban, medidos sin gastar un peso (2026-09-17, undécima parte)

Con `tools/sonda-oficina-y-fallido.mjs`, que solo lee y cotiza.

#### La entrega en oficina: la premisa había caducado, la conclusión no

`§6.2` la dio por imposible con este argumento: *"las cuatro transportadoras que tienen oficinas son
exactamente las cuatro que no cotizan"*. **Eso ya no es cierto**, y dejó de serlo el mismo día que se
escribió: se midió con cinco de las seis tarifas fallando por el `declared_amount` mal puesto, que
era nuestro y se corrigió en `§6.4`. Hoy cotizan cuatro.

Vuelto a medir con las tarifas vivas:

| Tarifa | Cotiza | `office_delivery` | `office_pickup` | `GET /office_points` |
|---|---|---|---|---|
| servientrega/standard | ✅ 8.200 | `false` | `false` | `200`, **total 0** |
| coordinadora/standard | ✅ 5.991 | `false` | `false` | `200`, **total 0** |
| envia/paquete_terrestre | ✅ 7.850 | `false` | `false` | `200`, **total 0** |
| ninetynineminutes/nextday | ✅ 9.897 | `false` | `false` | `200`, **total 0** |
| interrapidisimo/standard | ⛔ `no_coverage` | `false` | `false` | — |

**La conclusión se sostiene con mejor evidencia que antes.** Ya no es "no se puede saber porque no
cotizan": es que ninguna tarifa viva de esta cuenta ofrece oficina, y el catálogo de puntos responde
vacío para las cuatro. `§6.2` también anotaba que `office_points` sin `rate_id` da `400`; con tarifa
da `200` y cero puntos, que es una respuesta y no un bloqueo.

Una tercera forma de entrega en el checkout seguiría siendo una pantalla a la que nadie puede
llegar. Y ahora se sabe por qué, en vez de suponerlo.

#### `FALLIDO`: no puede llegar por el canal de la conciliación

`§6.9` dejó la pregunta abierta —"se confirma el día que una emisión real vuelva a morir"— y ya
habían muerto cuatro. Releídas:

| Envío | Transportadora | Guía | Pago |
|---|---|---|---|
| `87bc6955-…` | coordinadora | **null** | `refunded` |
| `037712dc-…` | coordinadora | **null** | `refunded` |
| `97735820-…` | servientrega | **null** | `refunded` |
| `e47c61d3-…` | coordinadora | **null** | `refunded` |

**Un envío que muere nunca llega a tener número de guía.** De ahí sale la respuesta, y no es la que
la pregunta esperaba: el rastreo se consulta por número, así que `error` **no puede llegar nunca por
el canal de la conciliación** — no hay a qué preguntarle. Solo podría llegar por webhook, y solo para
una guía que ya tuviera número antes de morir, que es un caso que no se ha visto.

Consecuencia para el código: **ninguna**, y eso es lo que había que comprobar. `EstadoEnvio.FALLIDO`
se queda como no terminal. El costo de esa elección —seguir preguntando por un envío que no se
moverá— resultó ser cero en el único camino que existe hoy, porque ese envío nunca entra a la
conciliación.

#### El barrio del destino, construido

Era lo único de esta integración que dependía de nosotros. `Direccion` gana `barrio`, el checkout lo
pide sin exigirlo, y la cotización lo manda como `area_level3` **solo cuando viene**: la clave se
omite en vez de viajar en nulo, que es la forma en que este campo ya rompió una vez (`§6.10`).

Verificado contra la API real: `POST /api/v1/envios/cotizacion` con `"barrio": "Boston"` responde
tarifa de Envía por 7.850.

### 6.13 El techo del valor declarado, medido (2026-09-17, duodécima parte)

Con `tools/sonda-techo-declarado.mjs`, que solo cotiza. El piso lo cerró `ADR-0035` con un `422`
medido; el techo solo tenía como fuente el formulario del panel, que no prueba que la API valide lo
mismo. Medellín → Bogotá, un celular de 20×15×8 y 500 g, moviendo únicamente el declarado:

| Declarado | Respuesta |
|---|---|
| 1.000.000 | ✅ cotiza |
| 4.999.999 | ✅ cotiza |
| **5.000.000** | ✅ cotiza |
| **5.000.001** | ⛔ `422 declared_amount: "El valor declarado debe ser menor o igual a 5000000"` |
| 6.000.000 | ⛔ el mismo `422` |
| 20.000.000 | ⛔ el mismo `422` |
| 3.000.000 **× dos bultos** (6.000.000 en total) | ✅ cotiza, las tres tarifas de siempre |

**El panel decía la verdad y el rango es cerrado por los dos extremos: [10.000, 5.000.000].** El
mensaje es simétrico al del mínimo, sale del mismo sitio —la validación de entrada, antes de que
ninguna transportadora vea nada— y por lo tanto tumba la cotización **entera**.

**Y es por bulto, igual que el mínimo.** Dos bultos de 3.000.000 suman seis millones y cotizan sin
una queja. Lo que no se puede es que un solo bulto pase de cinco, y un celular no se parte en dos.

#### Lo que apareció sin buscarlo: el declarado sí mueve el precio, pero solo en una

Entre el escalón de 1.000.000 y el de 4.999.999, con todo lo demás idéntico:

| Tarifa | Declarado 1.000.000 | Declarado 4.999.999 |
|---|---|---|
| servientrega/standard | 27.350 | **27.350** |
| envia/paquete_terrestre | 14.350 | **14.350** |
| coordinadora/standard | 20.827 | **54.427** |

Servientrega y Envía no se inmutan; Coordinadora cobra 33.600 más por cuatro millones más de
declarado, que es un 0,84 % — **coherente** con un seguro proporcional, aunque la sonda no puede
afirmar que ese sea el concepto. `ADR-0035` dejó anotado que elevar al mínimo "cuesta plata, cuánto
no se sabe": ahora se sabe que en dos de las tres tarifas vivas no cuesta nada, y en la tercera
elevar 2.000 pesos cuesta del orden de **diecisiete**.

#### Lo que esto abre, y es una decisión de negocio con plata encima

Un artículo de más de 5.000.000 —un celular de gama alta, un computador— **no se puede cotizar
hoy**, y el comprador recibe "no se pudo cotizar, intenta más tarde". O sea que el sistema ya
decidió no venderlo a domicilio; lo único que no hace es decirlo.

Las salidas son tres y ninguna es gratis: recortar el declarado al tope y aceptar que la
transportadora responda por cinco millones de un aparato de ocho; no ofrecer domicilio para esos
artículos, pero diciéndolo; o llevarlo a Skydropx y ver si el rango se amplía por contrato. La
primera es la única que parece un ajuste de código, y es la que más arriesga.

> **Decidido el mismo día en `ADR-0036`: la segunda.** Ese artículo no va a domicilio y el
> checkout lo dice nombrándolo, en `ArmadorDeBultos`, que es por donde pasan los dos caminos.

### 6.14 El conector sigue caído, y el barrio lo exige también el destino (2026-09-17, decimotercera parte)

Se volvió a medir el único punto que `§6.11` dejó del lado del proveedor. Todo lo de esta sección
salió **gratis**: reusando envíos ya emitidos con `ENVIO=<id>` y releyéndolos. Saldo antes y
después, **10.088**.

#### El conector: van ocho intentos, en tres días y tres horas

Jueves 17 de septiembre, **16:06**, con `tools/sonda-recoleccion.mjs`:

| Envío | Guía | `GET /pickups/coverage` | `POST /pickups` |
|---|---|---|---|
| `177d1939-…` | `2269401762` | **200**, fechas 18 y 21 de septiembre | `422` **ECONNREFUSED at PICKUP** |
| `8bf880c9-…` | `2269401763` | **200**, las mismas | `422` **ECONNREFUSED at PICKUP** |
| `da585a66-…` | `2269401764` | **200**, las mismas | `422` **ECONNREFUSED at PICKUP** |

El tercero **nunca se había sondeado**, así que esto ya no es una conclusión sobre los dos envíos
de siempre. Con los cinco de `§6.11` van **ocho intentos, tres días y tres horas distintas**
—18:45, 10:20 y 16:06—, y el mensaje es idéntico carácter por carácter. La cobertura sigue
respondiendo `200` con fechas de verdad, o sea que el envío es válido para Servientrega y nuestro
cuerpo sigue validado entero.

#### El endpoint está vivo, y eso no se sabía: el control de Envía

`§6.11` dejó escrito "lo que está caído es el conector de ellos", pero sin nada que lo separara de
"la recolección entera no responde". El control cuesta cero: pedir la recolección de un envío de
**Envía**, que no recoge por API.

Falló, **pero con otro error y en otra etapa**: `422 Recipient address2 not valid: null`, que es
validación de la dirección — antes de hablar con ninguna transportadora. Así que el endpoint
recibe, valida y discrimina; lo que no responde es el conector, un paso después. El
`ECONNREFUSED at PICKUP` se lee ahora con el "at PICKUP" en serio: dice en qué etapa murió.

#### El barrio: `POST /pickups` lo exige en las dos puntas

El control de arriba destapó algo que nadie había preguntado. Releyendo el barrio guardado de cada
envío —con `VER_ENVIO=<id>` de `tools/sonda-emision-v2.mjs`— contra lo que respondió la
recolección:

| Envío | Origen `area_level3` | Destino `area_level3` | `POST /pickups` |
|---|---|---|---|
| `197fef39-…` servientrega | `null` | `null` | `Shipper address2 not valid: null` |
| `eb69a24f-…` servientrega | `null` | `null` | `Shipper address2 not valid: null` |
| `90be4602-…` envia | `"La Milagrosa"` | **`null`** | `Recipient address2 not valid: null` |
| `177d1939-…` servientrega | `"La Milagrosa"` | `"Boston"` | pasa la validación → `ECONNREFUSED` |

**El error se mueve exactamente con el barrio que falta, y en ese orden: primero el origen,
después el destino.** `§6.10` cerró el del origen y nadie miró el otro extremo, porque las guías
que se sondearon después nacieron todas de la sonda, que manda barrio en las dos puntas.

⚠️ **Lo que no queda atado**: la fila que aísla el destino es de Envía y las demás de Servientrega.
La validación del origen se ve idéntica en las dos, el campo y el mensaje son el mismo salvo la
palabra que nombra la punta, y el barrio es el único dato que cambia entre las filas — pero
cerrarlo del todo exige una emisión de Servientrega sin barrio en destino, y eso cuesta 8.200 de
los 10.088. **Se decidió no gastarlo**: la rendija no cambia ninguna decisión.

**Y una lección de herramienta**: la relectura del envío **sí** devuelve las direcciones, en
`included` y no en `attributes`. Un script escrito de cero para esto las buscó donde no están y
las dio por ausentes; la sonda del propio repo ya sabía imprimirlas. Antes de escribir una sonda
nueva, mirar si una de las que hay ya contesta la pregunta.

#### La consecuencia, y es una decisión de producto abierta

`Direccion.barrio` es **opcional** en el checkout (`§6.12`: se pide sin exigirlo). Un comprador que
lo deje vacío produce un envío que, el día que el conector vuelva, **no se podrá recoger por API**:
ese paquete hay que llevarlo a mano.

Hoy no cambia nada, porque toda recolección es manual. La recomendación escrita, para que el día
que se retome no haya que pensarla de nuevo: **dejarlo opcional**, porque exigirlo le cobra
fricción a cada comprador de hoy por una capacidad que todavía no existe y que no depende de
nosotros. Lo que sí queda fijado es la regla de ese día: **sin barrio de destino, esa guía se
recoge a mano**. ~~`TODO (decisión de negocio): exigir el barrio en el checkout, o aceptar esa
regla.`~~ **Decidido el 17 de septiembre de 2026: se acepta la regla y el barrio sigue siendo
opcional**, escrito en la segunda corrección de `ADR-0021`. Exigirlo le cobraría fricción a cada
comprador de hoy por una capacidad que no existe todavía y que no depende de nosotros.

#### El criterio de tarifa, remedido y sin cambios

Cotizando otra vez, las banderas por tarifa siguen como las dejó `§6.4`:

| Tarifa | Estado | Precio | `pickup` |
|---|---|---|---|
| coordinadora/standard | cotiza | **5.991** | `true` — pero **no puede emitir** (§6.10) |
| envia/paquete_terrestre | cotiza | 7.850 | `false` |
| servientrega/standard | cotiza | 8.200 | `true` |
| ninetynineminutes/nextday | cotiza | 9.897 | `false` |
| interrapidisimo/standard | `no_coverage` | — | `true` |

La más barata no emite y la más barata de las que emiten no recoge por API. **Decidir si `pickup`
debe pesar en el criterio sigue exigiendo una recolección que funcione para comparar**, y el punto
anterior dice que no la hay.

### 6.15 Lo que se recauda en la puerta es el valor declarado, y punto (2026-09-17, decimocuarta parte)

La pregunta que quedaba para decidir `ADR-0037`: `recipient_pays_shipping` existe, ¿hace algo? La
respuesta de §6.5 —"no cambia el monto en la cotización"— dejaba dos lecturas abiertas: o el flete
se suma al emitir, que es el "si aplica" de la documentación, o el campo no hace nada.

Medido en tres puntos, y los tres dicen lo mismo con el valor declarado en 10.000:

| Dónde | `recipient_pays_shipping` | `on_delivery_amount` |
|---|---|---|
| Cotización sin la bandera | `false` | `"10000.0"` |
| Cotización con la bandera | **`true`** (lo devuelve) | `"10000.0"` |
| **Envío creado y pagado**, con la bandera | — | `"10000.0"` |

**La plataforma acepta el campo y lo guarda, y el flete no aparece por ninguna parte.** No es que
esté mal escrito ni mal puesto —el eco vuelve en `true`—, es que el "si aplica" no aplica en esta
cuenta. Con eso, **la contraentrega recauda el valor declarado y solo el valor declarado**, y el
flete no se puede delegar en la plataforma.

Dos cosas más que el mismo ejercicio dejó, y la segunda es cara:

- ✅ **El envío expone `on_delivery_amount` y `on_delivery_status`.** Se comprobó gratis antes de
  gastar nada, releyendo un envío viejo emitido sin recaudo: los dos campos están y vienen en
  `null`. O sea que el campo existe, que el `null` significa "sin recaudo" y no "sin campo", y que
  `on_delivery_status` sirve para conciliar guía por guía como prometió §6.4.
- ⛔ ~~**Envía tampoco puede emitir en este sandbox.**~~ **Lo que no se puede emitir es una
  contraentrega con Envía** (corregido el 18 de septiembre, `§6.17`: sin recaudo emite a la
  primera). Lo que se midió el 17 fue esto: `workflow_status: error` con
  `CARRIER_RESPONSE_ERROR` y el detalle: *"External carrier API service error: status code 400
  reason: **Usuario o Password incorrecto at LABEL_NUMBER**"*. Es la misma forma que el
  `ECONNREFUSED at PICKUP` de §6.11 —la etapa va al final del mensaje— y contradice lo que se daba
  por sabido desde el 16, que Envía era una de las tres que sí emiten. Quedan dos lecturas y
  **no se gastó en separarlas**: o las credenciales de etiqueta de Envía se rompieron del lado de
  Skydropx después del 16, o el recaudo usa otro camino de credenciales. `TODO (barato, y solo si
  hace falta): emitir con Envía SIN recaudo. Si falla igual, se rompió del lado de ellos y no tiene
  nada que ver con la contraentrega.` ~~`TODO`~~ **Cerrado el 18 de septiembre (`§6.17`), emitiendo:
  Envía emitió sin recaudo a la primera —`success`, guía `034054505970`, con rótulo— con todo lo
  demás igual. Sus credenciales de etiqueta no están rotas: lo que falla es el camino del recaudo.
  Esta sección decía "Envía tampoco puede emitir" y eso es falso; lo cierto es que no se puede
  emitir una contraentrega con Envía.**

**La consecuencia comercial es la que duele.** De las tres tarifas que sobreviven a una cotización
con recaudo en Medellín, **las dos más baratas no pueden emitir**: Coordinadora por el contador de
remisiones atascado (§6.10) y Envía por esto. La única que queda es **99 minutes, a 9.897 contra los
5.991 de Coordinadora**. Un contraentrega en este sandbox cuesta el 65 % más que el mismo envío
pagado en línea, y no por la tarifa sino por quién puede emitirla.

**Costo del ejercicio: cero.** El envío `5e4edbff-be5d-400d-8645-9c1b9a52cd9c` cobró 7.850 al
crearse y los devolvió al fallar —`payment_status: refunded`—. Saldo antes 10.088, durante 2.238,
después **10.088**. Confirma por cuarta vez que una emisión fallida cuesta tiempo y no plata, y
que `202` / `paid` no es una guía.

**Y una nota de método**, porque se repitió el patrón: antes de gastar el saldo se comprobó gratis
que el objeto del envío expusiera siquiera los campos de la pregunta. Si no los hubiera expuesto,
emitir no habría contestado nada y el saldo se habría ido igual. Ese chequeo vive en
`VER_ENVIO=<id> node tools/sonda-recaudo.mjs`, que busca toda clave de contraentrega a cualquier
profundidad, también dentro de `included`.

### 6.16 Los cobros extra, medidos sin gastar un peso (2026-09-18, decimoquinta parte)

`GET /api/v1/finance/extra-charges` es el único tramo de finanzas que la plataforma ofrecía y nadie
había mirado. §2.5 lo listaba entre "lo que podemos personalizar y hoy no estamos usando" desde
antes de la primera guía, con una frase que describía bien el riesgo: es el mecanismo por el que
**un peso mal declarado se reliquida**, y eso es dinero que sale del crédito sin que el pedido se
entere.

**Lo medido, con `tools/sonda-sobrecostos.mjs`** (solo lee; no emite nada y no consume saldo):

| Pregunta | Respuesta |
|---|---|
| `GET /api/v1/finance/extra-charges` | ✅ `200`. El sobre es `{ data: [], meta: {...} }` |
| `meta` | ✅ `current_page`, `next_page`, `prev_page`, `total_pages`, `total_count`. Coincide **campo por campo** con lo que declara el esquema |
| `GET /api/v1/finance/extra_charges` (guion bajo) | ❌ `404` con HTML. La grafía es con guion, como en el inventario de la API |
| Cobros en la cuenta | **Cero.** `total_count: 0`, con cinco guías emitidas |
| Saldo, de control | 10.088, igual que el 17 de septiembre |

**Y la mitad que no se pudo medir, que es la que importa declarar**: con la lista vacía, la forma de
un ítem no se comprobó contra nada. Sí la **declara el OpenAPI** (`/es-CO/api-docs.json`), y eso es
una fuente y no una suposición: `package_id`, `shipment_id`, `status`, `carrier`, `service`,
`tracking_number`, `label_date`, `detection_date`, `amount`, `charge_type` y `metadata`. Tres cosas
de ahí cambiaron el diseño:

- **`amount` viene como texto** (`"15.50"`) y **sin moneda**. Se lee con `BigDecimal` —regla dura
  #6— y se toma como pesos porque el cargo se descuenta del crédito y el crédito está en COP, que es
  lo único medido al respecto. La suposición queda escrita en `SobrecostoDeEnvio`, no en un comentario
  perdido.
- **No hay identificador del cargo.** Hay dos del envío (`shipment_id`, `package_id`) y ninguno del
  cobro, así que la identidad para "de esto ya avisé" se compone: envío, tipo, monto y fecha de
  detección (`SobrecostoDeEnvio.clave()`). Lleva el monto a propósito — una reliquidación por otra
  cifra vuelve a avisar, porque enterarse dos veces de algo de dinero es el error que se prefiere.
- **`charge_type` es el nombre de la clase de Rails**: el ejemplo es `ExtraCharge::Overweight`. Viaja
  al correo tal cual, sin traducir, porque es la misma cadena que va a ver quien busque el cargo en el
  panel.

El endpoint además **filtra por `start_date`/`end_date` sobre la fecha de detección** y pagina con
`page`/`per_page` (máximo 20). El vigilante pregunta por una ventana de treinta días y no "por
todo": el orden en que devuelve los cobros **no está documentado**, así que pedir "los recientes"
confiando en el orden sería apoyarse en algo que nadie prometió.

**Lo que queda abierto, y nace aquí:** los campos de peso que el cuerpo del *webhook* documenta
—`real_weight`, `original_weight`, `discrepancy_weight` (§6.4)— **no están en este endpoint**. Lo
más probable es que vengan dentro de `metadata`, que el esquema describe como "metadatos adicionales,
puede estar vacío" y no detalla. Se sabrá con el primer cobro real: `VOLCAR=1 node
tools/sonda-sobrecostos.mjs` imprime el cuerpo entero. Hasta entonces el correo dice cuánto y de qué
guía, y no cuántos gramos de más — que es lo que haría falta para corregir la medida del catálogo sin
abrir el panel.

### 6.17 Lo que falta para conciliar el recaudo solo no es código: es un dato que nadie publica (2026-09-18, decimosexta parte)

Tres mediciones, las tres **gratis**: saldo 10.088 antes y 10.088 después.

#### La recolección: noveno intento, cuarto día, mensaje idéntico

Viernes 18 de septiembre, sobre el envio `177d1939-6269-4bbb-a08f-68e5ae754e73` de Servientrega
—el mismo de `§6.14`, reusado con `ENVIO=<id>`—:

| Paso | Respuesta |
|---|---|
| `GET /pickups/coverage` | ✅ `200`, `SERVIENTREGA/STANDARD`, fechas **18 y 21 de septiembre** |
| `POST /pickups` | ⛔ `422` **`ECONNREFUSED at PICKUP`** |

Van **nueve intentos en cuatro días** y el mensaje sigue siendo el mismo carácter por carácter. La
cobertura responde con fechas de verdad, o sea que nuestro cuerpo sigue validado entero y el envio
sigue siendo válido para la transportadora: lo único que no responde es el conector, un paso
después. **Nada que decidir ni que construir de nuestro lado.**

#### `on_delivery_status` no lo documenta ninguna de las dos fuentes

La pregunta era si se puede conciliar el recaudo solo, releyendo el envío. La respuesta es que hoy
no, y por primera vez la causa está medida y no supuesta:

| Fuente | `on_delivery_status` | `on_delivery_amount` | `cash_on_delivery` | `recipient_pays_shipping` |
|---|---|---|---|---|
| `/es-CO/api-docs.json` (OpenAPI, 305 KB) | ausente | ausente | ausente | ausente |
| `/es-CO/api-docs` (HTML, 414 K caracteres de texto) | ausente | ausente | ausente | ausente |

Los campos **existen en la respuesta** —`§6.15` los leyó— y **no los declara nadie**. Es el caso
contrario al de los cobros extra de `§6.16`, donde el OpenAPI declaraba el esquema entero: ahí había
una fuente, aquí no hay ninguna.

Y la cuenta tampoco puede contestar. De los quince envíos que tiene, **uno solo se creó con recaudo**
—`5e4edbff-…`, el de `§6.15`— y murió al emitir: su `on_delivery_amount` dice `"10000.0"` y su
`on_delivery_status` viene en `null`. O sea que **el vocabulario del campo no se puede medir contra
esta cuenta**: ningún recaudo llegó nunca a cobrarse, así que nadie ha visto un valor distinto de
`null`. Mapear estados que no se han visto es la suposición que esta integración ya pagó cuatro
veces, y por eso **el tramo se paró antes de escribir el mapeo**.

Hay una segunda cosa que falta y es aparte del vocabulario: **`ConciliarRecaudo` exige la comisión**,
y el campo no la trae. De dónde sale la comisión depende de la decisión 6 de `§5` —créditos sin
comisión, o banco con comisión los jueves—, que sigue abierta y es contable. Con "créditos" la
conciliación se puede automatizar entera, porque la comisión es cero; con "banco" lo máximo que se
puede automatizar es el aviso.

#### Envía: los dos éxitos son del 16, así que `§6.15` sigue en pie

Listando los envíos de la cuenta con fecha —una lectura, sin costo— aparecieron dos de **Envía en
`success` con guía real** (`034054505967` y `034054505968`), que a primera vista contradecían
`§6.15`. No la contradicen: los dos son del **16 de septiembre**, anteriores a la rotura, y los dos
**sin recaudo** (`on_delivery_amount: null`). El único de Envía que falló es el del **17**, y es el
único **con recaudo**.

#### Y la emisión que las separa: **Envía sí emite. Lo que rompe es el recaudo**

Se gastó, con permiso, y contestó limpio. `SIN_PICKUP=1 TRANSPORTADORA=envia DECLARADO=10000
EMITIR=1 node tools/sonda-emision-v2.mjs`:

| | 17 de septiembre, **con** recaudo | 18 de septiembre, **sin** recaudo |
|---|---|---|
| `workflow_status` | `error` | ✅ **`success`** |
| Guía | ninguna | ✅ **`034054505970`** |
| `error_detail` | *"status code 400 reason: Usuario o Password incorrecto at LABEL_NUMBER"* | `null` |
| Rótulo | — | ✅ `label_url` presente |
| Costo | 7.850, reembolsados | 7.850, **cobrados** |

Misma cuenta, misma transportadora, mismo servicio (`paquete_terrestre`), mismo valor declarado.
**La única diferencia es la contraentrega**, y es la que decide. Las credenciales de etiqueta de
Envía no están rotas: lo que no funciona es el camino del recaudo, exactamente la segunda de las
dos lecturas que `§6.15` dejó abiertas.

Y de paso corrige a quién se le atribuye el fallo. Durante un día este documento dijo "Envía tampoco
puede emitir", que es falso: **Envía emite, y lo que no se puede emitir en este sandbox es una
contraentrega con Envía.** Es la quinta vez en esta integración que una conclusión correcta
descansaba sobre una causa equivocada, y van tres veces que lo destapa volver a medir algo que el
documento daba por cerrado.

**La consecuencia comercial no cambia, y sigue siendo la que duele.** De las tres tarifas que
sobreviven a una cotización con recaudo, Coordinadora no puede emitir por su contador de remisiones
y Envía no puede emitir **por ser recaudo**. Queda **99 minutes a 9.897** contra los 5.991 de
Coordinadora. Lo que cambia es qué habría que pedirle a Skydropx: no "arreglen las credenciales de
Envía", sino **"la contraentrega con Envía falla al pedir el número de guía"**.

**Costo: 7.850.** Saldo 10.088 → **2.238**, por debajo del umbral de 50.000, así que el vigilante de
saldo bajo avisa —que es exactamente lo que tiene que hacer—.

#### Lo que se construyó, ya que el mapeo no se podía

Las dos mediciones que faltan —la del recaudo y la de `metadata` de los cobros extra— dependían de
que alguien se acordara de correr una sonda el día exacto, meses después, sabiendo que el dato llega
sin avisar. **Ahora ocurren solas**, en `SkydropxClient`:

- Al releer un envio que lleva recaudo, la primera vez que pasa por una instancia se registra
  `on_delivery_amount`, `on_delivery_status` y `workflow_status` —y **solo esos tres**: el cuerpo del
  envio lleva el nombre, el teléfono y la dirección de quien compró, y volcarlo entero para medir un
  campo de dinero sería meter datos personales donde no hacen falta.
- Al leer el primer cobro extra, se registran **los nombres** de sus campos y los de `metadata`,
  nunca sus valores: lo desconocido es justo `metadata`, y escribir en el registro el contenido de
  algo cuya forma nadie ha visto es aceptar a ciegas lo que la plataforma meta ahí. Los nombres
  contestan la pregunta abierta —si los tres pesos viven ahí dentro— sin arrastrar datos de nadie.

Con el primer despacho contraentrega y el primer cobro real, las dos preguntas se contestan sin que
nadie vigile nada.

### 6.18 La cobertura del país entero, medida por primera vez (2026-09-19, decimoséptima parte)

Todo lo que este documento sabía de cobertura venía de **dos ciudades**: la tabla de `§6.5`, con
Medellín y Bogotá. Sobre esa muestra, los términos y condiciones publican *"Despachamos a todo el
territorio nacional"* y `docs/12` la da por buena.

El checkout, mientras tanto, tiene `ENVIO_SIN_COBERTURA` y cae a la recogida en el punto con un
texto que lo explica. **O sea que la pregunta nunca fue si el mecanismo funciona: es a cuánta gente
le toca**, y eso no se había medido nunca.

#### Por qué se pudo medir hoy y no antes

Por dos cosas que ya estaban y que nadie había juntado:

1. **La lista DIVIPOLA completa vive en el repositorio** desde el 4 de septiembre de 2026 —33
   departamentos y 1122 municipios, en
   `apps/web/src/app/features/checkout/domain/geografia-co.datos.ts`— porque es la que llena el
   selector de ciudad del checkout. La sonda la lee de ahí y no de una copia: así mide exactamente
   los destinos que se le ofrecen a quien compra, y el día que el DANE cambie la división
   territorial las dos cosas cambian juntas.
2. **Cotizar no gasta saldo.** Emitir cuesta; preguntar no. Es la misma propiedad que permitió medir
   el piso y el techo del valor declarado sin gastar un peso (`§6.12`, `§6.13`).

#### Cómo mide

`tools/sonda-cobertura.mjs`, con el patrón de las once sondas anteriores. Por cada municipio, **dos
cotizaciones**: una sin recaudo y otra con recaudo. No son la misma pregunta y confundirlas ya costó
una vez — sobrevivir a una cotización con recaudo es la señal de cobertura de contraentrega (`§6`),
y Servientrega es el caso de libro: cotiza sin recaudo y se cae en cuanto se le pide con él.

Un solo artículo de peso y valor medianos —25×18×8 cm, 0,8 kg, 250.000 declarados: un celular con su
caja, que es el grueso del catálogo— para que lo único que varíe entre una medición y la siguiente
sea el destino.

Tres cosas que la sonda distingue y que un conteo ingenuo mezclaría:

- **"Sin cobertura" no es "no se pudo preguntar".** Un `422` es el proveedor contestando y
  rechazando el cuerpo; un `5xx` o un tiempo agotado es no saber. Se cuentan aparte, que es la misma
  distinción que `adr/0039` metió en el checkout.
- **Las tarifas que quedan en `pending`** al cerrar la cotización se cuentan aparte. `§6.5` dejó
  escrito que `is_completed: true` puede volver con una tarifa todavía sin resolver y que el
  mapeador la descarta sin verla: a mil cotizaciones se sabrá si eso pasa a menudo o fue una
  casualidad, y si la que se pierde puede ser la más barata.
- **Es reanudable**, con el progreso escrito tras cada municipio. Cuatro horas contra un proveedor
  que admite dos peticiones por segundo se cortan, y volver a empezar desde cero es como se acaba no
  midiendo nunca.

#### El resultado

**1122 municipios, ninguno sin medir.**

| | sin recaudo | con recaudo |
|---|---|---|
| **Cotiza** | **1044 (93,0 %)** | **1008 (89,8 %)** |
| Nadie cubre ese destino | 4 (0,4 %) | 40 (3,6 %) |
| El proveedor rechaza el código DANE | 74 (6,6 %) | 74 (6,6 %) |
| No se pudo preguntar | 0 | 0 |

**"Despachamos a todo el territorio nacional" se sostiene, con el matiz que el §8 de los términos ya
trae.** Nueve de cada diez municipios del país cotizan, y en ocho de cada nueve de esos se puede
además pagar contra entrega. La recomendación para `docs/14` punto 4 es acercar el matiz al primer
párrafo, no reescribir la frase.

#### Lo que el total esconde, y es lo interesante

**Solo 4 municipios de Colombia no tienen quien los cubra**: Los Andes (Nariño) y tres de Guainía
—San Felipe, Puerto Colombia y Cacahual—. Cuatro, sobre 1122. El `ENVIO_SIN_COBERTURA` del checkout
existía desde la Fase 7 sin que nadie supiera si llegaría a dispararse alguna vez en producción: la
respuesta es que sí, y que va a ser rarísimo.

**Los 74 rechazos no son falta de cobertura, y esa es la conclusión que cambia algo.** Los 74
responden lo mismo, literal:

```
422 {"errors":{"address_to":{"postal_code":["no existe"]}}}
```

No es que ninguna transportadora llegue: es que **el catálogo de códigos DANE de Skydropx no tiene
ese municipio**. Para esos destinos el checkout ni siquiera puede preguntar, y quien compra desde
ahí ve una cotización que falla. Se concentran donde uno esperaría —Chocó 13, Nariño 8, Amazonas 8,
Vaupés 5— pero hay tres en Antioquia y dos en Cundinamarca, o sea que no es solo geografía remota:
es un catálogo incompleto.

**Y esto sí es accionable de este lado**, a diferencia de la cobertura real: es una lista concreta de
74 códigos para pedirle a Skydropx que agregue, con el municipio y el departamento de cada uno — **la petición quedó redactada el 19 de septiembre en `docs/tramites/2026-09-19-skydropx.md`, con el CSV adjunto**. Es
la primera petición a la plataforma que no depende de que un conector vuelva a funcionar.

**La contraentrega pierde 40 municipios más que el envío normal**, y la mitad están concentrados en
Santander (11), Meta (4), Cauca (3) y Guaviare (3). Es la señal de cobertura de recaudo funcionando
como `§6` la describió, ahora sobre el país entero en vez de sobre dos ciudades.

**Una sola tarifa quedó en `pending`** al cerrar su cotización, en 2244 cotizaciones. El defecto que
`§6.5` levantó —`is_completed: true` con una tarifa sin resolver, que el mapeador descarta sin
verla— existe, pero pasa una vez de cada dos mil. No justifica alargar el checkout contra un
proveedor que ya es lento. **La decisión que `§6.5` mandó al ADR se puede tomar ahora y es: no se
espera.**

#### Los 74 códigos DANE para pedirle a Skydropx

La petición, lista para mandarse. Cada uno responde `postal_code: "no existe"` a una cotización con
el resto del cuerpo idéntico a uno que sí funciona, así que no hay nada que interpretar.

<details>
<summary>Los 74, por código</summary>

| Código DANE | Departamento | Municipio |
|---|---|---|
| `05150` | Antioquia | Carolina |
| `05674` | Antioquia | San Vicente Ferrer |
| `05873` | Antioquia | Vigía del Fuerte |
| `13030` | Bolívar | Altos del Rosario |
| `13468` | Bolívar | Santa Cruz de Mompox |
| `13647` | Bolívar | San Estanislao |
| `13667` | Bolívar | San Martín de Loba |
| `13683` | Bolívar | Santa Rosa |
| `15401` | Boyacá | La Victoria |
| `19418` | Cauca | López de Micay |
| `19532` | Cauca | Patía |
| `19548` | Cauca | Piendamó - Tunía |
| `19693` | Cauca | San Sebastián |
| `19760` | Cauca | Sotará - Paispamba |
| `20013` | Cesar | Agustín Codazzi |
| `20238` | Cesar | El Copey |
| `23678` | Córdoba | San Carlos |
| `25653` | Cundinamarca | San Cayetano |
| `25843` | Cundinamarca | Villa de San Diego de Ubaté |
| `27025` | Chocó | Alto Baudó |
| `27050` | Chocó | Atrato |
| `27099` | Chocó | Bojayá |
| `27135` | Chocó | El Cantón del San Pablo |
| `27150` | Chocó | Carmen del Darién |
| `27250` | Chocó | El Litoral del San Juan |
| `27425` | Chocó | Medio Atrato |
| `27430` | Chocó | Medio Baudó |
| `27450` | Chocó | Medio San Juan |
| `27493` | Chocó | Nuevo Belén de Bajirá |
| `27600` | Chocó | Río Quito |
| `27660` | Chocó | San José del Palmar |
| `27810` | Chocó | Unión Panamericana |
| `47258` | Magdalena | El Piñón |
| `47545` | Magdalena | Pijiño del Carmen |
| `47660` | Magdalena | Sabanas de San Ángel |
| `47960` | Magdalena | Zapayán |
| `52019` | Nariño | Albán |
| `52051` | Nariño | Arboleda |
| `52203` | Nariño | Colón |
| `52224` | Nariño | Cuaspud Carlosama |
| `52427` | Nariño | Magüí |
| `52696` | Nariño | Santa Bárbara |
| `52699` | Nariño | Santacruz |
| `52835` | Nariño | San Andrés de Tumaco |
| `54344` | Norte de Santander | Hacarí |
| `68370` | Santander | Jordán |
| `68673` | Santander | San Benito |
| `70523` | Sucre | Palmito |
| `70702` | Sucre | San Juan de Betulia |
| `70742` | Sucre | San Luis de Sincé |
| `70820` | Sucre | Santiago de Tolú |
| `70823` | Sucre | San José de Toluviejo |
| `73055` | Tolima | Armero |
| `73443` | Tolima | San Sebastián de Mariquita |
| `76111` | Valle del Cauca | Guadalajara de Buga |
| `76126` | Valle del Cauca | Calima |
| `86865` | Putumayo | Valle del Guamuez |
| `88564` | Archipiélago de San Andrés, Providencia y Santa Catalina | Providencia |
| `91263` | Amazonas | El Encanto |
| `91405` | Amazonas | La Chorrera |
| `91407` | Amazonas | La Pedrera |
| `91430` | Amazonas | La Victoria |
| `91460` | Amazonas | Mirití - Paraná |
| `91530` | Amazonas | Puerto Alegría |
| `91536` | Amazonas | Puerto Arica |
| `91798` | Amazonas | Tarapacá |
| `94885` | Guainía | La Guadalupe |
| `94887` | Guainía | Pana Pana |
| `94888` | Guainía | Morichal |
| `97161` | Vaupés | Carurú |
| `97511` | Vaupés | Pacoa |
| `97666` | Vaupés | Taraira |
| `97777` | Vaupés | Papunahua |
| `97889` | Vaupés | Yavaraté |

</details>

#### La primera corrida dio 62,8 % y era mentira

Conviene que quede escrito, porque el número llegó a estar impreso y a punto de entrar en una
decisión legal.

La sonda autenticaba **una sola vez al arrancar** y la corrida dura cuatro horas. A partir del
municipio 597 todo respondió `401`: **377 municipios contados como "no se pudo medir"**, y el
resumen imprimió un 62,8 % de cobertura. Ese número no medía el país — medía a qué hora caducó el
token.

**Lo delator estaba en la tabla por departamento:** Santander 0/87, Valle del Cauca 0/42, Tolima
0/47, Norte de Santander 0/40. Departamentos enteros en cero, en bloque y por orden alfabético.
Ninguna geografía se comporta así. Comparar con la tabla de arriba —Santander 85/87, Valle 40/42,
Tolima 45/47— da la medida del error.

Si ese 62,8 % se hubiera publicado, habría ido derecho al punto 4 de `docs/14` como el dato que
decide si *"Despachamos a todo el territorio nacional"* se sostiene, y la respuesta habría sido "no"
cuando es "sí". **Una decisión legal tomada sobre la hora a la que caducó un token.**

Es la misma lección que este proyecto ya tenía escrita sobre el proxy de diagnóstico que estuvo
roto: **una herramienta de diagnóstico también es una variable del experimento.** Y la razón por la
que esta entrada no publicó un parcial mientras la corrida iba —el sesgo del orden alfabético— es la
que hizo mirar la tabla por departamento en vez de quedarse con el total.

Arreglado con dos defensas y no una, porque la de tiempo sola vuelve a depender de adivinar la
vigencia: el token se renueva por reloj cada media hora, y además cualquier `401` fuerza una
reautenticación y un reintento. Y el reanudado dejó de dar por medido un fallo — sin eso, los 377 se
habrían quedado perdidos y la corrida siguiente habría repetido el mismo porcentaje sobre medio
país.

## 7. Por dónde se puede empezar sin resolver nada de esto

Esta sección se escribió cuando no había nada construido. **Los tres tramos que
listaba —el paquete por variante, el cliente OAuth, la conciliación por guía— están
hechos**, y con ellos la cotización, el checkout, la contraentrega, el seguimiento y
el webhook firmado. Se conserva porque su criterio sigue sirviendo y porque el orden
en que se hicieron las cosas explica por qué el código se ve como se ve.

**Lo único que quedaba de la Fase 7 era la emisión de la guía**, bloqueada por el
saldo en COP 388. El 16 de septiembre de 2026 Skydropx recargó el sandbox y se midió
todo lo que faltaba: ver `§6.10`. Lo que quedó sin ejercer es de ellos —el conector de
recolección de Servientrega, medido otras dos veces el 17 de septiembre en horario hábil y caído
igual: `§6.11`— y el barrio del checkout, que era lo último
pendiente de producto, **está construido** (`§6.12`).
