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
  "Camiseta running Dry-Fit, azul, talla M". Es la unidad de inventario.
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

Estos ejes se modelan como **atributos tipados por categoría**, no como columnas
fijas: una tabla `atributo` con su tipo y sus valores permitidos, y la variante
guarda pares atributo-valor. Así se agrega "material" sin migrar el esquema. Lo
que sí es columna fija en la variante: `sku`, `precio`, `tasa_iva`, `existencia`,
`estado`, `codigo_barras`.

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
- `hash` del contenido para detectar recargas duplicadas del mismo archivo.
- `alt_es` y `alt_en` obligatorios en la principal, opcionales en los fotogramas
  de rotación, que son decorativos y llevan `alt=""` con la descripción en el
  contenedor.

```
set_rotacion
  id, producto_id, variante_id (nullable), fotogramas, estado,
  capturado_por, capturado_en, dispositivo, version_asistente

estado: BORRADOR | COMPLETO | PUBLICADO
fotogramas: 4 mínimo, 8 recomendado, 16 máximo
```

Se guarda `dispositivo` y `version_asistente` porque cuando un set se ve mal, lo
primero que hay que saber es con qué se capturó.

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
| `Envio` | transportadora, guía, estado, costo cotizado y real, recaudo | |
| `Usuario` | correo, credencial, roles, verificación | |
| `Direccion` | departamento, ciudad, dirección, indicaciones | Códigos DANE |
| `Categoria`, `Marca`, `Atributo` | catálogo maestro | |
| `SetRotacion`, `ImagenProducto` | material visual | |

## Reglas de inventario

El saldo de existencias no es una columna que se actualiza. Es la suma de
`MovimientoInventario` (`ENTRADA`, `SALIDA`, `AJUSTE`, `RESERVA`, `LIBERACION`).
Se guarda un saldo materializado por rendimiento, pero se recalcula y se concilia.

Ciclo con pago en línea: el checkout reserva al crear el intento de pago; la
reserva vence a los 30 minutos; el pago aprobado convierte reserva en salida; el
pago rechazado o vencido la libera.

Ciclo con contraentrega: la reserva se crea al confirmar el pedido y **no vence
por tiempo**: se mantiene hasta el despacho, porque no hay pago que esperar. Si el
pedido se cancela o el cliente rechaza en la entrega, se libera con un movimiento
de `LIBERACION` que registra el motivo.

La reserva se toma con bloqueo pesimista sobre la variante para que dos
compradores simultáneos no vendan la misma última unidad.

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
- Búsqueda de texto con `pg_trgm` o `tsvector`, nunca `LIKE '%...%'`.
