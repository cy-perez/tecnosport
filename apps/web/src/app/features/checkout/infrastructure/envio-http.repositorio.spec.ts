import { ErrorHttp } from '../../../core/http/respuesta-http';
import { CotizarEnvioComando } from '../domain/envio.model';
import { EnvioHttpRepositorio } from './envio-http.repositorio';

/**
 * "No hay cómo enviar a esa dirección" y "no se pudo preguntar" llegan las dos como un fallo
 * HTTP, y para quien compra no son lo mismo: la primera se resuelve recogiendo en el punto y la
 * segunda no se resuelve cambiando nada. Confundirlas manda al comprador a corregir una dirección
 * que estaba bien.
 */
describe('EnvioHttpRepositorio.cotizar', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const COMANDO: CotizarEnvioComando = {
    lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
    direccion: {
      codigoDaneDepartamento: '05',
      departamento: 'Antioquia',
      codigoDaneCiudad: '05001',
      ciudad: 'Medellín',
      direccion: 'Circular 4 # 70-20',
      indicaciones: null,
      barrio: null,
    },
  };

  // Sin `window`, `baseUrl()` toma la rama absoluta: el `fetch` de Node bajo jsdom exige un
  // origen. Mismo motivo que en `sesion-http.repositorio.spec.ts`.
  function conRespuesta(respuesta: Response): EnvioHttpRepositorio {
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => respuesta),
    );
    return new EnvioHttpRepositorio();
  }

  function json(cuerpo: unknown, status: number): Response {
    return new Response(JSON.stringify(cuerpo), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('devuelve la cotización cuando el servidor responde 200', async () => {
    const repositorio = conRespuesta(
      json(
        {
          costoEnvio: { valor: 9540, moneda: 'COP' },
          transportadora: '99 minutes',
          diasEstimados: 2,
          venceEn: '2026-09-12T12:00:00Z',
          admiteContraentrega: false,
        },
        200,
      ),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toEqual({
      costoEnvio: 9540,
      moneda: 'COP',
      transportadora: '99 minutes',
      diasEstimados: 2,
      venceEn: '2026-09-12T12:00:00Z',
    });
  });

  it('traduce el 409 de sin cobertura a null, que no es un error', async () => {
    const repositorio = conRespuesta(
      json({ status: 409, codigo: 'ENVIO_SIN_COBERTURA', detail: 'sin tarifa' }, 409),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toBeNull();
  });

  /** Un 409 con otro código sigue siendo un error: solo el de cobertura es una respuesta. */
  it('otro 409 se propaga', async () => {
    const repositorio = conRespuesta(json({ status: 409, codigo: 'EXISTENCIA_INSUFICIENTE' }, 409));

    await expect(repositorio.cotizar(COMANDO)).rejects.toBeInstanceOf(ErrorHttp);
  });

  it('un 500 se propaga', async () => {
    const repositorio = conRespuesta(new Response('', { status: 500 }));

    await expect(repositorio.cotizar(COMANDO)).rejects.toBeInstanceOf(ErrorHttp);
  });
});
