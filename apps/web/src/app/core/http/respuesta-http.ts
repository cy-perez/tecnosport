/**
 * Error de transporte o de servidor, con el código HTTP que lo causó. El mensaje es un diagnóstico
 * para quien programa, nunca un texto de pantalla: la presentación captura y traduce su propia
 * clave de Transloco (regla dura #4 de CLAUDE.md).
 */
export class ErrorHttp extends Error {
  constructor(
    readonly estado: number,
    detalle: string,
  ) {
    super(`${detalle} (HTTP ${estado})`);
    this.name = 'ErrorHttp';
  }
}

/**
 * Forma mínima del resultado de `openapi-fetch`. Se declara aquí, estructural, en vez de importar
 * su tipo genérico: lo único que estas dos funciones necesitan saber es que hay una `Response` y
 * puede haber `data`.
 */
interface ResultadoFetch<T> {
  data?: T;
  response: Response;
}

/**
 * Desempaqueta una respuesta que **sí** trae cuerpo, o lanza {@link ErrorHttp}.
 *
 * <p>Mira `response.ok` y no `error`, y esa es toda la razón de existir de este archivo:
 * `openapi-fetch` solo rellena `error` cuando el fallo trae cuerpo JSON. Un 500 vacío o en HTML
 * —lo que devuelve un balanceador cuando el backend no responde— dejaba `error` en `undefined`, el
 * `if (error)` no disparaba, y `data` llegaba `undefined` hasta el mapeador: el llamador recibía un
 * `TypeError` en vez del error real. Estaba en 31 sitios de 11 adaptadores.
 *
 * <p>`data === undefined` se comprueba además del código: un 200 al que le falte el cuerpo es un
 * fallo igual, y sin esa guarda el `TypeError` volvería por la otra puerta.
 */
export function desempaquetar<T>(resultado: ResultadoFetch<T>, detalle: string): T {
  if (!resultado.response.ok || resultado.data === undefined) {
    throw new ErrorHttp(resultado.response.status, detalle);
  }
  return resultado.data;
}

/**
 * Para las respuestas sin cuerpo (204). Comprueba el código y no toca `data`, que aquí es
 * legítimamente `undefined`.
 */
export function exigirExito(resultado: { response: Response }, detalle: string): void {
  if (!resultado.response.ok) {
    throw new ErrorHttp(resultado.response.status, detalle);
  }
}

/**
 * ¿El fallo fue del servidor o de la red, y no de lo que escribió quien lo usa?
 *
 * <p>Existe para que las pantallas no repartan aritmética de códigos HTTP: lo que una pantalla
 * necesita decidir es a quién atribuir el fallo, no qué número llegó. Sin esto, un 500 acaba
 * mostrándose como "correo o clave incorrectos", que es culpar al comprador de una caída propia.
 *
 * <p>Cuenta como fallo del servidor cualquier cosa que **no** sea un {@link ErrorHttp} de 4xx: un
 * 5xx, y también un error que ni siquiera llegó a tener respuesta —`fetch` rechaza antes, con la
 * red caída o el DNS sin resolver— porque ahí tampoco hay nada que el usuario pueda corregir.
 */
export function esFalloDelServidor(error: unknown): boolean {
  return !(error instanceof ErrorHttp) || error.estado >= 500;
}
