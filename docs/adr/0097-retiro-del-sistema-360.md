# ADR-0097: se retira el sistema 360 —el visor de la ficha y la captura desde el teléfono—

Fecha: 2026-10-27
Estado: aceptada. Reemplaza a `adr/0008` (rotación 360 con captura y procesamiento en el cliente)
y a `adr/0018` (el set de rotación como agregado propio).
Relacionados: `docs/09-plan-de-arranque.md` (la Fase 5, que queda como historia),
`docs/02-modelo-datos.md`, `docs/08-seguridad-legal.md` (la `Permissions-Policy`).

## Contexto

La Fase 5 construyó el sistema 360 entero: un asistente en el panel que guiaba la captura desde el
teléfono —cámara, silueta, nivelador con los sensores de orientación, recorte y escala en el
navegador— y subía los fotogramas con URL firmadas; un agregado `SetRotacion` con su ciclo
`BORRADOR → COMPLETO → PUBLICADO`; y un visor en la ficha que giraba el producto con el dedo, el
ratón o el teclado.

El negocio decidió no usarlo. El catálogo se arma con las fotos de estudio que retoca
`fotos-estudio-degradado` y con las que llegan por la ingesta de proveedores (`adr/0067`,
`adr/0075`); el asistente no entró en el flujo de trabajo. Mantenerlo vivo no es gratis: son
más de ochenta archivos entre la web y la API, un scope de traducciones, una tabla, cinco
endpoints del panel, una herramienta para reproducir grabaciones del sensor, permisos de cámara y
sensores abiertos en todo el sitio, y una bifurcación en la ficha, que tenía que elegir entre el
visor y la galería cuál era la imagen prioritaria.

## Decisión

Se retira todo lo del sistema 360, en un solo PR y un commit por capa:

1. **La ficha sin visor.** Fuera `ts-visor-360`, el campo `rotacion` de `Producto` en el
   frontend y el bloque `visor360` de las traducciones. **La galería es siempre la imagen
   principal de la ficha y la candidata a LCP**, sin rama condicional.
2. **El panel sin asistente de captura.** Fuera `features/captura360/`, su ruta, su scope de
   Transloco y la opción "capturar" de la lista de productos.
3. **La API sin el agregado.** Fuera `SetRotacion`, `EstadoSetRotacion`, sus casos de uso, su
   puerto y su adaptador, `TipoImagen.ROTACION`, el campo `rotacion` de `ProductoRespuesta` y los
   cinco endpoints de `/api/v1/admin/sets-rotacion`. `Producto` y `Variante` dejan de cargar un
   set. El contrato OpenAPI y el cliente generado se regeneran.
4. **La base sin la tabla.** La `V147` borra las filas de `imagen_producto` de tipo `ROTACION`
   —en cascada sus anchos de `imagen_variante`—, suelta `imagen_producto.set_rotacion_id` y su
   índice, y hace `drop table set_rotacion`.
5. **La `Permissions-Policy` apaga lo que ya nadie pide.** `camera`, `accelerometer`,
   `gyroscope`, `magnetometer` y `screen-wake-lock` pasan de `(self)` a `()`. `clipboard-write`
   sigue en `(self)`: lo usa el botón de copiar.
6. **Fuera las herramientas del nivel.** `tools/analizar-nivel-360.mjs`,
   `tools/sonda-nivel-360.mjs`, el script `npm run nivel-360` y las variables
   `ROTACION_FOTOGRAMAS_PREDETERMINADO` y `ROTACION_TOLERANCIA_GRADOS` del `.env.example`, que
   nadie leía.
7. **Lo del sistema visual que solo usaba el 360**: el ancho mínimo del contador del visor en
   `tokens.json` (regenerado con el kit, no a mano) y el espaciado del trazo de la guía de
   captura.

## Lo que se queda

- **`superficie-arrastre`**, la utilidad que hace arrastrable una superficie: nació para el visor
  y hoy la usa el carrusel de la portada.
- **El versionado del bucket de imágenes.** Se justificaba con el borrado de un set, pero quitar
  una imagen de la galería o reemplazar la principal también borra objetos, y la red sigue
  haciendo falta.
- **La historia.** `adr/0008` y `adr/0018` no se borran: pasan a reemplazadas. La Fase 5 de
  `docs/09-plan-de-arranque.md` se queda con una nota al inicio, y los ADR que nombran el set de
  paso (`0009`, `0019`, `0053`, `0057` y otros) se dejan como están, porque cuentan lo que era
  cierto el día en que se escribieron. `docs/10-captura-360.md` sí se borra: era la especificación
  de algo que ya no existe, no un registro de una decisión.
- **Las lecciones de prueba** que dejó la Fase 5 en `docs/06-testing.md` —la mutación que
  destapó una prueba del visor que pasaba con el defecto, el espía de `localStorage` que no se
  instalaba, el número fijo de vueltas— siguen escritas, marcadas como de un código retirado.

## Fuera de este cambio

- **Los objetos `productos/*/rotacion/` de los buckets.** Borrarlos es irreversible pasado el
  plazo del versionado, y se decide aparte. `npm run huerfanos` los informa ahora como huérfanos
  —antes eran "no juzgables", porque un set sin publicar no se veía desde ninguna API—.
- **El texto legal** que menciona "los visores de rotación" (`scopes/legales`). Se cambia con la
  skill de textos legales y en su propio PR, no a mano.

## Alternativas

**Dejarlo apagado detrás de una bandera.** Conserva el trabajo, pero el costo de mantenerlo es el
mismo: las pruebas, el contrato, la tabla y los permisos siguen ahí, y un código que nadie usa se
pudre sin que ninguna prueba lo diga. Si algún día vuelve, la Fase 5 y estos ADR dicen cómo se
hizo y qué se aprendió, y el código está en la historia de git.

**Quitar solo el asistente y dejar el visor** para sets cargados de otra forma. Ningún proveedor ni
proceso produce sets de rotación, así que el visor no tendría qué mostrar.

## Consecuencias

- La ficha tiene un solo camino para la imagen principal, y la LCP es siempre la de la galería.
- El sitio deja de pedir permiso, ni siquiera en potencia, para cámara y sensores.
- Desaparece del contrato `ProductoRespuesta.rotacion`. La web y el cliente generado cambian en el
  mismo PR; la app móvil todavía no existe, así que no hay otro cliente que romper.
- Volver a tener un 360 sería un proyecto nuevo: migración, agregado, endpoints y pantallas, con
  la Fase 5 como punto de partida y no como algo que se reactiva.
