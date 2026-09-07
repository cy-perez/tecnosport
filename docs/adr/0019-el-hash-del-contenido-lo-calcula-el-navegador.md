# ADR 0019. El hash del contenido de una imagen lo calcula el navegador

Fecha: 2026-09-06. Estado: aceptada.

## Contexto

`docs/02-modelo-datos.md` describe `imagen_producto.hash` como "el hash del
contenido para detectar recargas duplicadas del mismo archivo", y la columna se
dimensionó para eso: `varchar(80)`, holgado para los 64 caracteres de un SHA-256
en hexadecimal.

El código no hacía eso. Tanto `ConfirmarImagenPrincipal` (Fase 4) como
`CompletarSetRotacion` (Fase 5) guardaban ahí la **key del objeto en Cloud
Storage**, con un comentario que lo justificaba: un hash del contenido real lo
puede calcular el navegador, pero el backend no podría verificarlo sin descargar
los bytes.

Dos problemas, y el primero es de bulto:

1. **No cabía.** Una key de rotación mide unos 99 caracteres
   (`productos/{uuid}/rotacion/{uuid}/0.webp`) y la de una imagen principal unos
   98. Contra la base de datos real, `completar` y `confirmar` respondían 500 con
   `value too long for type character varying(80)`. Nunca funcionaron contra
   Cloud Storage; las pruebas no lo veían porque el doble de prueba del almacén
   acepta cualquier key y las pruebas usaban keys cortas. Se encontró recorriendo
   los endpoints a mano contra `bootRun` y un bucket de verdad.
2. **No servía para lo que la columna existe.** Cada key la arma el servidor con
   el id del set y el orden del fotograma, así que es única por construcción: dos
   subidas del mismo archivo producen keys distintas. La detección de duplicados
   que el modelo promete no podía funcionar nunca.

## Decisión

El hash lo calcula el **cliente**, sobre los bytes que acaba de subir, y lo manda
en el cuerpo de `completar` y de `confirmar`. En el navegador es
`crypto.subtle.digest('SHA-256', …)`, que exige contexto seguro (HTTPS o
`localhost`) — el mismo requisito que la cámara y el sensor de orientación del
asistente de captura ya imponen.

El servidor **no puede verificar que ese hash corresponda al archivo** sin
descargarlo, que es justo lo que la subida directa evita. Lo que sí hace, en tres
lugares:

- `HashContenido`, objeto de valor en `domain/compartido` junto a
  `CorreoElectronico`, exige 64 hexadecimales y normaliza a minúscula.
- Un `check` en el esquema (`V19`) repite la exigencia, para que una key colada
  ahí reviente en el `insert` y no cuatro capas más arriba.
- El tipo del dominio hace imposible pasar un `String` cualquiera: no compila.

Es el mismo compromiso que ya aceptó `ADR-0016` para el tipo y el tamaño del
archivo: el cliente declara, el servidor valida la forma y confía en el resto,
acotado porque estos endpoints exigen rol `ADMIN`.

## Alternativas descartadas

**Ensanchar la columna a 200 y seguir guardando la key.** Un `alter table` y
listo. Descartada porque entierra el problema en vez de resolverlo: el modelo
seguiría diciendo que ahí va un hash de contenido, y la detección de recargas
duplicadas seguiría sin funcionar. Un documento que miente es peor que no
tenerlo.

**Eliminar la columna.** Coherente si la detección de duplicados no se va a usar
nunca, y más honesto que llenarla de datos que no son lo que el nombre dice. Se
descartó porque el costo de calcular el hash en el cliente es despreciable —los
bytes ya están en memoria— y perder esa red es definitivo.

**Que el backend descargue y hashee.** Cumpliría la promesa al pie de la letra, y
por las mismas razones de `ADR-0018` no se hace: convierte al backend en
intermediario de 8 a 16 imágenes por set. Si algún día hace falta de verdad, el
lugar es una función disparada por el evento de subida del bucket, que puede
recalcular el hash y contrastarlo con el declarado.

## Consecuencias

- La subida de un set de rotación y la de la imagen principal funcionan contra
  Cloud Storage real, verificado de punta a punta.
- Las imágenes anteriores a este cambio no tienen hash de contenido y no hay cómo
  calculárselo: `V19` lo deriva del identificador que tenían. Queda bien formado
  y sigue siendo único, pero no es el hash de nada — si alguna vez se usa para
  deduplicar de verdad, esas filas no van a coincidir con ninguna subida nueva, y
  eso es correcto.
- Un cliente malicioso con rol `ADMIN` puede mandar un hash que no corresponda al
  archivo. No gana nada con eso hoy: el hash no autoriza ni identifica nada, solo
  serviría para envenenar una futura deduplicación.
- La deduplicación en sí sigue sin existir: ahora el dato para hacerla es el
  correcto, pero nadie lo consulta todavía.
