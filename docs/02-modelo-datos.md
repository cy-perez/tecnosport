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
`estado`, `codigo_barras`.

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
| `Pedido` | líneas congeladas, dirección, envío, totales, método de pago, estado, historial | Raíz transaccional |
| `Pago` | referencia, método, estado, eventos recibidos | Idempotente por referencia |
| `Envio` | transportadora, guía, costo real, comisión y fecha de conciliación del recaudo | Nace en el despacho; sin `estado` propio, lo lleva `Pedido.estado` (`ADR-0013`) |
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
```

Cada transición se registra en `HistorialPedido` con fecha, actor y motivo. Las
transiciones válidas se declaran en el dominio: un pedido `ENTREGADO` no vuelve a
`PAGADO`, y lo impide el código, no el buen juicio de quien opera.

`RECAUDO_PENDIENTE` y `RECAUDO_CONCILIADO` existen porque en contraentrega el
dinero lo cobra la transportadora y llega días después. Un pedido entregado con
recaudo sin conciliar es plata en la calle y tiene que ser visible.

## Congelado del pedido

Al crear el pedido se copian nombre, SKU, precio unitario, tasa de IVA e imagen a
la línea. Si mañana sube el precio, el pedido histórico no cambia. Un pedido nunca
lee el catálogo actual para reconstruir su total.

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
