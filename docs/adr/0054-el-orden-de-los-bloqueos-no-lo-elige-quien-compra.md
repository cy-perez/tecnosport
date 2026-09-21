# ADR-0054 — El orden de los bloqueos no lo elige quien compra

**Fecha:** 2026-09-21
**Estado:** aceptado. Corrige tres huecos de concurrencia que `adr/0049` y `adr/0050` dejaron
abiertos al mover la existencia al libro de movimientos.

## Contexto

La revisión adversarial de lo que entró entre el 20 y el 21 de septiembre encontró que la garantía
que sostiene todo el inventario —*"el bloqueo pesimista impide que dos compradores reserven la
misma última unidad"*— tenía tres agujeros, y que ninguno se veía leyendo el código de un solo
archivo.

Los tres comparten la misma forma: una afirmación escrita en un javadoc que es cierta en el camino
que se probó y falsa en otro que nadie miró.

## Decisión

### 1. Los bloqueos se toman en orden de `varianteId`, no en el del cuerpo HTTP

`CrearPedido` tomaba un bloqueo pesimista por línea, recorriendo `comando.lineas()` en el orden en
que llegaron, y no lo soltaba hasta el commit. Dos compradores con las mismas variantes en distinto
orden —A con `[X, Y]`, B con `[Y, X]`— se bloqueaban en cruz. Postgres detecta el interbloqueo y
aborta una de las dos con `40P01`; nadie lo atrapa ni lo reintenta, así que el comprador lo veía
como un **500 con el carrito lleno y el pago a un clic**.

Y era provocable a propósito: el orden del arreglo `lineas` lo elige quien postea.

Un orden total y estable sobre el recurso que se bloquea lo hace imposible. Dos transacciones que
pidan los mismos libros los piden en la misma secuencia, así que la segunda espera a la primera en
el primero que compartan, en vez de esperarse mutuamente. Se eligió `varianteId` porque es el id
del recurso que de verdad se bloquea; ordenar por SKU o por nombre sería un orden sobre otra cosa.

**El pedido conserva el orden del comprador.** Lo que se ordenó es la toma de bloqueos, no las
líneas del comprobante, y hay una prueba para cada una de las dos cosas.

#### Y las líneas duplicadas se rechazan

Nadie comprobaba que una variante no viniera dos veces. El carrito no lo produce —reconcilia por
variante y suma cantidades— pero la API es pública y la app móvil todavía no existe.

Se rechaza con `422 LINEAS_DUPLICADAS` en vez de sumar las cantidades. Sumarlas sería el servidor
decidiendo por el comprador algo que no pidió: una línea repetida por un defecto del cliente se
convertiría en una compra de dos unidades que alguien tiene que devolver.

La guarda es estática y vive en `Pedido`, con la forma de `AutorizacionDatos.exigirAutorizacion`,
porque `CrearPedido` tiene que poder exigirla **antes de reservar nada**. `Pedido.crear` la vuelve
a exigir como red para cualquier otro camino.

### 2. Abrir el libro de una variante es una operación del repositorio, con bloqueo

`AjustarExistencia` decía en su javadoc que el bloqueo pesimista de `buscarPorVarianteId` *"es lo
que impide que un conteo y una reserva simultánea se pisen"*. Eso era cierto **solo en una de sus
dos ramas**: con `orElseGet(() -> Inventario.crear(...))`, cuando la variante no tenía libro el
`select … for update` no encuentra fila, así que no bloquea nada y la transacción sigue creyendo
que lo tiene. Dos conteos simultáneos escribían dos agregados contra `ux_inventario_variante` y el
que perdía moría con una violación de integridad sin traducir: un 500 genérico, y el conteo de
quien recorrió la bodega perdido sin decirle por qué.

El puerto gana `abrirLibroConBloqueo(varianteId)`: inserta de forma idempotente —`on conflict
(variante_id) do nothing`— y vuelve a leer con `@Lock`. Dos transacciones que lleguen a la vez no
chocan: la segunda espera a que la primera confirme y entonces no inserta nada.

**No se quitó la rama**, que era la opción barata. La pantalla de existencias enseña precisamente
las variantes sin libro, así que negarse a contarlas dejaría filas imposibles de corregir desde el
sitio que existe para corregirlas.

### 3. `guardar` escribe los movimientos nuevos, no el histórico entero

`RepositorioInventarioJpa.guardar` pasaba a `saveAll` **todos** los movimientos del agregado.
`MovimientoInventarioJpaEntity` lleva el `@Id` asignado y no tiene `@Version`, así que Spring Data
la da por existente y cada `save` acababa en un `merge`: un `SELECT` y un posible `UPDATE` por cada
movimiento ya guardado.

Reservar una unidad de una variante con ochocientos movimientos eran unas mil seiscientas
sentencias, emitidas **con el bloqueo pesimista ya tomado**, y `CrearPedido` lo hace una vez por
línea. El checkout se degradaba justo en las variantes que más se venden, que son las que más
histórico acumulan, y por serialización: mientras una transacción emite las suyas, la otra espera.

`adr/0050` §3 puso por escrito el precio de **leer** el histórico entero. El de reescribirlo no
estaba aceptado en ninguna parte.

`Inventario` sabe ahora cuáles de sus movimientos se le agregaron a esta instancia. No es un
detalle de persistencia colado en el dominio: el libro es de solo-agregar, así que "cuáles son
nuevos" es una pregunta que el agregado puede responder y nadie más. Y la entidad implementa
`Persistable` para decirle a Spring Data lo que el `@Id` asignado le oculta.

## Alternativas descartadas

**Reintentar el interbloqueo en vez de ordenar.** Un `@Retryable` sobre `40P01` convierte un fallo
en latencia, pero deja la carrera viva: con tres o cuatro artículos populares los reintentos se
vuelven a chocar entre ellos. Ordenar elimina la condición; reintentar la administra.

**Sumar las cantidades de las líneas duplicadas.** Más amable con un cliente descuidado y con el
reintento de una app móvil, y por eso mismo peor: convierte un defecto del cliente en una compra
que nadie hizo. El servidor no interpreta lo que el cliente quiso decir.

**Quitar la rama del conteo sin libro.** La más barata, y deja filas que la pantalla enseña y no
deja arreglar.

**Un `@Version` en el movimiento en vez de `Persistable`.** Resuelve lo mismo y añade una columna y
una migración a una tabla que nunca se actualiza: bloqueo optimista para filas que no cambian.

## Consecuencias

- El checkout deja de poder interbloquearse por el orden de las líneas, y eso ya no depende de que
  el cliente se porte bien.
- El coste de escritura de una reserva deja de crecer con el histórico de la variante.
- Queda una deuda anotada: **el disponible negativo** —contar por debajo de lo reservado, que
  `adr/0049` §4 acepta grabar— solo avisa con un `log.warn`. Quien cuenta lo ve en la pantalla, y
  nadie más se entera. Decidir si eso merece un correo, una bandeja o una alerta de Cloud Logging
  es una decisión de operación que este ADR no toma.
- Y otra: **un conteo que confirma lo que ya había no deja ningún rastro**. `adr/0049` §2 decidió
  que un movimiento de cantidad cero no existe, y la consecuencia es que "nunca contada" y
  "contada ayer y estaba bien" son indistinguibles. Revisarlo es revisar aquella decisión.
