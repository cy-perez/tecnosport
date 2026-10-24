---
name: fichas-de-tecnologia
description: Bloque 3 de la ingesta de tecnología — arma la ficha de cada modelo nuevo con sus fotos ya procesadas y la deja como borrador en el panel de Borradores de tecnología (/admin/tecnologia), listo para que una persona elija colores y precio y lo apruebe, que lo publica. Lleva también las configuraciones nuevas de los modelos que ya se venden. Solo corre cuando la tanda de fotos del bloque 2 terminó. Úsala cuando alguien diga «arma las fichas», «sube los borradores», «deja los productos en el panel», «ya están las fotos, sigue», «pasa los nuevos al panel», o cuando /fotos-estudio-degradado termine su tanda con TANDA COMPLETA. Covers creating technology product drafts with processed photos in the admin panel.
---

# Fichas de tecnología → borradores del panel

El tercero de los tres bloques de la ingesta de tecnología (`docs/adr/0079`):

| Bloque | Skill | Qué deja |
|---|---|---|
| 1 | `/listas-de-proveedor` | `catalogo/5-api/lista-api.json`, `catalogo/5-api/por-proponer.json` y el inventario de lo que ya se vende actualizado |
| 2 | `/fotos-estudio-degradado` | `Fotos procesadas/` y `estado-fotos.json` de cada modelo nuevo; ninguno pasa sin 4 fotos LISTA |
| 3 | **esta** | el borrador de cada modelo, **con sus fotos**, en `/admin/tecnologia` |

**Esta skill no investiga, no retoca y no toca el inventario.** Lee lo que dejaron
los otros dos bloques y lo lleva al panel. Si algo falta, se vuelve al bloque que
lo produce; no se completa aquí.

Tampoco redacta: la descripción —con sus secciones y su orden— la escribe el
bloque 1 con la plantilla de `referencias/descripciones.md`, y el sitio la pinta
por secciones con `shared/ui/descripcion` (23/10/2026). Antes de esa fecha la
ficha la mostraba en un solo párrafo con la sintaxis a la vista: si alguien
pide «ordenar la ficha», el arreglo está en el texto del bloque 1 o en ese
componente, nunca en esta skill.

## Antes de empezar: ¿terminó la tanda?

La regla del negocio (10/10/2026) es que **ningún producto llega al panel a
medias**. Comprueba, sin escribir nada:

```bash
node tools/importar-lista-tecnologia.mjs catalogo/5-api/lista-api.json --borradores --proveedor <id>
```

La salida lista lo que va y, con `se queda`, lo que no:

- `espera fotos` o `no pasó por la tanda` → **para aquí.** La tanda del bloque 2
  no terminó: dile a la persona qué modelos faltan y que siga con
  `/fotos-estudio-degradado`. No lleves los demás mientras tanto: la tanda se
  entrega completa.
- `fuera de la tanda` → la persona sacó ese modelo a mano, con su motivo. Se
  queda fuera de esta corrida y no la bloquea; dilo en la entrega.
- `sin ficha en la lista` → la API lo espera pero el bloque 1 no lo exportó,
  casi siempre por falta de descripción. No bloquea esta corrida ni la tanda del
  bloque 2; dilo en la entrega: su borrador saldrá cuando el bloque 1 complete
  la ficha y la lista siguiente lo traiga.

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
node tools/importar-lista-tecnologia.mjs catalogo/5-api/lista-api.json --borradores --proveedor <id> --escribir
```

Necesita la sesión del panel en `--token` o `TS_TOKEN_ADMIN`. **No la pidas por
terminal ni le pidas a la persona la clave**: los prompts no funcionan desde `!`
en Claude Code.

Hace dos cosas, en este orden:

1. **Importa los borradores** (modo `BORRADORES` de la API): entra o se actualiza
   el borrador de cada modelo nuevo apto, y el de las configuraciones nuevas de
   los que ya se venden. No toca costo ni existencia: eso ya lo hizo el bloque 1.
2. **Sube al borrador las fotos LISTA** de cada modelo nuevo, en el orden de su
   `estado-fotos.json` —la primera es la principal: la elegida con `marcar.py
   --principal` en el bloque 2, o la primera por orden natural—, con sus anchos
   AVIF y la vista previa, al bucket privado del proveedor. La principal es la
   portada del producto en el sitio: si nadie la eligió, sube la `_1` que la
   persona descargó primero. El bloque 2 deja `portadas.jpg` para elegirlas
   todas de una vez (22/10/2026); si la entrega va sin elegir, dilo.

Se puede correr otra vez sin miedo. Un borrador que ya tiene exactamente esas
fotos —los mismos hashes, en el mismo orden— no se toca; uno con otras —una
corrida que se cayó, o una tanda que se repitió con fotos nuevas— se rehace
entero, para que la principal siga siendo la primera. Nunca se suben más de 10:
la principal y las 9 de la galería del producto, el mismo tope que
`BorradorTecnologia.TOPE_DE_FOTOS` en la API.

Y se niega en dos casos, a propósito:

- `lista-api.json` cambió después de que su inventario entró: los borradores
  llevarían un contenido que el inventario nunca vio. Si la salida nombra la
  copia de lo que entró (`5-api/entro-<fecha>-<huella>.json`), se corren los
  borradores con ese archivo; si no hay copia —inventarios de antes del
  21/10/2026—, se corre el inventario de esa versión con el bloque 1.
- El inventario de esa lista no es el último que entró (la API responde 409): o
  no se importó, o ya entró una lista más nueva.

El inventario de cada lista refresca, además, los borradores que siguen en
revisión —costo, configuraciones y colores—, y **aprobar se niega si el borrador
no es de la última lista**: un modelo que dejó de venir no se publica con
existencia que el proveedor ya no tiene. «Dejó de venir» incluye, desde el
15/10/2026, lo que vino con menos de un 5 % de margen: la regla 18 del bloque 1
lo manda como desaparecido. Un borrador de una lista anterior cuyo costo de hoy
ya no deja ese margen se queda sin poder aprobarse, y es lo esperado: dilo en
la entrega con su margen, que está en el Excel del bloque 1, en vez de buscar
cómo aprobarlo.

### 3. Entregar

Lee **`catalogo/5-api/avisos-panel.json`** —lo deja la exportación del bloque 1—
y repítele a la persona, por borrador, lo que tiene que resolver a mano al
revisarlo: la configuración **sin precio de mercado** (con su costo, porque el
panel solo exige superar el costo y sin referencia se fija un precio a ciegas),
la **paleta vacía** (los colores se marcan a mano) y las **notas de datos
pendientes** que la descripción publica tal cual («se le pidió al proveedor»,
«por confirmar»): aprobar es el momento de resolverlas o de aceptarlas a
sabiendas.

Dile a la persona que los borradores están en **`/admin/tecnologia`**, y qué hace
ahí con cada uno:

- abrir **«Ficha»** y leerla como la va a leer el cliente: el panel la pinta con
  el mismo componente que la ficha del sitio —subtítulos, viñetas y la ficha
  técnica en tabla, **sin fuentes**—. Si algo sale con `##`, `|` o `**` a la
  vista, o una frase dice de dónde salió un dato («según la ficha de…», «la
  tabla técnica sale de…»), la regla se corrige en el bloque 1
  (`referencias/descripciones.md` de `/listas-de-proveedor`) para las fichas que
  vengan. Desde el 23/10/2026 la descripción no nombra su fuente, tampoco el aviso
  de Open Icecat que pide su licencia: es un riesgo que asumió el negocio
  (`docs/14-consultas-al-abogado.md`, numeral 8);
- **editar la ficha** si hay algo que no se quiere publicar —«Editar la ficha»,
  desde el 23/10/2026—: aprobar publica la editada. La edición se guarda aparte de
  la de la skill, así que **correr esta skill otra vez no la pisa**; si la lista
  nueva trae otra ficha, el panel lo avisa y «Volver a la de la skill» la toma.
  Una corrección que deba valer para las listas siguientes va, además, al bloque 1;
- **subir, quitar o reordenar fotos** y elegir la principal, también desde el
  panel. Lo que se sube ahí no pasa por la revisión LISTA del bloque 2: lo decide
  quien revisa (ver «Cosas que se rompen»);
- marcar qué colores hay de cada configuración —los que sugiere la lista vienen
  marcados; si la lista no trae colores, la sugerencia es la paleta completa del
  modelo (regla 13 del bloque 1, decisión del 22/10/2026), así que se desmarca lo
  que no haya en vez de marcarlo todo a mano—;
- fijar el precio de venta, que tiene que superar el costo;
- elegir la marca y la categoría del catálogo;
- **aprobar, que publica el producto con sus fotos**, o rechazar con el motivo.

Lo que quedó por debajo del costo, o con menos de un 5 % de margen, ya no llega
aquí: la regla 18 del bloque 1 lo deja fuera de `lista-api.json`. Lo que sí
llega sin evaluar es la configuración **sin precio de mercado** —la regla no
tiene con qué medirla—: recuérdala aquí con su costo, porque es justo el
momento en que alguien fija el precio, y el panel solo exige que supere el costo.

Un modelo rechazado no se vuelve a proponer en las listas siguientes; las
configuraciones que se dejen sin colores al aprobar, tampoco.

## Cosas que se rompen si no se cuidan

**Llevar un modelo sin pasar por la tanda.** La herramienta lo deja fuera, y el
servidor no aprueba un modelo nuevo con menos de 4 fotos. Desde el 23/10/2026 el
panel deja subir fotos al borrador a mano, y esas se saltan la compuerta de
calidad —solo LISTA— que el servidor no puede ver: es para corregir una o dos
tomas, no para llenar un modelo que no pasó por la tanda. Las de la tanda salen
de esta skill. **Y una corrida de esta skill sobre un borrador que alguien
reordenó o al que le subió fotos lo rehace entero** (otros hashes, otro orden):
si ya se trabajó el borrador a mano, no se vuelve a correr sobre él.

**Correr los borradores de una lista vieja.** La API rechaza los de una fecha
anterior a la última lista importada: le pondrían al borrador el costo de antes.
Si pasa, el bloque 1 ya procesó una lista más nueva; se corre esta skill con la
de esa lista.

**Borrar `catalogo/4-entregables/fichas/`.** Las fotos que se suben salen de
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
