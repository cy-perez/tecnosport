export type Rol = 'CLIENTE' | 'ADMIN';

export interface Sesion {
  readonly usuarioId: string;
  readonly rol: Rol;
  readonly accessToken: string;
  /**
   * El token es de alcance restringido (ADR-0096): un `ADMIN` sin segundo factor mientras el
   * servidor lo exige. Con él solo se puede activar el factor; el resto del panel responde 403, así
   * que el guardia del panel lleva a la activación antes que a ninguna otra parte.
   */
  readonly segundoFactorPorActivar: boolean;
}

/**
 * Lo que devuelve el primer paso del inicio de sesión: la sesión abierta, o el desafío del segundo
 * factor cuando la cuenta lo tiene activo (202, sin cookie ni token). El `desafio` vive solo en
 * memoria de la pantalla: no es una credencial que valga sin el código, pero tampoco tiene por qué
 * sobrevivir a la pestaña.
 */
export type ResultadoInicioSesion =
  | { readonly tipo: 'sesion'; readonly sesion: Sesion }
  | { readonly tipo: 'segundo-factor'; readonly desafio: string; readonly venceEn: string };
