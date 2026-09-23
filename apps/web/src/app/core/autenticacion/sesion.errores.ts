/** Credenciales correctas, pero la cuenta no verificó su correo todavía (403, `docs/08-seguridad-legal.md`). */
export class CorreoSinVerificarError extends Error {}

/**
 * La clave actual que se escribió no es la de la cuenta (401 con código `CREDENCIALES_INVALIDAS`
 * en `POST /auth/clave`). Tipo propio y no un `ErrorHttp` a secas porque la pantalla tiene que
 * distinguirlo de un 401 por sesión vencida: el primero se corrige escribiendo bien, el segundo
 * volviendo a entrar.
 */
export class ClaveActualIncorrectaError extends Error {}

/** Demasiados intentos seguidos contra la misma cuenta (429, `docs/08-seguridad-legal.md`). */
export class DemasiadosIntentosError extends Error {}

/**
 * El token de acceso no sirve o ya vencio (401 con codigo `NO_AUTENTICADO`). Comparte el
 * codigo HTTP con {@link ClaveActualIncorrectaError} y por eso los dos se distinguen por el
 * `codigo` del cuerpo y no por el estado: decirle "esa no es tu clave" a quien la escribio
 * bien, porque lo que vencio fue la sesion, manda a buscar un problema que no existe.
 */
export class SesionExpiradaError extends Error {}
