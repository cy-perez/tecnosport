# ADR-0049 — La existencia se corrige contando, y el catálogo copia el conteo

**Fecha:** 2026-09-20
**Estado:** aceptado

## Contexto

Los doce primeros productos reales se cargaron el 19 de septiembre **con existencia 5**, un número
inventado. Quedó escrito ese mismo día, y el 19 por la noche quedó escrito otra vez al construir el
vigilante de lo que falta por medir: *"la existencia sigue sin poderse corregir (...) queda como la
siguiente tarea de esta rama"*.

Al ir a hacerlo aparecieron dos cosas que el enunciado no decía.

### Hay dos existencias y no se hablan

| | Qué es | Quién la mueve |
|---|---|---|
| `variante.existencia` | una columna del catálogo | **`AgregarVariante`, al crear la variante. Nadie más, nunca** |
| `Inventario` | el libro de movimientos | `CrearPedido` reserva, el pago confirma, el retracto devuelve |

La columna es la que sale en `VarianteRespuesta.existencia`, y es la que la vitrina lee para decidir
si un producto está agotado (`producto.model.ts`, `seleccion-variante.ts`). El libro es el que
decide de verdad si una compra se puede completar: `CrearPedido` reserva contra él con bloqueo
pesimista.

O sea que **vender las cinco unidades de una variante no cambia el número que ve quien compra.** La
ficha sigue diciendo 5 para siempre. `RepositorioProductos` no tiene —no tenía— un solo método capaz
de actualizar esa columna.

Ese defecto ya existía y no lo causa este ajuste. Pero cualquier ajuste que escriba en un solo lado
lo empeora, así que había que decidirlo antes de escribir el caso de uso, no después.

### El dominio ya estaba hecho

`Inventario.registrarAjuste(cantidad, motivo, ahora)` existe desde la Fase 2: acepta cantidad con
signo, exige motivo, se niega a dejar el saldo total en negativo y deja el movimiento en el
histórico. Lo que faltaba era todo lo de arriba — caso de uso, puerto, endpoint y pantalla.

## Decisión

### 1. El ajuste escribe en el libro y copia el resultado a la columna

De las tres salidas evaluadas para la desincronización:

- **A. El ajuste escribe en los dos.** Barato: un método nuevo en `RepositorioProductos`.
- **B. La columna pasa a ser una proyección que recalcula todo el que mueva inventario.** Obliga a
  tocar `CrearPedido` y el aplicador de resultado de pago.
- **C. Se borra la columna y la respuesta calcula el disponible leyendo el libro.** Cambia el
  contrato público, el mapeador, la vitrina y el OpenAPI, y mete una lectura del inventario en las
  consultas del catálogo.

**Se elige A, y C queda como tarea siguiente.** B tiene el peor perfil de los tres: cuesta tocar el
camino del pago —donde un error se paga con un pedido— y aun así deja la vitrina mostrando el saldo
*total*, cuando lo que decide si se puede comprar es el *disponible*, que baja con cada reserva y
sube solo cuando una vence. Pagar el precio de B para seguir mostrando un número equivocado es el
peor de los dos mundos.

C es la correcta y se sabe. Lo que no es correcto es meterla dentro de esta tarea: convierte "el
panel corrige la existencia" en "se rediseña cómo la vitrina sabe si hay existencia", que toca el
catálogo público y merece su propia revisión.

**Con A, la columna y el libro quedan iguales en el momento de contar**, que es exactamente lo que
hace falta hoy para quitar de encima el 5 inventado.

### 2. El panel manda el conteo físico, no la diferencia

Quien cuenta sabe *"hay tres"*, no *"menos dos"*. El caso de uso resta contra `saldoTotal()` y
registra el ajuste con la diferencia.

Pedirle la diferencia a quien acaba de contar le pide además una resta contra un número que tiene
que ir a buscar, y **un error de esa resta es indistinguible de una pérdida real**: las dos cosas
llegan como un ajuste negativo con un motivo escrito por una persona.

Consecuencia: contar lo mismo que ya había no escribe nada. `registrarAjuste` se niega —con razón— a
registrar un movimiento de cantidad cero, así que el caso de uso lo resuelve antes y responde "sin
cambios", igual que `MedirVariante` distingue medir de remedir.

### 3. El motivo es obligatorio

Un libro de movimientos sin motivo es un contador con pasos de más. El dominio ya lo recibe; lo que
se agrega es que no pueda llegar vacío.

### 4. Contar por debajo de lo reservado se permite y se avisa

Si hay dos unidades comprometidas en pedidos en vuelo y el conteo físico da una, **la realidad es
esa**, y prohibir la corrección solo consigue que la base siga mintiendo con más confianza.

Se graba, la respuesta lo dice y el servidor lo registra como `warn`: hay pedidos aceptados que no
se van a poder despachar, y eso es un problema de operación que alguien tiene que atender, no un
error de digitación que se pueda rechazar en un formulario.

El límite duro sigue siendo el del dominio: el saldo total no puede quedar negativo.

### 5. Una variante sin libro no bloquea la corrección

`SembradorCatalogo` escribe entidades JPA directo, así que puede existir una variante sin fila de
inventario. El caso de uso crea el libro en ese caso en vez de fallar: negarse a corregir por un
defecto de datos anterior deja el dato malo en pie y obliga a arreglarlo con SQL, que es justo de lo
que se está saliendo.

### 6. La pantalla enseña las tres cifras y marca el descuadre

La lista trae, por variante: lo que dice el catálogo, el saldo total del libro y el disponible. Y
marca la variante como **descuadrada** cuando las dos primeras no coinciden.

Ese descuadre es el defecto del contexto hecho visible: mientras la opción C no exista, **cada venta
lo produce**. Enseñarlo es más honesto que ocultarlo y es, de paso, el argumento acumulándose solo
para hacer C.

Las descuadradas van primero en la lista, y después los productos publicados y el nombre —el mismo
criterio de `ListarVariantesSinMedir`, con un escalón más arriba: lo que está desalineado es lo que
hay que mirar hoy.

## Alternativas rechazadas

- **Un endpoint que fije la columna del catálogo directamente**, sin pasar por el libro. Es lo más
  corto y borra el histórico: dejaría un número sin motivo, sin fecha y sin autor, en un sistema
  cuyo inventario es por movimientos precisamente para no tener eso.
- **Rechazar el conteo que deja reservas sin respaldo** (ver decisión 4).
- **Restringir el ajuste a sumar.** Una pérdida, un daño o una unidad que nunca llegó son ajustes
  negativos legítimos, y son justo los que más falta hace que queden escritos con su motivo.

## Consecuencias

- El 5 inventado se puede corregir desde el panel, con motivo y con rastro.
- La vitrina sigue mostrando un número que solo se actualiza cuando alguien cuenta. **No es una
  regresión —hoy no se actualiza nunca— pero tampoco es la solución.** La pantalla lo hace visible
  marcando el descuadre, y C queda pendiente.
- El listado carga los libros de inventario sin bloqueo y calcula los saldos **con el dominio**, en
  vez de reimplementar en SQL qué reserva sigue vigente — tres condiciones que tendrían que quedarse
  en sincronía con `Inventario` para siempre. El precio, y conviene no disimularlo: trae el
  histórico completo de movimientos del catálogo, que crece con las ventas y no solo con el número
  de productos. Con doce productos no se nota; con un catálogo grande y un año de ventas encima, esa
  pantalla necesita paginación o una proyección, y quien la toque entonces tendrá que elegir de
  nuevo entre duplicar la regla en SQL o probar la duplicación.

## Lo que esto NO arregla

- **La columna se sigue desincronizando con cada venta** (opción C).
- **El conteo real de los doce productos cargados sigue siendo un dato de negocio que el sistema no
  puede inventar.** Esta decisión construye la puerta; las cifras las escribe una persona que contó.
