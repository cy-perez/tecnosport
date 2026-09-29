/** Las redes en que el panel puede difundir. Refleja el enum `RedSocial` del backend. */
export type RedSocial = 'FACEBOOK' | 'INSTAGRAM';

export const REDES: readonly RedSocial[] = ['FACEBOOK', 'INSTAGRAM'];

/**
 * En qué quedó una difusión.
 *
 * `PENDIENTE` no es "cargando": es una constancia que quedó sin resolver porque el proceso se cayó
 * entre la llamada a Meta y su respuesta. En la ficha se enseña distinto de las otras dos, porque
 * significa "no sabemos si salió" y lo único sensato es mirar la cuenta antes de reintentar.
 */
export type EstadoPublicacion = 'PENDIENTE' | 'PUBLICADA' | 'FALLIDA';

/** Una difusión que ocurrió, con el pie tal como se publicó ese día. */
export interface PublicacionEnRed {
  readonly id: string;
  readonly red: RedSocial;
  readonly estado: EstadoPublicacion;
  /** El id del post en la red, cuando salió. Sirve para volver a él. */
  readonly idPublicacionExterna: string | null;
  readonly pieDeFoto: string;
  readonly urlImagen: string;
  readonly solicitadaEn: string;
  readonly publicadaEn: string | null;
  readonly detalleDelFallo: string | null;
}

/** Lo que el panel manda al difundir. Sin `pieDeFoto`, el servidor arma el propuesto. */
export interface OrdenDeDifusion {
  readonly productoId: string;
  readonly redes: readonly RedSocial[];
  readonly pieDeFoto?: string;
}
