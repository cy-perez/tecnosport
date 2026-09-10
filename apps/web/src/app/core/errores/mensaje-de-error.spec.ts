import { TranslocoService } from '@jsverse/transloco';
import { ErrorHttp } from '../http/respuesta-http';
import { mensajeDeError } from './mensaje-de-error';

/**
 * Doble mínimo de Transloco: devuelve la clave cuando no la tiene, que es lo que hace el de verdad y
 * lo que `mensajeDeError` usa para saber si existe.
 */
function transloco(textos: Record<string, string>): TranslocoService {
  return {
    translate: (clave: string) => textos[clave] ?? clave,
  } as unknown as TranslocoService;
}

const TEXTOS = {
  'admin.retractos.error': 'No se pudo completar la accion.',
  'admin.errores.monto_de_reintegro_invalido': 'El monto no cabe.',
};

describe('mensajeDeError', () => {
  it('traduce el codigo que manda el backend', () => {
    const error = new ErrorHttp(422, 'diagnostico', 'MONTO_DE_REINTEGRO_INVALIDO');

    expect(mensajeDeError(error, transloco(TEXTOS), 'admin.retractos.error')).toBe(
      'El monto no cabe.',
    );
  });

  // Hay treinta y cuatro codigos en el backend y la mayoria no los puede provocar un panel. Los que
  // no estan traducidos caen al generico, en vez de pintar una clave cruda en la pantalla.
  it('un codigo sin traducir cae al mensaje generico', () => {
    const error = new ErrorHttp(404, 'diagnostico', 'ATRIBUTO_NO_ENCONTRADO');

    expect(mensajeDeError(error, transloco(TEXTOS), 'admin.retractos.error')).toBe(
      'No se pudo completar la accion.',
    );
  });

  it('un fallo sin codigo tambien: un 502 en HTML no trae ProblemDetail', () => {
    const error = new ErrorHttp(502, 'sin backend');

    expect(mensajeDeError(error, transloco(TEXTOS), 'admin.retractos.error')).toBe(
      'No se pudo completar la accion.',
    );
  });

  it('y algo que no es un ErrorHttp —la red caida— igual', () => {
    expect(mensajeDeError(new TypeError('fetch failed'), transloco(TEXTOS), 'admin.retractos.error')).toBe(
      'No se pudo completar la accion.',
    );
  });
});
