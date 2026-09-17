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
- **El flete cobrado al comprador probablemente sí lleva IVA, y el sistema no lo
  está calculando.** Verificado el 14 de septiembre de 2026, y el resultado sorprende
  porque son dos preguntas y no una:
  - El **servicio de transporte de carga**, comprado suelto a la transportadora, está
    **excluido** de IVA. Eso es cierto y es lo que se encuentra al buscar.
  - Pero el **flete que el vendedor le recobra al comprador dentro de una venta
    gravada** es otra cosa: el **artículo 447 del Estatuto Tributario** manda que la
    base gravable incluya los "acarreos" y demás erogaciones complementarias *"aunque
    se facturen o convengan por separado y aunque, considerados independientemente, no
    se encuentren sometidos a imposición"*. El **Concepto DIAN 4945 de 2025** lo
    confirma para el transporte que contrata el vendedor para entregar, incluso
    subcontratado a un tercero — que es exactamente este caso.
  - La distinción que sí exime es que **el comprador contrate el transporte por su
    cuenta** con un tercero ajeno a la venta. No es lo que hace este sitio.

  **La consecuencia es de plata, no de redacción.** Hoy se le cobra al comprador el
  `rate.total` de la cotización tal cual, que es el precio de un servicio excluido y por
  lo tanto no trae IVA dentro. Si ese valor integra la base gravable, de cada flete hay
  que declarar el 19% — y como no se le sumó al cobrar, sale del margen del negocio en
  **cada pedido a domicilio**. `TODO: confirmar con el contador y, si aplica, sumar el
  IVA al flete antes de cobrarlo.`

  Y hay un daño que no se puede reparar hacia atrás: `pedido.costo_envio` se guarda
  **sin desglose**, así que de los pedidos ya cobrados no se puede separar cuánto era
  base y cuánto impuesto para facturar. Si se confirma, el desglose hay que agregarlo.

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
`adr/0012` los había eliminado; `adr/0021` los devuelve. **Construido el 10 de
septiembre de 2026** (`V32`): objeto de valor `Paquete` en el dominio, cuatro
columnas `not null` con un `check` de positividad —que también está en el dominio,
pero el sembrador escribe entidades JPA directo y no pasa por él—, y el panel
pidiéndolos al crear una variante.

El catálogo sembrado quedó con **medidas de demostración, declaradas como tales**
en `SembradorCatalogo` y en la migración. No es inventar un dato de negocio: ese
catálogo es ficción completa —ni "Under Trail" ni el "Celular TecnoSport Aurora"
existen—, y es el mismo criterio de `hashDeSiembra` y de las fotos de picsum.

La migración rellena **por SKU explícito** y solo después pone las columnas en
`not null`: si aparece una fila que no reconoce, falla y detiene el despliegue. Un
relleno por defecto le habría puesto el mismo peso a una camiseta y a un par de
tenis.

`TODO: peso y dimensiones reales de las variantes del catálogo de producción,
medidos con el producto empacado.` No se heredan de las filas sembradas ni se
inventan: un peso inventado es un flete cobrado de menos, o un pedido que la
transportadora reliquida después —y eso último se puede vigilar con
`finance/extra-charges`, ver `docs/13-skydropx-capacidades.md`.

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
| `Pedido` | líneas congeladas, contacto de quien recibe, dirección, tipo de entrega, tarifa de envío congelada, totales, método de pago, estado, historial | Raíz transaccional |
| `Pago` | referencia, método elegido, medio reportado por la pasarela, estado, eventos recibidos | Idempotente por referencia. El método que el comprador eligió y el medio con que la pasarela cobró son dos hechos distintos y ninguno pisa al otro (`ADR-0029`) |
| `Envio` | sus guías, comisión y fecha de conciliación del recaudo | Nace en el despacho; sin `estado` propio, lo lleva `Pedido.estado` (`ADR-0013`, `ADR-0022`). El costo real es la suma de sus guías |
| `GuiaEnvio` | transportadora y su código en la plataforma, número de guía, costo real de ese paquete, eventos de seguimiento | Dentro de `Envio`, y **varias**: ninguna transportadora colombiana admite multipaquete (`ADR-0031`) |
| `EventoSeguimiento` | estado de la transportadora, descripción, momento del evento y de su recepción | Dentro de `GuiaEnvio`. Se agrega, nunca se sobrescribe |
| `Usuario` | correo, credencial, roles, verificación | |
| `SesionRefresco` | familia, rotación, revocación | Un eslabón de la rotación por fila (Fase 4) |
| `TokenVerificacionCorreo` | token, vencimiento, un solo uso | Separado de `TokenRecuperacionClave` por sensibilidad (`ADR-0015`) |
| `TokenRecuperacionClave` | token, vencimiento, un solo uso | Consumirlo revoca todas las sesiones del usuario (`ADR-0015`) |
| `Direccion` | departamento, ciudad, dirección, indicaciones | Códigos DANE |
| `Contacto` | nombre y teléfono de quien recibe | Va en la guía y es a quien llama el mensajero. Nulo solo en pedidos anteriores a `V36` |
| `Categoria`, `Marca`, `Atributo` | catálogo maestro | `Atributo.unidad` (opcional) acompaña al valor cuando el número solo no dice nada: "12 meses" |
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

**El plazo de entrega no es una columna.** Los treinta días calendario del
artículo 18 de la Ley 1480 de 2011 se cuentan desde el registro de `PAGADO` del
historial —o el de `CONFIRMADO_CONTRAENTREGA`, que es donde se celebra el
contrato cuando se paga al recibir—, igual que la fecha de entrega se lee del
registro de `ENTREGADO`. El historial no se sobrescribe y cada uno de esos
estados se alcanza una sola vez, así que una columna propia sería una segunda
verdad capaz de divergir sin que nada avise.

Lo que sí se guarda es `pedido.aviso_plazo_entrega_enviado_en`: cuándo se le
avisó al comprador de que el plazo venció. Ese no se deduce de ningún estado —es
el hecho de que salió un correo— y de él depende que el vigilante no vuelva a
escribir en cada vuelta. Vencer no cancela nada: quien decide terminar el
contrato es quien compró (`ADR-0028`).

## Envío: cotización congelada y seguimiento

El costo de envío se cotiza contra Skydropx antes de pagar y **se congela en el
pedido**, no en `Envio` — porque `Envio` nace en el despacho (`ADR-0013`) y el
comprador necesita el costo mucho antes de eso.

```
pedido
  ... , tipo_entrega, costo_envio, tarifa_envio_id,
  tarifa_envio_transportadora, tarifa_envio_servicio,
  tarifa_envio_dias, tarifa_envio_admite_contraentrega,
  tarifa_envio_vence_en

tipo_entrega: ENVIO_A_DOMICILIO | RETIRO_EN_PUNTO
```

Los nombres se decidieron al escribir `V33` (11 de septiembre de 2026): las seis
columnas de la tarifa comparten el prefijo `tarifa_envio_` para que se lean como
lo que son —un solo objeto de valor desarmado en columnas— y para que un `check`
pueda exigirlas juntas. `costo_envio` se queda fuera del prefijo porque no es
parte de la tarifa: es lo que se cobró, y existe también cuando no hubo tarifa.

`TipoEntrega` ya existe con esos dos nombres y **no se renombra**: el enum es del
dominio y el texto de la interfaz es de Transloco. Que la vitrina diga "recogida
en el punto" y el dominio diga `RETIRO_EN_PUNTO` no es una incoherencia; renombrar
un enum persistido para alinearlo con una palabra de la interfaz sí sería una
migración sin beneficio.

Reglas:

- **`RETIRO_EN_PUNTO` implica `costo_envio` en cero** y las columnas de tarifa en
  nulo: no se cotizó nada porque no hay nada que enviar. Es una invariante del
  agregado, no una convención de la interfaz.
- **`ENVIO_A_DOMICILIO` exige tarifa.** Un pedido a domicilio sin tarifa congelada
  no se puede crear: significaría que el flete se decidió después de que el
  comprador aceptó el total.

  **Dónde vive hoy esa exigencia**, porque no es donde se esperaría: en
  `CrearPedido`, que cotiza siempre antes de reservar y aborta si no hay tarifa, y
  no en el constructor de `Pedido`. El agregado la acepta nula a propósito, porque
  es el mismo constructor con el que el repositorio reconstruye los pedidos
  anteriores a la Fase 7, que no tienen ninguna y son válidos. Mientras
  `CrearPedido` sea el único que crea pedidos la regla se cumple; el día que
  aparezca otro camino —una importación, un pedido creado desde el panel— hay que
  subirla al agregado separando el constructor de reconstrucción del de creación.
- **`tarifa_envio_id` es opaco.** Es el `rate_id` de Skydropx y solo sirve para
  emitir la guía; no se muestra ni se acepta desde el cliente.
- **`tarifa_envio_admite_contraentrega` se guarda en falso por ahora.** Skydropx
  no declara la cobertura de recaudo en la tarifa —ninguna tarifa exitosa trae un
  campo que lo diga— y prometerla sin dato sería ofrecer un pago que después no
  existe. Ver `docs/13-skydropx-capacidades.md`, sección 6.
- **`tarifa_vence_en` no bloquea el pago.** Guarda cuándo caducan las 24 horas de
  validez de la tarifa. Un pedido no cambia de total porque la tarifa venció
  (`docs/11-pagos-y-envios.md`).
  **Y al despachar no se consulta**: la emisión recotiza siempre (`ADR-0033`). El
  camino "usar la congelada si todavía vive" se recorrería de vez en cuando —entre
  el pago y el despacho suele pasar más de un día— y se rompería callado. Lo que
  este campo sigue explicando es por qué el número que pagó el comprador y el que
  paga el negocio pueden no coincidir.

Del despacho en adelante el rastro vive en `Envio`:

```
envio
  id, pedido_id, comision_recaudo, recaudo_conciliado_en, creado_en

guia_envio
  id, envio_id, transportadora, codigo_transportadora, numero, costo_envio,
  url_etiqueta

evento_seguimiento
  id, guia_id, estado_proveedor, descripcion,
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
- **`codigo_transportadora` es el nombre de la transportadora en Skydropx**
  (`servientrega`, `ninetynineminutes`), no el que se muestra. El rastreo lo exige y
  responde 404 con el nombre visible, así que no se puede derivar del otro
  (`docs/13-skydropx-capacidades.md` §6.8). **Es opcional**: una guía tecleada en el
  panel puede ni siquiera existir en la plataforma —quien despacha pudo emitirla en
  la web de la transportadora—, así que lo que decide si se puede conciliar no es
  quién la lleva, es si la emitimos nosotros. Las que no lo tienen se saltan en la
  conciliación y se cuentan aparte.
- **`url_etiqueta` es opcional y no por descuido.** Es el rótulo que se imprime y se
  pega a la caja. Las guías tecleadas en el panel no lo tienen —se imprimieron por
  fuera— y de las que emitimos nosotros tampoco está garantizado: dos guías de
  Servientrega por el mismo camino, una lo trajo y la otra no lo trajo nunca, ni con
  el envío ya entregado (`docs/13-skydropx-capacidades.md` §6.7). Quien despacha no
  puede darlo por hecho.
- **`guia_envio.costo_envio` es interno.** Es lo que la transportadora cobra por ese
  paquete, y su suma frente al `costo_envio` cobrado al comprador es el margen del
  pedido. Ningún endpoint público lo devuelve.
- **Varias guías por envío, y el número es único en toda la tabla** (`ADR-0031`).
  Lo segundo es lo que deja resolver un evento del webhook con el número y nada
  más, sin preguntar la transportadora. Un pedido de dos variantes son dos guías
  con dos cobros: en Colombia ninguna transportadora admite multipaquete, y con
  dos bultos la cotización cobra el doble.
- **El rastro cuelga de la guía, no del envío.** La transportadora reporta el
  movimiento de un paquete; mezclar dos rastros le diría al comprador que le
  entregaron algo que sigue en camino.

### La emisión de la guía

Entre pedirle las guías a la plataforma y tenerlas hay minutos, y en ese intervalo
**ya se cobró**. Eso es lo que estas dos tablas guardan (`ADR-0033`):

```
emision_de_guia
  id, pedido_id, transportadora, id_tarifa, actor,
  estado, detalle, solicitada_en, resuelta_en

envio_en_plataforma
  emision_id, posicion, id_externo
```

- **La fila nace antes de la llamada, no después.** Ese es el punto entero: si se
  escribiera al recibir la respuesta, un reinicio del servicio entre el cobro y la
  respuesta dejaría una guía pagada que nadie sabe que existe.
- **`id_tarifa` es la llave de recuperación.** Skydropx cachea la creación por
  `rate_id` durante 96 horas (`unique_shipment`), así que repetir la petición con esa
  misma tarifa devuelve el envío que ya se pagó en vez de crear otro. Sin este campo
  escrito, esa ventana no sirve de nada.
- **Seis estados, y tres cuentan como abierta**: `SOLICITADA` (se va a pedir),
  `EN_CURSO` (cobró, falta la guía) e `INDETERMINADA` (la llamada no terminó y pudo
  cobrar igual). Un **índice único parcial** sobre esos tres impide que un pedido
  tenga dos emisiones abiertas: la segunda sería otro cobro por lo mismo, y entre
  leer y escribir cabe un segundo clic en el panel.
- **`INDETERMINADA` y `PARCIAL` piden una persona.** La primera porque no se sabe si
  hubo cobro; la segunda porque en multienvío unas guías pueden vivir y otras morir,
  y quedan guías pagadas que alguien tiene que cancelar o usar. Ningún programa las
  cierra.
- **`actor` está en la fila y no solo en el registro.** Es quien comprometió el saldo,
  y para algo que gasta dinero una línea de log no es auditoría.
- **`envio_en_plataforma` es tabla aparte y lleva `posicion`** porque en multienvío hay
  un envío por bulto y el orden es el de los bultos: es lo que permite decir cuál guía
  corresponde a cuál paquete cuando haya que mirar un fallo parcial. `id_externo` es
  único en toda la tabla.
- **Esto no es el `Envio`.** El envío nace cuando ya hay guías; aquí todavía no las
  hay. Y el pedido no se mueve de `EN_PREPARACION` hasta que las haya, que es lo que
  hace que una emisión fallida no tenga nada que devolver a ninguna cola.

### El acuse de revisión

Cinco estados de envío dejan el paquete quieto y dos de emisión dejan saldo
comprometido. Verlos es una consulta; **poder dejar de verlos** necesita una tabla:

```
acuse_revision
  id, tipo, guia_id, emision_id, revisado_en, actor, nota
```

- **Existe porque dos de esos cinco estados son terminales.** De una guía
  `CANCELADO` o `DESTRUIDO` no llega otro evento nunca, así que una bandeja
  calculada solo a partir del estado las acumularía para siempre y a los pocos
  meses sería una lista que nadie abre.
- **Dos columnas de referencia y no una suelta con un discriminador**, para que la
  llave foránea siga existiendo. Una restricción `check` las hace excluyentes según
  `tipo`: un acuse apunta a una guía o a una emisión, nunca a las dos ni a ninguna.
- **Append-only, como `evento_seguimiento`.** Acusar dos veces la misma guía son dos
  filas y dos momentos; ninguna pisa a la anterior. El día de la reclamación hay que
  poder decir quién sabía qué, y cuándo.
- **`revisado_en` es nuestro reloj.** Se compara contra `evento_seguimiento.recibido_en`
  —cuándo nos enteramos— y nunca contra `ocurrio_en`, que lo pone la transportadora:
  comparar dos relojes distintos haría que un evento con desfase pareciera anterior
  al acuse sin serlo, y el precio de equivocarse es una guía en excepción que
  desaparece de la vista sin que nadie la haya visto.
- **El acuse no resuelve nada.** Una emisión `INDETERMINADA` acusada sigue abierta y
  sigue bloqueando una emisión nueva de ese pedido. Decidir que no hubo cobro y
  pasarla a `FALLIDA` mueve plata: es otra decisión, con su propia puerta.

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
