# ADR 0016. Subida de imagen principal: sin verificar el contenido real ni el tamaño

Fecha: 2026-09-05. Estado: aceptada, con riesgo pendiente de revisar.

## Contexto

`docs/08-seguridad-legal.md` ya prometía, desde antes de esta fase: "Subida
de imágenes: tipo verificado por contenido y no por extensión, tamaño
máximo, nombre regenerado, servidas desde el dominio de estáticos." El primer
caso de uso real de subida (imagen principal, Track B paso 5) no cumple esa
frase al pie de la letra:

- El tipo se verifica contra una lista blanca de `Content-Type`
  (`image/jpeg`, `image/png`, `image/webp`) que declara el propio cliente al
  pedir la URL firmada — no se inspeccionan los bytes reales del objeto
  subido para confirmar que el contenido coincide con ese tipo.
- No hay un tamaño máximo propio: `SolicitarSubidaDeImagenPrincipal` no
  limita el tamaño, y `ConfirmarImagenPrincipal` solo verifica que el objeto
  exista y registra su tamaño real, sin rechazarlo por ser demasiado grande.
- El nombre sí se regenera (`GeneradorIdentificador.nuevo()`, nunca el
  nombre original del archivo) y las imágenes sí se sirven desde el dominio
  de estáticos (Cloud Storage/CDN) — esas dos partes de la frase original sí
  se cumplen.

## Decisión

Se acepta el hueco por ahora, acotado por el contexto de este endpoint
específico: `POST /api/v1/admin/productos/{id}/imagen-principal[/url-subida]`
exige sesión con rol `ADMIN` — no es una superficie anónima ni de cliente.
Un archivo mal etiquetado o pesado que suba un administrador es un problema
operativo (una imagen que se ve mal, un bucket que crece), no la misma
superficie de ataque que una subida anónima sin autenticar.

`docs/08-seguridad-legal.md` se corrige para reflejar la realidad: la
verificación por contenido y el tamaño máximo quedan como objetivo pendiente
de este endpoint, no como algo ya cumplido.

## Alternativas

**Verificar el contenido real (magic bytes) antes de confirmar**: más
correcto, pero exige leer el objeto completo desde Cloud Storage al backend
para inspeccionarlo — contradice la decisión ya tomada de que "el navegador
sube los bytes con un PUT directo a Cloud Storage, el backend nunca los
toca" (docs/03-api.md). Habría que descargar el objeto solo para
inspeccionarlo, lo que anula buena parte del ahorro de la URL firmada. Queda
como opción si el riesgo resulta inaceptable: se puede hacer sin traer el
objeto entero, leyendo solo los primeros bytes con un `Range`, pero no se
construyó en esta pasada.

**Poner un tamaño máximo ya, aunque el número sea arbitrario**: se descartó
por la regla dura de no inventar datos de negocio — un límite de peso real
depende de con qué van a fotografiar el catálogo (celular, cámara réflex) y
no está decidido todavía. Queda como `TODO` explícito en vez de un número
inventado.

## Consecuencias

Un administrador (la única cuenta que puede llegar a este endpoint hoy)
podría subir un archivo cuyo contenido no coincida con el `Content-Type`
declarado, o un objeto arbitrariamente grande, sin que el backend lo
rechace. El riesgo se acepta mientras el panel solo lo use el propio
operador del negocio. Si el panel alguna vez delega esta subida a alguien
fuera de máxima confianza, o cuando el asistente de captura de la Fase 5
reutilice el mismo mecanismo para un flujo más automatizado, hay que resolver
este ADR antes: fijar un tamaño máximo real (con el dato de negocio que
falta) y decidir si vale la pena la verificación por contenido.
