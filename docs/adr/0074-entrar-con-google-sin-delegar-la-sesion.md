# ADR-0074 — Entrar con Google sin delegarle la sesión

**Fecha:** 2026-10-08
**Estado:** aceptado. Cambia lo que `docs/08-seguridad-legal.md` decía de Google:
"no en la fase 1. El diseño lo permite después sin migración". Lo segundo era
falso: `usuario.clave_hash` era `NOT NULL` y el agregado exigía una clave.

## Contexto

El negocio pidió que quien compra pueda crear su cuenta y entrar con su cuenta de
Gmail. La autenticación del sitio es propia (`docs/08`): un token de acceso de 15
minutos que vive en memoria y una cookie de refresco `HttpOnly` con rotación y
detección de reúso. Nada de eso tenía que cambiar para sumar una forma más de
probar quién es la persona.

## Decisión

1. **Google prueba la identidad; la sesión sigue siendo nuestra.** El navegador
   pinta el botón oficial de Google Identity Services y recibe un ID token. El
   servidor lo verifica en `POST /api/v1/auth/google` y abre **la misma sesión**
   que `POST /auth/sesion`: el mismo token, la misma cookie (`SesionConCookie`).
   No hay `oauth2Login` de Spring, ni sesión de servidor, ni token de Google
   guardado.
2. **Sin dependencias nuevas.** El token se verifica con el `NimbusJwtDecoder` que
   ya trae `spring-security-oauth2-jose`, contra las llaves públicas de Google y
   exigiendo emisor, audiencia y vencimiento. Comprobado en el jar 7.1.0 antes
   de escribirlo (regla 9). En el frontend, el script de Google se carga solo en
   las dos pantallas que lo usan, detrás de un puerto (`BOTON_GOOGLE`).
3. **Una cuenta, tres caminos** (`IniciarSesionConGoogle`):
   - si ya está unida a esa cuenta de Google (por el `sub`, que no cambia aunque
     cambie el correo), entra;
   - si hay una cuenta con ese correo creada con clave, se une y queda
     verificada, porque Google solo entrega correos verificados (si no lo
     estuviera, la credencial se rechaza);
   - si no hay cuenta, se crea ya verificada, **solo si se autorizó el
     tratamiento de datos** (Ley 1581 de 2012). Desde «Iniciar sesión» no se
     autoriza nada: el servidor responde `409 CUENTA_GOOGLE_SIN_REGISTRO` y el
     sitio manda a «Crear cuenta», donde está la casilla. La constancia se
     guarda con origen `GOOGLE`.
4. **El panel no entra con Google.** Una cuenta `ADMIN` recibe el mismo error que
   una credencial mala, y su correo no se toma para una cuenta nueva. El
   administrador tiene su propia puerta, con clave.
5. **Una cuenta puede no tener clave.** `clave_hash` admite nulo, con un `CHECK`
   que exige al menos una forma de entrar (`V90`). Iniciar sesión con clave en
   una cuenta sin clave es el error genérico, sin revelar que existe. Quien
   quiera una clave la pide por «¿Olvidaste tu contraseña?».
6. **Apagado por omisión.** Sin `GOOGLE_CLIENT_ID`, el sitio no pinta el botón y
   el endpoint responde 404. El identificador no es secreto, pero es de un
   ambiente: Google comprueba el origen.

## Consecuencias

- **Falta crear el cliente OAuth** en Google Cloud Console: la pantalla de
  consentimiento y los orígenes autorizados de cada ambiente. Después hay que
  poner `GOOGLE_CLIENT_ID` en el ambiente. Hasta entonces la función existe y no
  se ve.
- **Política de datos versión `2026-10-08.2`:**
  - §5 dice qué entrega Google: correo, verificación e identificador, nunca la
    contraseña;
  - §8 nombra a Google como quien confirma la identidad, y dice que el botón se
    carga desde sus servidores;
  - la de cookies dice que Google puede usar allí sus propias cookies.
  - Es un sufijo y no otra fecha porque la versión del mismo día (recogida
    apagada, `ADR-0072`) puede llegar antes a producción, y las constancias de
    una y otra no pueden compartir versión.
- **Punto para abogado** (`docs/14`, 7): si cargar el botón de Google, que le
  entrega a Google la IP y sus cookies desde nuestras páginas, necesita algo
  más que el aviso.
