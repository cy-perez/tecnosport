import { InjectionToken } from '@angular/core';

/**
 * El tope del mensaje, el mismo que el dominio del servidor
 * (`Sugerencia.MAXIMO_CARACTERES_MENSAJE`).
 *
 * <p><b>Es una cortesía, no una validación.</b> Lo que de verdad rechaza un mensaje demasiado largo
 * es el servidor, y tiene que seguir siendo así: la ruta es pública y cualquiera puede mandar un
 * cuerpo sin pasar por este formulario (regla dura #7). Lo que este número compra es que la caja
 * deje de aceptar teclas y el contador explique por qué, en vez de que quien escribió trescientas
 * palabras reciba un error después de pulsar enviar.
 *
 * <p>Que sean dos copias es deliberado y tiene su precio: si el servidor baja el tope, aquí hay que
 * bajarlo también. La alternativa —pedírselo a la API— sería una petición más en cada carga de la
 * página para un número que cambia una vez cada nunca.
 */
export const MAXIMO_CARACTERES_SUGERENCIA = 2000;

/** Lo que el buzón manda. El correo es opcional; la autorización solo cuenta cuando lo hay. */
export interface NuevaSugerencia {
  readonly mensaje: string;
  readonly correo: string | null;
  readonly autorizaDatos: boolean;
}

export interface RepositorioSugerencias {
  /** `POST /api/v1/sugerencias`. Responde 202: se acepta el mensaje, no se crea nada consultable. */
  enviar(sugerencia: NuevaSugerencia): Promise<void>;
}

export const REPOSITORIO_SUGERENCIAS = new InjectionToken<RepositorioSugerencias>(
  'RepositorioSugerencias',
);
