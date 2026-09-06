# ADR 0015. Un agregado de token distinto por cada nivel de sensibilidad

Fecha: 2026-09-04. Estado: aceptada.

## Contexto

La Fase 4 necesitaba dos mecanismos de token de un solo uso: confirmar un
correo al registrarse, y autorizar el cambio de contraseña al recuperarla.
Los dos comparten forma (token opaco, un solo uso, vence a los N
minutos/horas, ligado a un `Usuario`), lo que abre la pregunta de si conviene
un solo agregado genérico (`TokenDeUnSoloUso` con un campo `tipo`) o dos
agregados separados.

## Decisión

Dos agregados separados: `TokenVerificacionCorreo`
(`V14__verificacion_correo.sql`) y `TokenRecuperacionClave`
(`V16__recuperacion_clave.sql`), cada uno con su propia tabla, su propio
vencimiento configurable (`VERIFICACION_CORREO_HORAS_VENCIMIENTO`,
`RECUPERACION_CLAVE_MINUTOS_VENCIMIENTO`) y su propio puerto.

Además, confirmar la recuperación revoca **todas** las sesiones de refresco
del usuario (`RepositorioSesiones.revocarTodasDeUsuario`), no solo la familia
de la sesión que originó la solicitud — a diferencia de la detección de
reutilización de refresco (`docs/08-seguridad-legal.md`), que sí revoca solo
una familia.

## Alternativas

**Un solo `TokenDeUnSoloUso` con un campo `tipo`**: menos tablas y menos
código repetido (generar, consumir, expirar es idéntico en los dos). Se
descartó porque confirmar un correo y autorizar un cambio de contraseña son
sensibilidades distintas — un bug que confundiera el tipo al validar
(aceptar un token de verificación para cambiar la clave) sería un agujero de
seguridad real, y separar las tablas hace ese bug estructuralmente
imposible en vez de depender de una validación de `tipo` que alguien podría
olvidar.

**Revocar solo la familia de sesión actual al recuperar la clave** (mismo
criterio que la detección de reutilización de refresco): se descartó porque
perder el control de la clave es un evento más grave que un refresco
reutilizado — si alguien más conocía la clave vieja, pudo haber abierto
sesión en otro dispositivo, y esa sesión no debe sobrevivir al cambio de
clave.

## Consecuencias

Dos migraciones, dos puertos, dos casos de uso de "consumir" casi idénticos
en forma — duplicación aceptada a cambio de que un error de tipo no pueda
ocurrir. Si en el futuro aparece un tercer token de un solo uso con la misma
sensibilidad que alguno de estos dos, conviene reconsiderar una base común
(clase abstracta o tabla compartida con `tipo`) en vez de seguir
duplicando.

Un cambio de contraseña por recuperación cierra sesión en todos los
dispositivos del usuario, incluidos los legítimos — un costo de fricción
aceptado a cambio de la garantía de seguridad.
