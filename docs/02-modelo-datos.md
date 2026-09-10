# Modelo de datos

## Dinero, impuestos y redondeo

- Moneda única: COP. Objeto de valor `Dinero` con `BigDecimal` de escala 0. El
  peso colombiano no se fracciona en la práctica comercial.
- **El precio almacenado y mostrado incluye IVA.** Cada producto guarda su
  `tasa_iva` (`0.19` para casi todo el catálogo, `0.00` para lo excluido). El
  desglose se calcula hacia atrás al facturar.
- Todo cálculo intermedio en `BigDecimal`, con un solo redondeo al final,
  `HALF_UP`.
- Prohibido `double`, `float` y `Double` en cualquier parte del recorrido.
- **El total del pedido es subtotal de líneas más costo de envío**, y las dos
  cifras se guardan y se muestran por separado (`adr/0021`). El costo de envío es
  un `Dinero` como cualquier otro: escala 0, sin fracciones de peso.
- **El costo de envío no se reparte entre las líneas.** Repartirlo obligaría a
  redondear N veces y a decidir qué pasa con el sobrante; es un cargo del pedido,
  no de la mercancía.
- `TODO: ¿el costo de envío cobrado al comprador lleva IVA? Consultar con el
  contador.` Mientras no se resuelva, el flete se guarda como un valor sin
  desglose y no altera el IVA de las líneas.

## Producto, variante y unidad

Tres niveles, porque las tres líneas del catálogo se comportan distinto:

- **Producto** — lo que el cliente reconoce: "Camiseta running Dry-Fit",
  "iPhone 15". Nombre, descripción, marca, categoría, imágenes.
- **Variante** — lo que se compra y lo que tiene existencia y SKU propio:
  "Camiseta running Dry-Fit, azul, talla M". Es la unidad de inventario. El
  SKU es único en todo el catálogo, no solo dentro de su producto — se
  aprovechó la implementación de Fase 1 para dejarlo como `UNIQUE` de base
  de datos, porque el dominio, al construir un `Producto`, no tiene forma de
  ver el resto del catálogo y por eso solo puede evitar el duplicado dentro
  de sí mismo.
- **Unidad serializada** — solo celulares. Un equipo físico con IMEI.

Un producto tiene N variantes. Una variante tiene existencia numérica; si es de
la categoría celulares, además tiene N unidades con IMEI, y la existencia es el
conteo de unidades disponibles, no un número que se edita a mano.

## Variantes por categoría, mercado colombiano

**Ropa deportiva**
- Talla alfanumérica: XS, S, M, L, XL, XXL. Guardar también la equivalencia
  numérica cuando el proveedor la use (6, 8, 10, 12, 14, 16).
- Color: nombre comercial más HEX para el selector visual.
- Género: hombre, mujer, unisex, niño, niña.
- Opcionales por producto: material, tipo de manga, tipo de calce.

**Calzado deportivo** — comparte producto con ropa, pero su eje de talla es otro
- Talla COL/EUR de 34 a 45, con medias tallas (38.5, 39.5). Guardar también la
  equivalencia US: muchos proveedores la usan y el cliente pregunta por ella.
- Color.
- Género.
- Ancho: normal, ancho. Solo si el proveedor lo diferencia.

**Bolsos y maletines**
- Color.
- Capacidad en litros (18L, 25L, 35L) o talla S, M, L.
- Material: poliéster, nylon, lona, cuero sintético.
- Compartimento para portátil en pulgadas (13, 14, 15.6) o ninguno.

**Celulares**
- Almacenamiento: 64, 128, 256, 512 GB, 1 TB.
- Memoria RAM: 4, 6, 8, 12, 16 GB.
- Color.
- Condición: nuevo o reacondicionado. Si es reacondicionado, grado (A, B, C) y
  meses de garantía, que son distintos a los de un equipo nuevo.
- Red: 4G, 5G. SIM: dual física, física más eSIM.
- **IMEI por unidad.** En Colombia el IMEI importa: los equipos deben estar
  homologados ante la CRC y el IMEI se reporta. El sistema guarda el IMEI de cada
  unidad y lo asocia al pedido en el despacho.
- Garantía en meses, obligatoria y visible en la ficha.

Estos ejes se modelan como **atributos**, no como columnas fijas: una tabla
`atributo` con su tipo y sus valores permitidos, y la variante guarda pares
atributo-valor. Así se agrega "material" sin migrar el esquema. Lo que sí es
columna fija en la variante: `sku`, `precio`, `tasa_iva`, `existencia`,
`estado`, `codigo_barras`, y **el paquete: `peso_gramos`, `largo_cm`,
`ancho_cm`, `alto_cm`**.

**El paquete es obligatorio y es columna fija, no atributo** (`adr/0021`). Sin
peso ni dimensiones no hay cotización de envío, así que una variante sin esos
cuatro valores no se puede publicar — es una invariante del dominio, igual que el
SKU. Van como columnas y no como pares atributo-valor porque no describen el
producto para el comprador: los consume el cotizador, y un dato que un adaptador
necesita leer siempre no puede vivir en una bolsa de atributos opcionales.
`adr/0012` los había eliminado; `adr/0021` los devuelve, y el catálogo ya sembrado
necesita relleno antes de encender la cotización.

`TODO: peso y dimensiones reales de las variantes ya sembradas.` No se inventan:
un peso inventado es un flete cobrado de menos, o un pedido que la transportadora
reliquida después.

**El catálogo de atributos es global, no tipado por categoría en el esquema**
(confirmado al construir Track B, Fase 4): no existe ninguna columna ni tabla
que asocie un atributo a una categoría. Qué atributo corresponde a qué
categoría es hoy una convención de negocio que solo conoce `SembradorCatalogo`
(los datos de siembra) — el backend no valida ni filtra atributos por
categoría al agregar una variante (`GET /api/v1/atributos`,
`docs/03-api.md`). Nada impide hoy asignarle "almacenamiento" a una camiseta
por error del panel. Queda pendiente si el negocio lo pide.

## Imágenes y set de rotación

Un producto tiene una **imagen principal** obligatoria, que es la que se ve
primero en la rejilla y en la ficha, y opcionalmente un **set de rotación** con N
fotogramas ordenados.

```
imagen_producto
  id, producto_id, variante_id (nullable), tipo, orden,
  url, url_webp, ancho, alto, bytes, hash, alt_es, alt_en, creada_en

tipo: PRINCIPAL | GALERIA | ROTACION
```

Reglas:

- **El set de rotación pertenece a la variante cuando el color cambia el aspecto**
  (ropa, bolsos, celulares). Si la variante no tiene set propio, se usa el del
  producto. Esto evita fotografiar catorce colores el primer día sin cerrar la
  puerta a hacerlo después.
- **El set es completo o no existe.** Un set con fotogramas faltantes no se
  publica: el visor no se muestra y la ficha cae a la galería normal.
- `orden` va de 0 a N-1 y define la secuencia de giro. El fotograma 0 es la vista
  frontal y es el que se muestra antes de que el usuario interactúe.
- `hash` del contenido para detectar recargas duplicadas del mismo archivo: el
  SHA-256 en hexadecimal, 64 caracteres en minúscula, con un `check` en el
  esquema que lo exige (`V19`). Lo calcula el navegador, que es el único que
  tiene los bytes —en una subida directa el archivo nunca pasa por el backend—,
  así que el servidor no puede confirmar que corresponda al archivo sin
  descargarlo: `ADR-0019`. Lo que **no** va aquí es la key del objeto en Cloud
  Storage; guardarla ahí, como se hizo hasta la Fase 5, ni cabía en la columna ni
  permitía detectar duplicado alguno, porque cada key es única por construcción.
- `alt_es` y `alt_en` obligatorios en la principal, opcionales en los fotogramas
  de rotación, que son decorativos y llevan `alt=""` con la descripción en el
  contenedor.
- **Imagen principal con URL firmada (Fase 4):** el navegador sube el archivo
  directo a Cloud Storage con un `PUT`, el backend nunca ve los bytes; solo
  verifica que el objeto exista y su tamaño antes de confirmar. `url_webp`
  todavía apunta al mismo objeto que `url` — la conversión real de formato a
  WebP es del asistente de captura de la Fase 5, no existe todavía. `ancho` y
  `alto` los declara el cliente y se confían tal cual (metadato
  presentacional, no una medida verificada contra el archivo real). No se
  borra el objeto anterior al reemplazar la principal — el bucket tiene
  versionado (`docs/07-infra-gcp.md`). El tipo de contenido se acepta por una
  lista blanca declarada por el cliente, no verificado contra los bytes
  reales, y no hay tamaño máximo de subida propio — riesgo aceptado mientras
  el panel solo lo use el administrador del negocio (`ADR-0016`).

```
set_rotacion
  id, producto_id, variante_id (nullable), fotogramas, estado,
  capturado_por, capturado_en, dispositivo, version_asistente

estado: BORRADOR | COMPLETO | PUBLICADO
fotogramas: 4 mínimo, 8 recomendado, 16 máximo
```

`fotogramas` es **cuántos se prometieron al abrir el set**, no cuántas filas de
imagen tiene ya: es lo que permite distinguir un set de 4 de uno de 8 al que se le
perdieron cuatro subidas. Se fija al abrir y no cambia (migración `V18`).

Se guarda `dispositivo` y `version_asistente` porque cuando un set se ve mal, lo
primero que hay que saber es con qué se capturó.

**Un producto tiene a lo sumo un set publicado**, y es el único que la ficha
carga: mientras se captura uno nuevo, el que ya está publicado se sigue viendo.
Reemplazarlo son dos pasos —borrar el viejo, publicar el nuevo— y en el medio el
producto se queda sin visor. El set en `BORRADOR` y el `COMPLETO` solo los ve el
panel. Ver `ADR-0018`.

## Entidades principales

| Agregado | Contenido | Nota |
|---|---|---|
| `Producto` | variantes, imágenes, set de rotación, marca, categoría, estado | Raíz del catálogo |
| `Variante` | sku, atributos, precio, existencia | Dentro de `Producto` |
| `UnidadSerializada` | imei, estado, variante | Solo celulares |
| `Inventario` | movimientos y reservas | El saldo no se edita: se agrega movimiento |
| `Carrito` | líneas, identificador anónimo o de usuario | Vive 30 días |
| `Pedido` | líneas congeladas, dirección, tipo de entrega, tarifa de envío congelada, totales, método de pago, estado, historial | Raíz transaccional |
| `Pago` | referencia, método, estado, eventos recibidos | Idempotente por referencia |
| `Envio` | transportadora, servicio, guía, costo real, comisión y fecha de conciliación del recaudo, eventos de seguimiento | Nace en el despacho; sin `estado` propio, lo lleva `Pedido.estado` (`ADR-0013`, `ADR-0022`) |
| `EventoSeguimiento` | estado de la transportadora, descripción, momento del evento y de su recepción | Dentro de `Envio`. Se agrega, nunca se sobrescribe |
| `Usuario` | correo, credencial, roles, verificación | |
| `SesionRefresco` | familia, rotación, revocación | Un eslabón de la rotación por fila (Fase 4) |
| `TokenVerificacionCorreo` | token, vencimiento, un solo uso | Separado de `TokenRecuperacionClave` por sensibilidad (`ADR-0015`) |
| `TokenRecuperacionClave` | token, vencimiento, un solo uso | Consumirlo revoca todas las sesiones del usuario (`ADR-0015`) |
| `Direccion` | departamento, ciudad, dirección, indicaciones | Códigos DANE |
| `Categoria`, `Marca`, `Atributo` | catálogo maestro | |
| `SetRotacion`, `ImagenProducto` | material visual | |

## Reglas de inventario

El saldo de existencias no es una columna que se actualiza. Es la suma de
`MovimientoInventario` (`ENTRADA`, `SALIDA`, `AJUSTE`, `RESERVA`, `LIBERACION`).
Se guarda un saldo materializado por rendimiento, pero se recalcula y se concilia.

Ciclo con pago en línea: el checkout reserva al crear el intento de pago; la
reserva vence a los 30 minutos; el pago aprobado convierte reserva en salida; el
pago rechazado o vencido la libera. `linea_pedido.id_reserva` guarda qué
movimiento `RESERVA` respalda cada línea — sin ese id no hay forma segura de
saber cuál reserva liberar o confirmar, porque dos pedidos distintos pueden
tener reservas pendientes de la misma variante al mismo tiempo. Reintentar un
pago fallido no reutiliza la reserva liberada: crea una nueva, revalidada
contra existencia real (`ADR-0014`), y actualiza ese id.

Ciclo con contraentrega: la reserva se crea al confirmar el pedido y **no vence
por tiempo**: se mantiene hasta el despacho, porque no hay pago que esperar. Si el
pedido se cancela o el cliente rechaza en la entrega, se libera con un movimiento
de `LIBERACION` que registra el motivo.

La reserva se toma con bloqueo pesimista sobre la variante para que dos
compradores simultáneos no vendan la misma última unidad.

**`variante.existencia` y `Inventario` conviven, todavía no están unificados**
(confirmado al construir "agregar variante", Fase 4): la ficha pública y la
rejilla siguen leyendo la columna directo, el checkout sigue calculando el
saldo desde `Inventario`. Al crear una variante se escriben las dos a la vez
en la misma transacción, pero ningún mecanismo detecta si algo las
desincroniza más adelante. Migrar la lectura pública a
`Inventario.saldoDisponible` es el objetivo de fondo, pendiente (`ADR-0017`).

## Estados del pedido

```
CREADO
  -> PAGO_PENDIENTE -> PAGADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO
  |        |                                          |
  |        +-> PAGO_FALLIDO -> (reintento)            +-> DEVUELTO
  |
  +-> CONFIRMADO_CONTRAENTREGA -> EN_PREPARACION -> DESPACHADO
                                                      |
                                       +--------------+--------------+
                                       |                             |
                                  ENTREGADO                 RECHAZADO_EN_ENTREGA
                                       |                             |
                              RECAUDO_PENDIENTE              (libera inventario)
                                       |
                                 RECAUDO_CONCILIADO
                                       |
                                   DEVUELTO
```

Cada transición se registra en `HistorialPedido` con fecha, actor y motivo. Las
transiciones válidas se declaran en el dominio: un pedido `ENTREGADO` no vuelve a
`PAGADO`, y lo impide el código, no el buen juicio de quien opera.

`RECAUDO_PENDIENTE` y `RECAUDO_CONCILIADO` existen porque en contraentrega el
dinero lo cobra la transportadora y llega días después. Un pedido entregado con
recaudo sin conciliar es plata en la calle y tiene que ser visible.

**`DEVUELTO` se alcanza por los dos caminos**, desde `ENTREGADO` en pago en línea
y desde `RECAUDO_CONCILIADO` en contraentrega. El retracto del artículo 47 de la
Ley 1480 de 2011 no distingue el método de pago, y hasta septiembre de 2026 el
grafo sí lo hacía: `RECAUDO_CONCILIADO` era terminal, así que una compra
contraentrega entregada y cobrada no tenía ningún camino de vuelta. No se
devuelve desde `RECAUDO_PENDIENTE`: mientras el dinero no haya llegado no hay
nada que reintegrar, y el paso es conciliar primero.

**El grafo no cambia con Skydropx, cambia quién dispara las transiciones.**
`DESPACHADO`, `ENTREGADO` y `RECHAZADO_EN_ENTREGA` ahora pueden llegar por el
webhook del proveedor (`ADR-0022`) y no solo por una acción del panel. Los doce
estados de la transportadora **no** entran al enum: viven en
`evento_seguimiento`. Meterlos aquí sería atar la máquina de estados del pedido
al vocabulario de un proveedor, que es la parte del dominio que más caro sale
mover.

La tabla `cobertura_contraentrega` **se retira** (`ADR-0023`): la cobertura sale
de la cotización, no de una lista propia cargada a mano.

## Envío: cotización congelada y seguimiento

El costo de envío se cotiza contra Skydropx antes de pagar y **se congela en el
pedido**, no en `Envio` — porque `Envio` nace en el despacho (`ADR-0013`) y el
comprador necesita el costo mucho antes de eso.

```
pedido
  ... , tipo_entrega, costo_envio, transportadora_cotizada,
  servicio_cotizado, tarifa_id_proveedor, dias_estimados,
  tarifa_vence_en

tipo_entrega: ENVIO_A_DOMICILIO | RETIRO_EN_PUNTO
```

`TipoEntrega` ya existe con esos dos nombres y **no se renombra**: el enum es del
dominio y el texto de la interfaz es de Transloco. Que la vitrina diga "recogida
en el punto" y el dominio diga `RETIRO_EN_PUNTO` no es una incoherencia; renombrar
un enum persistido para alinearlo con una palabra de la interfaz sí sería una
migración sin beneficio.

Reglas:

- **`RETIRO_EN_PUNTO` implica `costo_envio` en cero** y las columnas de tarifa en
  nulo: no se cotizó nada porque no hay nada que enviar. Es una invariante del
  agregado, no una convención de la interfaz.
- **`ENVIO_A_DOMICILIO` exige tarifa.** Un pedido a domicilio sin tarifa congelada no se
  puede crear: significaría que el flete se decidió después de que el comprador
  aceptó el total.
- **`tarifa_id_proveedor` es opaco.** Es el `rate_id` de Skydropx y solo sirve
  para emitir la guía; no se muestra ni se acepta desde el cliente.
- **`tarifa_vence_en` no bloquea el pago.** Guarda cuándo caducan las 24 horas de
  validez de la tarifa, para que al despachar se sepa si hay que cotizar de nuevo.
  Un pedido no cambia de total porque la tarifa venció (`docs/11-pagos-y-envios.md`).

Del despacho en adelante el rastro vive en `Envio`:

```
envio
  id, pedido_id, transportadora, servicio, guia, url_rastreo,
  costo_envio_real, comision_recaudo, recaudo_conciliado_en, creado_en

evento_seguimiento
  id, envio_id, estado_proveedor, descripcion,
  ocurrido_en, recibido_en, id_evento_proveedor
```

- **`id_evento_proveedor` es `UNIQUE`.** Es lo que hace idempotente el webhook: el
  mismo evento reintentado no escribe dos filas ni vuelve a transicionar el
  pedido. Si el proveedor no manda identificador propio, se usa el hash de la
  firma, igual que en el webhook de Wompi.
- **`ocurrido_en` y `recibido_en` son fechas distintas y las dos importan.** Un
  `delivered` que ocurrió el lunes y llegó el jueves corre plazos legales desde el
  lunes; la segunda fecha es la que explica por qué nadie se enteró.
- **`estado_proveedor` se guarda tal cual llega**, sin traducir a un enum propio.
  Solo tres de los doce estados mueven el pedido (`ADR-0022`); mapear los otros
  nueve a un vocabulario nuestro sería inventar estados que el dominio no usa.
- **`costo_envio_real` es interno.** Es lo que la transportadora cobra, y frente al
  `costo_envio` cobrado al comprador es el margen del pedido. Ningún endpoint
  público lo devuelve.

## Congelado del pedido

Al crear el pedido se copian nombre, SKU, precio unitario, tasa de IVA e imagen a
la línea. Si mañana sube el precio, el pedido histórico no cambia. Un pedido nunca
lee el catálogo actual para reconstruir su total.

**El costo de envío se congela igual que el precio**, y por el mismo motivo: es
parte de lo que el comprador aceptó. Que mañana la transportadora suba la tarifa
no cambia lo que ese pedido debe.

## Convenciones de base de datos

- Nombres en español, `snake_case`, tablas en singular: `producto`,
  `linea_pedido`, `movimiento_inventario`, `set_rotacion`.
- Clave primaria `id uuid`, versión 7, generada en la aplicación.
- `creado_en` y `actualizado_en` en `timestamptz`, siempre UTC.
- Borrado lógico solo donde el negocio lo pida (`producto.estado`), no como norma.
- Índices explícitos y justificados en la migración, con un comentario del porqué.
- Búsqueda de texto con `pg_trgm` o `tsvector`, nunca `LIKE '%...%'`. El
  catálogo (Fase 1) usa `pg_trgm`: `similarity()` sobre un índice GIN de
  `lower(nombre)`, menos piezas que mantener que un `tsvector` generado. Un
  buscador con más volumen o que necesite pesar campos puede justificar
  cambiar a `tsvector` más adelante; no hace falta las dos a la vez.
