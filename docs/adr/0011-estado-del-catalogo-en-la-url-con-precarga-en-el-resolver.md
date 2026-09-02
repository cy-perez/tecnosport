# ADR 0011. Estado del catálogo en la URL, con precarga en el resolver de ruta

Fecha: 2026-09-02. Estado: aceptada.

## Contexto

La vitrina (rejilla, filtros, ficha) usa TanStack Query para todos los datos
remotos, con SSR. Durante la construcción se encontraron dos problemas
relacionados, los dos empíricos, no previstos en el diseño original:

1. **¿Dónde vive el filtro elegido (categoría, marca, línea, precio, texto,
   orden)?** Guardarlo en una señal del componente es lo más simple, pero
   hace que la URL de la rejilla filtrada no sea compartible ni sobreviva un
   refresh, y que "volver atrás" del navegador no funcione.
2. **Condición de carrera de SSR con `injectInfiniteQuery`/`injectQuery`.**
   El adaptador de Angular de TanStack Query integra sus consultas con
   `PendingTasks` (el mecanismo que le dice a Angular SSR "espera, hay algo
   pendiente antes de serializar la página") — pero ese registro ocurre
   dentro de un `effect()`, que Angular agenda de forma asíncrona, no
   sincrónica con el primer render del componente. El resultado, verificado
   con `curl` contra un `bootRun` real: según qué tan rápido responda el
   backend, la misma página a veces serializa con los datos ya cargados y a
   veces con los *esqueletos* de carga — nunca un error, pero tampoco
   determinista. Pasó primero con la rejilla, después otra vez con las
   opciones de categoría/marca de los filtros, y otra vez con la ficha: el
   mismo bug, cada vez que se agregó una consulta nueva a una ruta.

## Decisión

**El filtro completo vive en los query params de la URL**
(`?categoria=&marca=&linea=&precioMin=&precioMax=&texto=&orden=`), nunca en
una señal de componente. `domain/query-params-filtro.ts` tiene las funciones
puras de conversión en los dos sentidos
(`filtroDesdeQueryParams`/`queryParamsDesdeFiltro`), compartidas entre la
página y el resolver de la ruta — convertir en un solo lugar evita que los
dos conviertan distinto y se desincronicen. El selector de idioma ya seguía
este mismo criterio (navegar en vez de mutar estado local, docs/05-i18n.md);
esto lo extiende a cualquier estado que deba ser parte de la URL.

**Toda ruta que lee una consulta de TanStack Query la precarga primero en su
`resolve`**, con `queryClient.prefetchQuery`/`prefetchInfiniteQuery` (que
nunca lanzan: si el backend falla, el prefetch no rompe la navegación, y el
componente igual reintenta y muestra su propio estado de error). El resolver
y el componente arman las mismas `queryKey`/`queryFn`/`staleTime` con la
misma función de opciones (`opcionesBusqueda`, `opcionesFicha`,
`opcionesCategorias`/`opcionesMarcas`) — nunca las escriben dos veces, para
que el resolver caliente exactamente la consulta que el componente va a
pedir. Con la caché ya tibia antes de que el componente exista, la
serialización de SSR ya no compite con un `effect()` asíncrono.

Ejemplos: `application/buscar-productos.consulta.ts`
(`precargarProductos`), `application/buscar-ficha-producto.consulta.ts`
(`precargarFichaProducto`), `application/listar-opciones-filtro.consulta.ts`
(`precargarOpcionesFiltro`), todos invocados desde `catalogo.routes.ts`.

## Alternativas

- **Hidratación SSR de TanStack Query** (`dehydrate`/`hydrate`, transferir el
  estado de la consulta al cliente) resolvería el mismo síntoma de otra
  forma, pero no ataca la causa: seguiría existiendo una ventana en la que
  Angular podría serializar antes de que la consulta resuelva la primera
  vez. Sigue pendiente como optimización aparte (evitar el refetch al
  hidratar), pero no es la corrección de esta condición de carrera.
- **Estado del filtro en un servicio con señales, sincronizado a la URL
  aparte** (en vez de que la URL sea la única fuente de verdad): más
  superficie para que los dos se desincronicen, sin beneficio real dado que
  Angular Router ya expone el estado de la URL como observable.

## Consecuencias

Cada consulta nueva de una página (no de un componente hijo interactivo) que
se agregue a una ruta de la vitrina necesita su propio `precargarX()` y su
entrada en el `resolve` de esa ruta — omitirlo reintroduce el mismo bug de
*skeletons* intermitentes, silenciosamente, porque en local con el backend
tibio casi nunca se nota. Detalle operativo en `apps/web/CLAUDE.md`.
