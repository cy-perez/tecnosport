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
      tipo: 'TARIFA',
      cotizacion: {
        costoEnvio: 9540,
        moneda: 'COP',
        transportadora: '99 minutes',
        diasEstimados: 2,
        venceEn: '2026-09-12T12:00:00Z',
      },
    });
  });

  it('traduce el 409 de sin cobertura a un resultado, que no es un error', async () => {
    const repositorio = conRespuesta(
      json({ status: 409, codigo: 'ENVIO_SIN_COBERTURA', detail: 'sin tarifa' }, 409),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toEqual({ tipo: 'SIN_COBERTURA' });
  });

  /**
   * El otro 409 de negocio (`ADR-0036`), y lo que se prueba no es que no lance: es que **los
   * artículos lleguen**. Sin ellos la pantalla solo puede decir "algo de tu carrito", que era el
   * mensaje inútil que esta decisión vino a reemplazar.
   */
  it('traduce el 409 de artículo no asegurable con los artículos que vinieron', async () => {
    const repositorio = conRespuesta(
      json(
        {
          status: 409,
          codigo: 'ARTICULO_NO_ASEGURABLE',
          detail: 'supera el máximo',
          articulos: [{ varianteId: 'v-1', nombre: 'Portátil para diseño' }],
        },
        409,
      ),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toEqual({
      tipo: 'ARTICULO_NO_ASEGURABLE',
      articulos: [{ varianteId: 'v-1', nombre: 'Portátil para diseño' }],
    });
  });

  /**
   * Y si el cuerpo no trae los artículos —o los trae con otra forma— la pantalla se queda sin
   * nombres pero el comprador ve la frase. Un mensaje a medias es mejor que una excepción.
   */
  it('el artículo no asegurable sin artículos legibles no rompe', async () => {
    const repositorio = conRespuesta(
      json({ status: 409, codigo: 'ARTICULO_NO_ASEGURABLE', articulos: 'ninguno' }, 409),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toEqual({
      tipo: 'ARTICULO_NO_ASEGURABLE',
      articulos: [],
    });
  });

  /**
   * El tercer 409 de negocio: la plataforma rechazó los datos del envío. Lo que importa es que
   * **no** se propague como error, porque el texto de un error invita a reintentar y el reintento
   * trae el mismo rechazo — Skydropx deduplica las cotizaciones por contenido.
   */
  it('traduce el 409 de cotización rechazada a un resultado', async () => {
    const repositorio = conRespuesta(
      json({ status: 409, codigo: 'COTIZACION_RECHAZADA', detail: 'el proveedor rechazó' }, 409),
    );

    await expect(repositorio.cotizar(COMANDO)).resolves.toEqual({ tipo: 'COTIZACION_RECHAZADA' });
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
