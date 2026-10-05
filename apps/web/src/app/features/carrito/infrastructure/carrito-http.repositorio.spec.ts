import { ErrorHttp } from '../../../core/http/respuesta-http';
import { CarritoInexistenteError } from '../domain/carrito.errores';
import { CarritoHttpRepositorio } from './carrito-http.repositorio';

/**
 * Contra `fetch`, como `cuenta-http.repositorio.spec.ts` y por el mismo motivo: el store decide
 * soltar el carrito y crear otro según el **tipo** de error que salga de aquí, y un doble que lance
 * el tipo correcto no dice nada de si este adaptador lo lanza.
 */
describe('CarritoHttpRepositorio', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function conRespuesta(respuesta: Response): CarritoHttpRepositorio {
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => respuesta),
    );
    return new CarritoHttpRepositorio();
  }

  function problema(estado: number, codigo: string): Response {
    return new Response(JSON.stringify({ codigo, status: estado }), {
      status: estado,
      headers: { 'Content-Type': 'application/problem+json' },
    });
  }

  it('agregar a un carrito que el servidor ya no tiene sale como CarritoInexistenteError', async () => {
    const repositorio = conRespuesta(problema(404, 'CARRITO_NO_ENCONTRADO'));

    await expect(repositorio.agregarLinea('purgado', 'variante-1', 1)).rejects.toBeInstanceOf(
      CarritoInexistenteError,
    );
  });

  it('cambiar o quitar una línea de un carrito purgado, también', async () => {
    await expect(
      conRespuesta(problema(404, 'CARRITO_NO_ENCONTRADO')).actualizarCantidad('purgado', 'l', 2),
    ).rejects.toBeInstanceOf(CarritoInexistenteError);
    await expect(
      conRespuesta(problema(404, 'CARRITO_NO_ENCONTRADO')).eliminarLinea('purgado', 'l'),
    ).rejects.toBeInstanceOf(CarritoInexistenteError);
  });

  // Un carrito nuevo no arregla una línea que ya no está: ese 404 no puede soltar el carrito.
  it('un 404 de otra cosa no se confunde con el carrito purgado', async () => {
    const repositorio = conRespuesta(problema(404, 'LINEA_CARRITO_NO_ENCONTRADA'));

    await expect(repositorio.eliminarLinea('carrito-1', 'l')).rejects.toSatisfy(
      (error: unknown) => error instanceof ErrorHttp && !(error instanceof CarritoInexistenteError),
    );
  });
});
