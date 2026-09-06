# ADR 0017. `variante.existencia` y `Inventario` conviven, no se unifican

Fecha: 2026-09-05. Estado: aceptada, con deuda pendiente de revisar.

## Contexto

`docs/02-modelo-datos.md` ya decía, desde la Fase 1: "el saldo de existencias
no es una columna que se actualiza. Es la suma de `MovimientoInventario`... Se
guarda un saldo materializado por rendimiento, pero se recalcula y se
concilia." En la práctica, hasta el cierre de esta fase, esas dos cosas
habían evolucionado por caminos separados que nunca se habían encontrado:

- La ficha pública y la rejilla (`MapeadorCatalogo`, desde la Fase 1) leen
  directo la columna `variante.existencia`.
- El checkout (`Inventario.saldoDisponible`, desde la Fase 2/3) calcula el
  saldo a partir de los movimientos, para reservar con bloqueo pesimista.

Al construir "agregar variante" (Track B, paso 4) — el primer caso de uso del
panel admin que crea inventario — hubo que decidir qué pasa con las dos vías
de lectura cuando nace una variante nueva.

## Decisión

Las dos conviven, mantenidas en sync a mano en el punto donde se crean: al
agregar una variante, `AgregarVariante.ejecutar` escribe la existencia
inicial tanto en la columna `variante.existencia` como en un movimiento
`ENTRADA` de `Inventario`, en la misma transacción. Ninguna de las dos es
"la copia", pero si algo las desincroniza más adelante (un ajuste manual que
solo toque una de las dos, por ejemplo), no hay ningún mecanismo que lo
detecte ni lo corrija.

No se aprovechó este caso de uso para migrar la ficha pública a leer
`Inventario.saldoDisponible` en vez de la columna — es un cambio aparte, más
grande (afecta un camino de lectura de la Fase 1, no solo el nuevo), y no
era necesario para que "agregar variante" funcionara.

## Alternativas

**Migrar la ficha pública a leer `Inventario.saldoDisponible`, eliminar la
columna `variante.existencia`**: es la solución de fondo, alineada con lo
que ya decía `docs/02-modelo-datos.md` desde la Fase 1. Se descartó para esta
pasada por alcance: tocar `MapeadorCatalogo` y la ruta de lectura pública no
era parte del plan de "agregar variante", y mezclar los dos habría hecho el
caso de uso mucho más grande de lo necesario para cerrarlo.

**No escribir la columna al agregar variante, dejarla en su valor por
defecto**: se descartó porque habría roto la ficha pública de inmediato para
cualquier variante creada desde el panel — el catálogo mostraría "sin
existencia" para un producto que sí tiene inventario real.

## Consecuencias

Cualquier operación futura que toque existencia (ajustes de inventario,
devoluciones, ventas fuera del checkout) tiene que acordarse de escribir las
dos vías o el catálogo público y el checkout van a mostrar números distintos
para la misma variante. Es una fuente real de bugs silenciosos mientras no
se resuelva. Queda como deuda explícita: unificar el camino de lectura
público a `Inventario.saldoDisponible` antes de construir el siguiente caso
de uso que también escriba existencia (el pendiente de reabastecimiento,
`GET/POST /api/v1/admin/variantes/{id}/inventario`, ya anotado como
pendiente en `docs/03-api.md`, es buen candidato para resolver esto de
una vez en vez de heredar el mismo parche).
