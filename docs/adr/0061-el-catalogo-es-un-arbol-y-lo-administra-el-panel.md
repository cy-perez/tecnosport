# ADR-0061 — El catálogo es un árbol, y lo administra el panel

**Fecha:** 24 de septiembre de 2026
**Estado:** aceptada

## Contexto

Hasta hoy el catálogo tenía **dos niveles y medio**: cuatro… no, tres líneas de negocio en un enum
(`ROPA_Y_CALZADO`, `BOLSOS`, `TECNOLOGIA`) y, colgando de cada una, una lista **plana** de
categorías en base de datos. Ocho de ellas —celulares, tablets, relojes, audífonos, consolas,
computadores, proyectores, parlantes— entraron por migración (`V38`, `V62`) y son las que la skill
`listas-de-proveedor` sabe reponer.

El negocio pidió el surtido de ropa, calzado y bolsos con la forma que de verdad tiene:

```
Ropa
 ├─ Dama       → Camisas, Blusas, Busos, Pantalones, Faldas, Shorts, Bodis, Licras, Sudaderas
 └─ Caballero  → Camisetas, Busos, Sudaderas, Pantalonetas
Calzado deportivo (tenis)
 ├─ Dama   ├─ Caballero   └─ Unisex
Bolsos
 └─ Dama → Bolsos de mano, Manos libres, Morrales
```

Eso son **tres** niveles bajo la raíz, y el modelo plano solo daba para uno.

## Decisión

### 1. Una columna, no una tabla

`categoria` gana `padre_id uuid references categoria(id)`. Un árbol de categorías **es** una tabla
de categorías con padre. Una tabla `subcategoria` habría duplicado el slug, el nombre, la línea y
todas sus consultas sin ganar una sola invariante.

El tope es de **dos niveles bajo la línea**, y no es una limitación técnica —la columna aguanta lo
que sea—: es que el menú lateral pinta el árbol entero desplegable, y un tercer nivel de sangría no
cabe en un riel de 288 px. Un árbol que el menú no puede pintar es un árbol que el comprador no
puede recorrer. Lo defiende `ProfundidadDeCategoriaExcedidaException`.

### 2. `ROPA_Y_CALZADO` se parte, y el enum queda en cuatro

Es un cambio de nivel grueso de verdad, no una categoría disfrazada: ropa y calzado no comparten
talla, ni proveedor, ni criterio de empaque, y sobre todo no comparten lo que se declara en la guía
de envío — `ContenidoDeclarado` declaraba "Ropa y calzado deportivo" en la caja de una camiseta
sola. Que costara tocar el enum, las traducciones, las pruebas, el filtro y la portada es
exactamente la señal de que era un cambio de línea.

La regla de `LineaCatalogo` se mantiene igual de dura para lo que venga: **una línea nueva es
código; una categoría nueva es una fila**.

### 3. Un producto cuelga siempre de una hoja

Si "Camisas" tuviera productos y además subcategorías, "lo que hay en Camisas" tendría dos
respuestas —lo que cuelga directo y todo lo que hay debajo— y ninguna es la que espera quien hizo
clic en el menú. Se defiende por los dos lados: no se le dan hijas a una categoría con productos
(`CategoriaConProductosException`) ni productos a una categoría con hijas
(`CategoriaNoEsHojaException`). Juntas, ningún nodo intermedio llega a tener productos nunca.

### 4. El slug sigue siendo único global, con la ruta dentro

El filtro de la vitrina viaja por slug (`?categoria=ropa-dama-busos`), así que dos ramas con el
mismo slug serían dos ramas que el filtro no puede distinguir — y "Busos" existe bajo Dama y bajo
Caballero, "Dama" en tres líneas. El slug derivado lleva el del padre delante, por una regla
mecánica. `bolsos-dama-bolsos-de-mano` queda largo y se deja así: una excepción por estética obliga
a recordar cuál fue.

**Las ocho de tecnología conservan slug, id y línea.** Están en URLs publicadas, las afirma
`CategoriasDeTecnologiaTest` y las repite `CATEGORIAS_INCLUIDAS` de la skill. Solo cambia el nombre
visible de "Consolas" a "Consolas de videojuegos": el slug no se lee, se navega.

### 5. Las categorías vacías se muestran

`ListarCategorias` llamaba a `listarConProductosPublicados()` para no ofrecer un filtro que lleva a
una rejilla en blanco. El argumento era bueno mientras el catálogo era una lista plana; con el árbol
dejó de serlo. **Un menú que pinta "Ropa › Dama" y se salta "Faldas" porque hoy no hay ninguna le
está diciendo al comprador que no vendemos faldas**, que es una afirmación mucho más cara que una
rejilla vacía — y la rejilla ya sabe decir que no encontró nada con esos filtros.

Con eso desapareció la razón de ser de `ListarCategoriasAdmin`, que existía solo para que el panel
viera lo que la vitrina escondía. Un caso de uso menos. La decisión se propagó a los otros dos
sitios que filtraban por lo mismo: el selector de línea del filtro y las baldosas de la portada.

El equivalente de marcas **sí** sigue en pie: una marca sin productos no es una promesa de surtido.

### 6. El árbol inicial es migración; lo que venga después, panel

`V63` carga las treinta categorías, porque es dato real que toda instalación necesita — el mismo
criterio de `V38` y `V54`. Lo que aquel razonamiento no cubría es el caso de después: la
subcategoría que pide el proveedor del lunes. Obligar a escribir SQL y desplegar para poder cargar
un producto convierte un dato operativo en un cambio de esquema (`ADR-0047`). De ahí el CRUD del
panel: `CrearCategoria`, `EditarCategoria`, `EliminarCategoria`.

Sin borrado en cascada. Desde el panel, "Dama" y "Faldas" se ven igual de borrables, y "Dama"
arrastra nueve.

## Consecuencias

- El filtro de categorías de la vitrina ofrece **solo hojas**, etiquetadas con su ruta completa
  ("Ropa › Dama › Camisas"). Sin la ruta el desplegable tiene entradas que no se distinguen.
- Los desplegables de crear y editar producto hacen lo mismo, por el mismo motivo.
- Los nombres de las líneas subieron al diccionario **raíz** de i18n: los leen cinco sitios y tres
  de ellos no cargan el scope de catálogo.
- Los nombres de categoría siguen saliendo de la base de datos, en español, también en la versión en
  inglés del sitio. Es lo que ya pasaba con las categorías y las marcas; queda como pendiente
  conocido y no como regresión.
- `SembradorCatalogo` dejó de crear categorías: las **busca**, igual que `V62` hizo con "Celulares".

## Alternativas descartadas

**Dejar las cuatro raíces como categorías y borrar el enum.** Es lo que la propia doctrina de
`LineaCatalogo` sugiere —"lo que se pueda modelar como fila no se modela como enum"— y se descartó
por lo que la línea decide fuera del catálogo: `ContenidoDeclarado` la mapea a lo que se declara en
la guía de envío y `PoliticaContraentrega` recibe un `Set<LineaCatalogo>`. Un `switch` exhaustivo
sin `default` es lo que obliga a decidir la etiqueta de una línea nueva antes de que compile; con
filas, esa pregunta no se hace sola.

**Unicidad de slug por padre, con la ruta en la URL.** Habría dado slugs cortos
(`?categoria=ropa/dama/busos`), y exigía cambiar el parámetro del filtro, el `Slug` del dominio —que
prohíbe la barra— y las URLs ya publicadas. Se pagó el slug largo.
