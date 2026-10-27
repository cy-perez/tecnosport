# ADR-0096: el panel lleva segundo factor TOTP, sin dependencias

Fecha: 2026-10-27
Estado: aceptada.
Relacionados: `docs/08-seguridad-legal.md` (la línea que pedía segundo factor antes de abrir
producción), `adr/0087` (la IP del cliente), `HuellaDeToken` (`V140`), `LimitadorDeIntentos`.

## Contexto

El panel se abre con correo y clave. El correo del administrador es el mismo público de contacto,
el ambiente de dev es público y opera la cuenta real de Instagram y credenciales productivas de
Sistecrédito. `docs/08` dejó escrito que el panel tiene que llevar segundo factor antes de abrir
producción. Una clave filtrada —reutilizada en otro sitio, pescada con un correo falso, leída por
encima del hombro— hoy basta para entrar a todo.

## Decisión

1. **TOTP de la RFC 6238 con HMAC-SHA1, seis dígitos y treinta segundos**, que es lo único que
   entienden todas las aplicaciones de autenticación. Calculado en el dominio (`Totp`, `Base32`,
   `ClaveTotp`) con `javax.crypto.Mac` del JDK: **sin dependencias nuevas**. `ArquitecturaTest`
   deja `javax.crypto` en `domain` y en ninguna otra capa pura.
2. **Sin QR.** La clave se muestra en Base32 agrupada y como URI `otpauth://`, que el teléfono
   abre o la persona pega. Pintar un QR pedía una librería o un generador escrito a mano, y la
   clave en texto cumple igual.
3. **Ventana de un paso a cada lado** (±30 s), y un código aceptado no se acepta otra vez ni uno
   más viejo que él (`ultimoPasoUsado`). La confirmación de la activación también lo fija.
4. **Diez códigos de respaldo** de 80 bits, mostrados una sola vez, guardados como SHA-256
   (`HuellaDeToken.de(String)`), un solo uso cada uno. Usar uno avisa por correo al titular con
   los que le quedan.
5. **La clave TOTP se guarda cifrada con AES-256-GCM** —IV de 12 bytes, etiqueta de 128 bits, el
   id del usuario como dato asociado, formato `v1:` + Base64(iv‖cifrado)— **dentro del adaptador
   de persistencia, sin puerto**: el cifrado es cómo se guarda, no una operación del negocio. La
   llave viene de `SEGUNDO_FACTOR_LLAVE` (32 bytes en Base64), no tiene valor por omisión fuera
   de `local` y la API no arranca sin ella. El dato asociado impide copiar la fila cifrada de una
   cuenta a otra.
6. **Inicio de sesión en dos pasos.** Clave correcta y correo verificado en una cuenta con el
   factor activo responden **202** con un desafío de cinco minutos (`desafio`, `venceEn`) y **sin
   cookie ni token**. `POST /auth/segundo-factor/verificacion` con el desafío y el código abre la
   sesión. El desafío se guarda por su huella, como los tokens de recuperación.
7. **Topes del segundo paso con `LimitadorDeIntentos`, no con columnas.** El controlador abre la
   transacción y un intento fallido que escribiera filas se revertiría al lanzar; el limitador
   cuenta en su propia transacción. Tres llaves: cinco por desafío (agotado, el desafío muere y
   hay que volver a escribir la clave), cinco por cuenta e IP y diez por cuenta sola cada quince
   minutos. Configurables.
8. **Bandera `ADMIN_EXIGE_SEGUNDO_FACTOR`**, `false` por omisión y `true` en dev. Un ADMIN sin
   factor con la bandera encendida entra con una sesión de **alcance restringido**: el token de
   acceso lleva `alcance: SOLO_ACTIVAR_SEGUNDO_FACTOR`, el filtro le da `ROLE_ADMIN_POR_ACTIVAR`
   en vez de `ROLE_ADMIN`, y con eso `/api/v1/admin/**` le responde 403 y solo
   `/api/v1/auth/segundo-factor/**` le abre. El alcance se recalcula en cada emisión del token.
   Sin la bandera, activarlo es opcional y desactivarlo también.
9. **Activar, desactivar y regenerar revocan todas las sesiones del usuario** y abren una nueva,
   en el orden de `CambiarClave`. Las tres piden la clave actual; desactivar y regenerar piden
   además un código.
10. **Sin "recordar este equipo".** Cada inicio de sesión pide el código.
11. **Recuperar la clave no desactiva el factor.** Quien perdió la clave y la recupera por correo
    sigue necesitando el código: el correo no es un segundo factor del correo.

## Alternativas descartadas

- **Una librería de TOTP.** Son cien líneas con vectores de prueba de la RFC; la librería es
  deuda y otra superficie.
- **Correo o SMS como segundo factor.** El correo es el mismo canal que recupera la clave; el SMS
  cuesta, depende de un tercero y se secuestra con un cambio de SIM.
- **Columnas de intentos en `segundo_factor`.** Se revierten con la transacción del intento
  fallido: el contador no sube nunca.
- **Un puerto `CifradorDeSecretos` en `application`.** Ningún caso de uso cifra ni descifra nada;
  el repositorio devuelve el agregado con la clave en claro y la guarda cifrada.

## Consecuencias

- **El token de acceso vive quince minutos después de revocar** las sesiones. Es el riesgo que ya
  se aceptó con el JWT sin revocación.
- **La primera activación la protege solo la clave**: quien tenga la clave antes que el dueño
  puede activar su propio factor. Con la bandera encendida, la cuenta queda en sus manos de todas
  formas; la salida es `infra/operacion/restablecer-segundo-factor.sql`.
- **Perder la llave AES es perder todos los factores.** El mismo procedimiento SQL los borra y
  obliga a activarlos de nuevo. La llave no se rota sin migrar las filas; el prefijo `v1:` deja
  sitio para una versión siguiente.
- **Montar el secreto antes del código.** `SEGUNDO_FACTOR_LLAVE` es obligatoria: un despliegue sin
  ella no arranca. El orden es infraestructura, secreto y código (`docs/07`).
- Las herramientas que inician sesión contra el panel (`tools/cargar-catalogo.mjs`,
  `tools/huerfanos-bucket.mjs`) aceptan `--codigo` o `TS_CODIGO_2FA`; sin código ante un 202,
  fallan diciendo cómo pasarlo.
