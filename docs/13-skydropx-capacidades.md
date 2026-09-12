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
  otra. Servientrega y Envía lo devuelven de forma constante, lo que apunta a
  transportadoras o planes sin activar en la cuenta.
  `[[ CONFIRMAR EN EL PANEL: qué transportadoras y qué planes hay activos en el
  sandbox, porque sin una tarifa estable no se puede verificar el camino feliz de
  punta a punta. ]]`

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

- ⛔ **No se pudo emitir ninguna guía: la cuenta no tiene créditos.** El intento
  con el cuerpo completo respondió
  `422 No tienes los créditos suficientes para este envío. Agrega créditos y
  continúa.` No se creó nada ni se consumió saldo. Junto a ese mensaje aparece
  `Valor declarado es obligatorio`, que **no** cede con ninguna de siete grafías
  —`declared_value` y `declared_amount`, en el envío y en el bulto, más
  `insurance` y `protect`—; tiene la forma de un error de la transportadora, así
  que lo más probable es que sea ruido de una validación previa que no llega a
  ejecutarse sin saldo.

  `[[ CONFIRMAR CON LA CUENTA: cargar créditos de prueba en el sandbox. Sin eso
  no se puede emitir una guía, y sin una guía emitida no hay webhook que firmar
  ni evento que mapear — es decir, el resto del paso 7 de la Fase 7 está topado
  por la cuenta, no por el código. ]]`

- ❌ **La firma del webhook** y **el cuerpo de `POST /pickups`**: siguen sin
  confirmarse, y no se pueden confirmar hasta que exista un envío real.
- ❌ **La comisión financiera del retiro a banco.** Es comercial, no técnica: va
  por el ejecutivo de cuenta.

## 7. Por dónde se puede empezar sin resolver nada de esto

Tres tramos no dependen de ninguna respuesta pendiente:

1. **El paquete por variante** (paso 1 de la Fase 7). Peso y dimensiones son
   nuestros; ninguna incógnita de la API los toca.
2. **El cliente OAuth con el token en caché** y el respeto de las 2 peticiones por
   segundo. La autenticación es lo único ✅ de punta a punta.
3. **La conciliación por guía**, que sostiene el seguimiento entero aunque el
   webhook tarde.
