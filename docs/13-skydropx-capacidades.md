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
| Campos exactos de la dirección | ❌ | No se obtuvo la estructura para Colombia |

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
| Consultar días disponibles | ✅ | `GET /pickups/coverage` antes de programar |
| Programar una recolección | ✅ | Agrupa **varios envíos** en una sola recogida |
| Reprogramar | ✅ | `POST /pickups/reschedule` |
| Consultar el estado | ✅ | `GET /pickups/{id}` |
| Cuerpo exacto de la petición | ⚠️ | **Dos fuentes se contradicen** (ver abajo) |
| Si es obligatoria | ⚠️ | Una fuente dice que no, que la alternativa es dejar el paquete en oficina |
| Qué transportadoras la soportan | ❌ | Se valida con el endpoint de cobertura, caso por caso |

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
| Crear la guía desde una tarifa | ✅ | `quotation_id` + `rate_id` |
| Crear sin cotizar antes | ✅ | `rate-shipments`, útil para reexpedir |
| Cancelar una guía | ✅ | `cancellations` |
| Asegurar el envío | ✅ | `protect`, por envío |
| Tipo de empaque | ✅ | Catálogo `packagings` |
| Campos de contraentrega | ❌ | **La documentación no los muestra** |
| Qué devuelve (rótulo, guía) | ❌ | No se obtuvo el esquema de respuesta |

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
| Consultar por guía | ✅ | `GET /shipments/tracking/{guia}/{transportadora}` |
| Webhook de eventos | ⚠️ | Existe; **la sección de la documentación no se pudo leer** |
| Cabecera y algoritmo de firma | ⚠️ | Solo de un SDK de terceros (§6) |
| Lista y nombre de los eventos | ❌ | Los doce de `ADR-0022` no se pudieron re-confirmar |

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
- **`finance/extra-charges`**: los cobros extra que la transportadora aplica
  después. Es el mecanismo por el cual **un peso mal declarado se reliquida**, que
  es justo el riesgo que `docs/02-modelo-datos.md` menciona al prohibir inventar
  pesos. Con este endpoint el sobrecosto deja de ser invisible.
- **`finance/credits`**: el saldo. Si la cuenta se queda sin crédito no hay guías,
  y hoy nadie se enteraría hasta que un despacho falle.

## 3. La contraentrega, como la plantea Skydropx

Lo que cambia respecto de lo que suponía `ADR-0023`:

- ⚠️ **Hay que solicitar el servicio; no viene activo.** Existe un trámite
  ("Solicitar el servicio de pago contra entrega") que es requisito antes de que
  cualquier tarifa devuelva recaudo. Es un prerrequisito del negocio, anterior a
  todo el código.
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
| `0021` cotización | Todo: asíncrona, 24 h, tarifa elegida por el servidor, `fail-closed` | Falta decidir multipaquete; los campos de dirección siguen sin confirmar |
| `0022` seguimiento | La conciliación programada, que se sostiene sola con el tracking por guía | **Le falta el tramo de recolección entero**; los doce estados y la firma no se pudieron re-confirmar |
| `0023` contraentrega | La cobertura por tarifa y el recaudo del total | El servicio hay que **solicitarlo**; el retiro es semanal y con comisión; los límites siguen sin confirmar |

Ninguno se contradice de frente. `0022` es el que queda corto, y no por estar
equivocado sino por no haber mirado que entre emitir una guía y que el paquete se
mueva hay un paso con nombre propio.

## 5. Decisiones abiertas, para tomar antes de codificar

1. **Recolección programada o entrega en oficina.** Determina si la Fase 7 gana
   un agregado `Recoleccion`, una pantalla de panel y una cuarta tarea
   programada, o si el despacho termina en "alguien lleva los paquetes".
2. ~~**Un bulto o varios.**~~ **Decidido el 11 de septiembre de 2026: un `parcel`
   por variante.** Es lo que el modelo ya sabe —cada variante tiene su `Paquete`
   con peso y medidas reales— y evita inventar las dimensiones de una caja
   combinada. Pesa además que el peso sumado se saldría del tope de 8 kg de
   Envía y dejaría transportadoras fuera. Con él se decidió el valor declarado:
   **el total de lo que va en cada bulto**, no el mínimo ni el 2.500 por omisión,
   porque la transportadora responde hasta lo declarado.
3. **Asegurar los envíos** (`protect`), y con qué criterio. Un celular no es una
   camiseta.
4. **Validar la dirección** con `verify_by_carriers` antes de cobrar, o no.
5. **Entrega en oficina** como tercera forma de entrega, o no en esta fase.
6. **Dónde cae el recaudo**: créditos sin comisión o banco con comisión los
   jueves. Es una decisión contable, no técnica.
7. **Cancelar la guía** cuando se cancela un pedido ya despachado.
8. **v1 o v2** en cotizaciones y envíos.

## 6. Lo que se cerró con la cuenta real

### Estado al 14 de septiembre de 2026: qué está en manos de Skydropx

Léase esto antes de volver a probar nada contra el sandbox. Lo que sigue **ya
se investigó hasta el fondo que permite la cuenta** y el detalle está en §6.1;
repetir las pruebas no va a cambiar el resultado, porque el fallo está del lado
de Skydropx y **la solicitud ya se les envió el 14 de septiembre de 2026**.

| Tema | Estado | Qué falta y de quién depende |
|---|---|---|
| Firma del webhook | ✅ Resuelto con la documentación oficial | Implementar HMAC‑SHA512 sobre los bytes crudos, cabecera `Authorization: HMAC <firma>`; comprobar contra un evento real cuando haya guía. **Nuestro.** |
| DHL en el panel | ✅ Resuelto: solo internacional | Nada. No aplica al negocio. |
| Servientrega, Envía, Coordinadora sin tarifa | ⛔ Bloqueado por Skydropx | La API de cada transportadora rechaza lo que Skydropx le manda (valor declarado ausente o petición inválida). Solicitud enviada con identificadores de cotización. **De ellos.** |
| Inter Rapidísimo `to_f >= 25` | ⏳ Verificación de origen pendiente | Plantilla `535bd77b-fce2-46f0-9354-56b9c42fba5f` en `pending_to_send`. **Una sola acción nuestra:** volver a cotizar con `address_from.address_template_id` desde el 16 de septiembre. Si sigue igual, es de ellos y ya está en la solicitud. |
| Créditos del sandbox | ⛔ Bloqueado por Skydropx | No hay API de recarga. La recarga del panel corre contra el sandbox de Mercado Pago: un intento falló al crear el pago y otro fue aprobado por Mercado Pago y **no se acreditó**. Solicitud enviada. **De ellos.** |
| Guía, webhook real, recolección, recaudo | ⏸ Esperan a los créditos | Cuando lleguen: emitir una guía con `auto_advance: true` y el ciclo entero se dispara solo. |

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
- ✅ **`declared_value` va en cada `parcel` y por omisión queda en COP 2.500.**
  `declared_amount` es obligatorio a nivel de cotización, aparte.
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
  cuerpo va envuelto en `shipment` y lleva `quotation_id`, `rate_id`,
  `address_from`, `address_to` y `parcels`. Lo dijo el propio 422 al mandarle
  solo los dos identificadores, y trae **dos exigencias que no estaban en
  ninguna parte**:

  - Las direcciones piden además **`email` y `reference`**, las dos obligatorias
    y en los dos extremos. El correo del comprador ya lo tenemos; `reference`
    —una referencia para encontrar el sitio— no se pide hoy en el checkout, y el
    origen tampoco tiene correo configurado.
  - Cada bulto pide **`package_type` y `package_content`**: qué tipo de empaque
    es y qué va dentro, en texto.

  **Lo que se decidió el 14 de septiembre de 2026** para los tres datos que esto
  dejó abiertos (el detalle y el porqué, en `docs/09-plan-de-arranque.md`, paso 7):

  | Campo | Qué se manda |
  |---|---|
  | `address_to.reference` | El `indicaciones` del pedido, que sigue siendo opcional; si viene vacío, `Sin indicaciones adicionales` |
  | `address_from.email` | `contacto@tecnosport.co`, ya implementado como `ORIGEN_CORREO` |
  | `package_content` | Genérico por línea: "Ropa y calzado deportivo", "Bolsos y morrales", "Equipo de telefonía móvil" — coincide con el contenido real para sostener una reclamación, sin anunciar en la etiqueta que dentro va un celular |

  `package_type` sigue sin decidirse porque **no es un dato de negocio sino un
  valor del catálogo de Skydropx**, y su lista de valores válidos no se ha podido
  leer sin emitir una guía.

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

## 7. Por dónde se puede empezar sin resolver nada de esto

Tres tramos no dependen de ninguna respuesta pendiente:

1. **El paquete por variante** (paso 1 de la Fase 7). Peso y dimensiones son
   nuestros; ninguna incógnita de la API los toca.
2. **El cliente OAuth con el token en caché** y el respeto de las 2 peticiones por
   segundo. La autenticación es lo único ✅ de punta a punta.
3. **La conciliación por guía**, que sostiene el seguimiento entero aunque el
   webhook tarde.
