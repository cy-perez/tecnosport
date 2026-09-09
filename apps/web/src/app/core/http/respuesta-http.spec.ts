import { ErrorHttp, desempaquetar, exigirExito } from './respuesta-http';

/**
 * Reproduce la forma que devuelve `openapi-fetch`: cuando el cuerpo del fallo no es JSON, `error`
 * se queda en `undefined` y solo la `Response` dice que algo salió mal. Ese es el caso que el
 * `if (error)` de los adaptadores no veía.
 */
function resultado<T>(cuerpo: T | undefined, estado: number, tipo = 'application/json') {
  return {
    data: cuerpo,
    response: new Response(null, { status: estado, headers: { 'Content-Type': tipo } }),
  };
}

describe('desempaquetar', () => {
  it('devuelve el cuerpo cuando la respuesta es exitosa', () => {
    expect(desempaquetar(resultado({ id: 'c1' }, 200), 'no se pudo cargar')).toEqual({ id: 'c1' });
  });

  it('lanza ErrorHttp con el código ante un 500 sin cuerpo — el caso que se colaba', () => {
    const fallo = resultado(undefined, 500);

    expect(() => desempaquetar(fallo, 'no se pudo cargar el carrito')).toThrowError(ErrorHttp);
    try {
      desempaquetar(fallo, 'no se pudo cargar el carrito');
    } catch (error) {
      expect((error as ErrorHttp).estado).toBe(500);
      expect((error as ErrorHttp).message).toContain('no se pudo cargar el carrito');
    }
  });

  it('lanza ErrorHttp ante un 502 con cuerpo HTML, que es lo que devuelve un balanceador', () => {
    expect(() => desempaquetar(resultado(undefined, 502, 'text/html'), 'sin backend')).toThrowError(
      ErrorHttp,
    );
  });

  it('lanza ErrorHttp ante un 200 sin cuerpo, para que no llegue undefined al mapeador', () => {
    expect(() => desempaquetar(resultado(undefined, 200), 'respuesta vacía')).toThrowError(ErrorHttp);
  });

  it('no confunde un cuerpo falsy con la ausencia de cuerpo', () => {
    expect(desempaquetar(resultado(0, 200), 'cero es un cuerpo')).toBe(0);
    expect(desempaquetar(resultado('', 200), 'vacío es un cuerpo')).toBe('');
    expect(desempaquetar(resultado(false, 200), 'false es un cuerpo')).toBe(false);
  });
});

describe('exigirExito', () => {
  it('deja pasar un 204, que no lleva cuerpo', () => {
    expect(() => exigirExito(resultado(undefined, 204), 'no se pudo borrar')).not.toThrow();
  });

  it('lanza ErrorHttp con el código ante un fallo', () => {
    try {
      exigirExito(resultado(undefined, 503), 'no se pudo borrar');
      expect.unreachable('debió lanzar');
    } catch (error) {
      expect((error as ErrorHttp).estado).toBe(503);
    }
  });
});
