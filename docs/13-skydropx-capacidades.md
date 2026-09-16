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
5. **Entrega en oficina** como tercera forma de entrega, o no en esta fase. La API
   quedó confirmada el 15 de septiembre (§6.2) y **ninguna transportadora del
   sandbox la ofrece hoy**, así que la decisión sigue abierta sin poder probarse.
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
| Servientrega, Envía, Coordinadora sin tarifa | ⛔ Bloqueado por Skydropx | La API de cada transportadora rechaza lo que Skydropx le manda (valor declarado ausente o petición inválida). Solicitud enviada con identificadores de cotización. **De ellos.** **Re-medido el 15 de septiembre: idéntico, mensaje por mensaje** (§6.2). |
| Inter Rapidísimo `to_f >= 25` | ⏳ Verificación de origen en curso | Plantilla `535bd77b-fce2-46f0-9354-56b9c42fba5f`, que el 15 de septiembre **pasó de `pending_to_send` a `process`**: la transportadora la está mirando. El `to_f` sigue igual. Volver a cotizar cuando el estado cambie otra vez. |
| Créditos del sandbox | ✅ **Resuelto el 15 de septiembre de 2026** | Skydropx depositó **49.000 COP a mano** (`transaction_source: Skydropx`, etiqueta `USO_INTERNO`, comentario "para realizar peruebas"). Saldo: **50.000 COP**. Nunca funcionó la vía de autoservicio; la resolvió el soporte. |
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
  | `package_content` | Genérico por línea de catálogo, **y el mapa vive en `ContenidoDeclarado` (`domain/envio`), no en esta tabla** — coincide con el contenido real para sostener una reclamación, sin anunciar en la etiqueta qué va dentro |

  `package_type` sigue sin decidirse porque **no es un dato de negocio sino un
  valor del catálogo de Skydropx**, y su lista de valores válidos no se ha podido
  leer sin emitir una guía.

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

**Las cuatro transportadoras que tienen oficinas son exactamente las cuatro que
no cotizan.** Mientras eso siga así, una tercera forma de entrega en el checkout
sería una pantalla a la que nadie puede llegar.

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
- **Inter Rapidísimo y su `to_f >= 25`**, con la plantilla de origen en `process`.

## 7. Por dónde se puede empezar sin resolver nada de esto

Tres tramos no dependen de ninguna respuesta pendiente:

1. **El paquete por variante** (paso 1 de la Fase 7). Peso y dimensiones son
   nuestros; ninguna incógnita de la API los toca.
2. **El cliente OAuth con el token en caché** y el respeto de las 2 peticiones por
   segundo. La autenticación es lo único ✅ de punta a punta.
3. **La conciliación por guía**, que sostiene el seguimiento entero aunque el
   webhook tarde.
