---
name: fichas-de-tecnologia
description: Bloque 3 de la ingesta de tecnología — arma la ficha de cada modelo nuevo con sus fotos ya procesadas y la deja como borrador en el panel de Borradores de tecnología (/admin/tecnologia), listo para que una persona elija colores y precio y lo apruebe, que lo publica. Lleva también las configuraciones nuevas de los modelos que ya se venden. Solo corre cuando la tanda de fotos del bloque 2 terminó. Úsala cuando alguien diga «arma las fichas», «sube los borradores», «deja los productos en el panel», «ya están las fotos, sigue», «pasa los nuevos al panel», o cuando /fotos-estudio-degradado termine su tanda con TANDA COMPLETA. Covers creating technology product drafts with processed photos in the admin panel.
---

# Fichas de tecnología → borradores del panel

El tercero de los tres bloques de la ingesta de tecnología (`docs/adr/0079`):

| Bloque | Skill | Qué deja |
|---|---|---|
| 1 | `/listas-de-proveedor` | `catalogo/lista-api.json`, `catalogo/por-proponer.json` y el inventario de lo que ya se vende actualizado |
| 2 | `/fotos-estudio-degradado` | `Fotos procesadas/` y `estado-fotos.json` de cada modelo nuevo; ninguno pasa sin 4 fotos LISTA |
| 3 | **esta** | el borrador de cada modelo, **con sus fotos**, en `/admin/tecnologia` |

**Esta skill no investiga, no retoca y no toca el inventario.** Lee lo que dejaron
los otros dos bloques y lo lleva al panel. Si algo falta, se vuelve al bloque que
lo produce; no se completa aquí.

## Antes de empezar: ¿terminó la tanda?

La regla del negocio (10/10/2026) es que **ningún producto llega al panel a
medias**. Comprueba, sin escribir nada:

```bash
node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --borradores --proveedor <id>
```

La salida lista lo que va y, con `se queda`, lo que no:

- `espera fotos` o `no pasó por la tanda` → **para aquí.** La tanda del bloque 2
  no terminó: dile a la persona qué modelos faltan y que siga con
  `/fotos-estudio-degradado`. No lleves los demás mientras tanto: la tanda se
  entrega completa.
- `fuera de la tanda` → la persona sacó ese modelo a mano, con su motivo. Se
  queda fuera de esta corrida y no la bloquea; dilo en la entrega.

Y comprueba que `por-proponer.json` es de la misma lista que `lista-api.json`: la
herramienta se niega si las fechas no coinciden, porque los borradores de una
lista con el inventario de otra mezclan costos de dos días.

## Flujo

### 1. Mostrar lo que va

Con la simulación de arriba, dile a la persona en pocas líneas: cuántos modelos
nuevos van —con cuántas fotos cada uno— y cuántas configuraciones nuevas de
modelos que ya se venden, y qué se queda fuera y por qué.

### 2. Llevarlo al panel

```bash
node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --borradores --proveedor <id> --escribir
```

Necesita la sesión del panel en `--token` o `TS_TOKEN_ADMIN`. **No la pidas por
terminal ni le pidas a la persona la clave**: los prompts no funcionan desde `!`
en Claude Code.

Hace dos cosas, en este orden:

1. **Importa los borradores** (modo `BORRADORES` de la API): entra o se actualiza
   el borrador de cada modelo nuevo apto, y el de las configuraciones nuevas de
   los que ya se venden. No toca costo ni existencia: eso ya lo hizo el bloque 1.
2. **Sube al borrador las fotos LISTA** de cada modelo nuevo, en el orden de su
   `estado-fotos.json` —la primera es la principal—, con sus anchos AVIF y la
   vista previa, al bucket privado del proveedor.

Se puede correr otra vez sin miedo: un borrador que ya tiene sus fotos no se toca,
y uno que quedó a medias —una corrida que se cayó— se rehace entero para que la
principal siga siendo la primera.

### 3. Entregar

Dile a la persona que los borradores están en **`/admin/tecnologia`**, y qué hace
ahí con cada uno:

- marcar qué colores hay de cada configuración —los que sugiere la lista vienen
  marcados—;
- fijar el precio de venta, que tiene que superar el costo;
- elegir la marca y la categoría del catálogo;
- **aprobar, que publica el producto con sus fotos**, o rechazar con el motivo.

Si en el bloque 1 algún precio de mercado quedó por debajo del costo, recuérdalo
aquí: es justo el momento en que alguien fija el precio.

Un modelo rechazado no se vuelve a proponer en las listas siguientes; las
configuraciones que se dejen sin colores al aprobar, tampoco.

## Cosas que se rompen si no se cuidan

**Llevar un modelo sin pasar por la tanda.** La herramienta lo deja fuera, y el
servidor no aprueba un modelo nuevo con menos de 4 fotos; pero si alguien sube
fotos a mano al borrador, se salta la compuerta de calidad —solo LISTA— que el
servidor no puede ver. Las fotos de un borrador salen siempre de esta skill.

**Correr los borradores de una lista vieja.** La API rechaza los de una fecha
anterior a la última lista importada: le pondrían al borrador el costo de antes.
Si pasa, el bloque 1 ya procesó una lista más nueva; se corre esta skill con la
de esa lista.

**Borrar `catalogo/entregables/fichas/`.** Las fotos que se suben salen de
`Fotos procesadas/` de cada modelo, y la herramienta se niega si la tanda contó
una foto que ya no está. Las carpetas viven fuera del repositorio; el registro de
lo procesado está en `referencias/conocidos.json` de la skill de listas.

## Archivos

```
tools/importar-lista-tecnologia.mjs   --borradores: selección, importación y subida de fotos
tools/subida-de-imagenes.mjs          la subida de las variantes, compartida con el catálogo
tools/material-catalogo.mjs           la carpeta de cada modelo y sus fotos procesadas
docs/adr/0079-la-ingesta-de-tecnologia-en-tres-bloques.md
```
