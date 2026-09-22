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
