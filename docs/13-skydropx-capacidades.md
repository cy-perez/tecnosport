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
2. **Un bulto o varios.** Peso sumado y dimensiones máximas, contra un `parcel`
   por línea. Cambia el flete y cambia la invariante del paquete.
3. **Asegurar los envíos** (`protect`), y con qué criterio. Un celular no es una
   camiseta.
4. **Validar la dirección** con `verify_by_carriers` antes de cobrar, o no.
5. **Entrega en oficina** como tercera forma de entrega, o no en esta fase.
6. **Dónde cae el recaudo**: créditos sin comisión o banco con comisión los
   jueves. Es una decisión contable, no técnica.
7. **Cancelar la guía** cuando se cancela un pedido ya despachado.
8. **v1 o v2** en cotizaciones y envíos.

## 6. Lo que solo se cierra con la cuenta real

Nada de esto se implementa de memoria.

- **La URL base de la cuenta colombiana.** ⚠️ Un SDK de terceros dice
  `https://app.skydropx.com` para pruebas y producción, y el panel colombiano vive
  bajo `/co`. No es suficiente para escribirlo en una variable de entorno.
- **La firma del webhook.** ⚠️ La única pista concreta es de terceros: cabecera
  `authorization` con el formato `HMAC {firma}` y **HMAC SHA-512** sobre el
  cuerpo. Coincide con el algoritmo que `ADR-0022` ya anotaba, lo que sube algo la
  confianza, pero el nombre de la cabecera sigue siendo de una sola fuente no
  oficial.
- **El cuerpo de `POST /pickups`.** ⚠️ Dos formas incompatibles.
- **Los campos de contraentrega al crear el envío.** ❌ Sin ellos no hay recaudo.
- **Los límites, la comisión y el seguro del recaudo.** ❌
- **Si hay ambiente de pruebas separado** y cómo se pide. ❌

`[[ CONFIRMAR CON LA CUENTA: los seis puntos de esta sección, en una sola sesión
con las credenciales reales. Es una tarea de una tarde y desbloquea la mitad de la
Fase 7. ]]`

## 7. Por dónde se puede empezar sin resolver nada de esto

Tres tramos no dependen de ninguna respuesta pendiente:

1. **El paquete por variante** (paso 1 de la Fase 7). Peso y dimensiones son
   nuestros; ninguna incógnita de la API los toca.
2. **El cliente OAuth con el token en caché** y el respeto de las 2 peticiones por
   segundo. La autenticación es lo único ✅ de punta a punta.
3. **La conciliación por guía**, que sostiene el seguimiento entero aunque el
   webhook tarde.
