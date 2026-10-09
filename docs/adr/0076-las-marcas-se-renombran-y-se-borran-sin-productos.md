# ADR-0076: las marcas se renombran desde el panel, y se borran solo sin productos

Fecha: 2026-10-09
Estado: aceptada
Reemplaza a: la sección "Lo que el panel puede y lo que no" de `ADR-0047`, que
dejaba fuera renombrar y borrar. El resto de aquel ADR sigue en pie.

## Contexto

`ADR-0047` abrió el alta de marcas desde el panel y cerró a propósito las otras
dos operaciones. Dejó escrito su costo: "una marca mal escrita se queda mal
escrita", y el arreglo era una migración o una marca huérfana. También dejó la
condición para reabrirlo: antes de renombrar había que saber qué les pasa a las
URL y a los filtros que ya existen.

El 9 de octubre de 2026 se pidió renombrar y eliminar desde la pantalla de
marcas.

## Lo que se comprobó antes de decidir

- **El filtro de la vitrina va por id**, no por nombre: `?marca=<uuid>`
  (`query-params-filtro.ts`). Un enlace compartido con el filtro puesto sigue
  llevando a la misma marca después de renombrarla.
- **El slug del producto sale del nombre del producto** (`CrearProducto`,
  `Slug.generarDesde(comando.nombre())`) y se fija al crearlo. La marca no entra
  en ninguna URL.
- **La única llave hacia `marca` es `producto.marca_id`** (`V1`), sin cascada.
  Los borradores de tecnología guardan `marca_sugerida` como texto suelto, sin
  llave.

## Decisión

1. **Renombrar** (`RenombrarMarca`, `PUT /api/v1/admin/marcas/{id}`) cambia el
   nombre y nada más: el id, y con él todos los productos y los filtros, se
   quedan. Aplica las reglas del alta (`Marca`: no vacío, 120 caracteres, sin
   espacios a los lados) y la misma unicidad sin distinguir mayúsculas, **sin
   contarse a sí misma**: corregir "xiaomi" a "Xiaomi" no choca. El índice de
   `V56` sigue siendo el guardián de la carrera, y su violación se traduce en el
   adaptador a `MarcaYaExisteException` (409).
2. **Eliminar** (`EliminarMarca`, `DELETE /api/v1/admin/marcas/{id}`) solo
   procede **si la marca no tiene ningún producto, en ningún estado**: también
   borradores y archivados, porque la llave no distingue. Con productos responde
   409 `MARCA_CON_PRODUCTOS` diciendo cuántos, y el panel indica moverlos a otra
   marca desde el formulario de cada producto. La carrera —un producto que entra
   entre la cuenta y el borrado— la ataja la llave, y el adaptador la traduce a
   la misma excepción.
3. **No hay reasignación en bloque.** Mover los productos de una marca a otra de
   un golpe cambiaría fichas publicadas sin que nadie las mire una por una, y
   para el caso que motiva esto —una marca mal escrita o creada por error— la
   marca casi siempre está vacía.

## Consecuencias

**A favor:**

- Una marca mal escrita se corrige en el panel, sin migración ni despliegue.
- La marca creada por error se puede quitar, y deja de aparecer en el
  desplegable del formulario de producto.

**En contra:**

- **Renombrar se ve en la tienda de inmediato**: ficha, filtro y lo que el
  buscador indexe después. Es lo que se pide, pero no tiene ensayo previo. El
  texto de la pantalla lo advierte.
- **El nombre del producto no cambia con la marca.** Un producto que se llama
  "Xaomi Redmi 13" sigue llamándose así: su nombre es suyo y se corrige en su
  formulario. La marca solo cambia lo que dice el campo marca.
- **Unir dos marcas duplicadas** (la misma marca escrita dos veces, cada una con
  productos) sigue siendo trabajo a mano: mover los productos uno por uno y
  luego borrar la vacía.
