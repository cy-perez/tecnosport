/**
 * Error de transporte o de servidor, con el código HTTP que lo causó. El mensaje es un diagnóstico
 * para quien programa, nunca un texto de pantalla: la presentación captura y traduce su propia
 * clave de Transloco (regla dura #4 de CLAUDE.md).
 *
 * <p>`codigo` es el que manda el backend en el `ProblemDetail` (`docs/03-api.md`): un valor estable
 * como `MONTO_DE_REINTEGRO_INVALIDO`, no una frase. Se guarda para que la pantalla pueda decir *qué*
 * pasó en vez de "hubo un error", y se guarda el código y no el `detail` justamente para no
 * saltarse Transloco: la frase del backend viene en un solo idioma y la escribe Java. Puede venir
 * vacío —un 500 en HTML de un balanceador no trae cuerpo—, y entonces la pantalla cae a su mensaje
 * genérico.
 */
export class ErrorHttp extends Error {
  constructor(
    readonly estado: number,
    detalle: string,
    readonly codigo?: string,
  ) {
    super(`${detalle} (HTTP ${estado})`);
    this.name = 'ErrorHttp';
  }
}

/**
 * El `codigo` del cuerpo de error, si vino y si es una cadena. `openapi-fetch` deja el cuerpo del
 * fallo en `error` solo cuando es JSON, así que aquí no se puede dar nada por hecho.
 */
function codigoDe(cuerpo: unknown): string | undefined {
  if (cuerpo === null || typeof cuerpo !== 'object' || !('codigo' in cuerpo)) {
    return undefined;
  }
  const codigo = (cuerpo as { codigo?: unknown }).codigo;
  return typeof codigo === 'string' && codigo !== '' ? codigo : undefined;
}

/**
 * Forma mínima del resultado de `openapi-fetch`. Se declara aquí, estructural, en vez de importar
 * su tipo genérico: lo único que estas dos funciones necesitan saber es que hay una `Response` y
 * puede haber `data`.
 */
interface ResultadoFetch<T> {
  data?: T;
  error?: unknown;
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
    throw new ErrorHttp(resultado.response.status, detalle, codigoDe(resultado.error));
  }
  return resultado.data;
}

/**
 * Para las respuestas sin cuerpo (204). Comprueba el código y no toca `data`, que aquí es
 * legítimamente `undefined`.
 */
export function exigirExito(
  resultado: { response: Response; error?: unknown },
  detalle: string,
): void {
  if (!resultado.response.ok) {
    throw new ErrorHttp(resultado.response.status, detalle, codigoDe(resultado.error));
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
