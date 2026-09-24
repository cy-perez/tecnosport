import { CrearPedidoComando } from '../domain/pedido.comandos';
import { PedidoHttpRepositorio } from './pedido-http.repositorio';

/**
 * La llave de idempotencia y lo que de verdad cubre.
 *
 * El javadoc de antes decía "un UUID por intento del usuario, no por reintento HTTP — se genera una
 * vez aquí, por cada vez que este método se invoca (una invocación = un intento real de
 * confirmar)". Esa premisa es falsa justo en el caso que la llave existe para cubrir: la petición
 * sale, la red se corta antes de la respuesta, el servidor ya creó el pedido y reservó el
 * inventario, y quien compra ve un error y vuelve a pulsar. Esa segunda pulsación es el mismo
 * intento, y con llave nueva creaba un segundo pedido con una segunda reserva sobre las mismas
 * unidades — que en contraentrega no vence nunca.
 */
describe('PedidoHttpRepositorio: la llave de idempotencia', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const COMANDO: CrearPedidoComando = {
    correo: 'cliente@tecnosport.co',
    contacto: { nombre: 'Cliente de prueba', telefono: '3001234567' },
    lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
    tipoEntrega: 'ENVIO_A_DOMICILIO',
    direccion: {
      codigoDaneDepartamento: '05',
      departamento: 'Antioquia',
      codigoDaneCiudad: '05001',
      ciudad: 'Medellín',
      direccion: 'Circular 4 # 70-20',
      indicaciones: null,
      barrio: null,
    },
    metodoPago: 'NEQUI',
    autorizaDatos: true,
  };

  const PEDIDO_CREADO = {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    estado: 'PAGO_PENDIENTE',
    correo: 'cliente@tecnosport.co',
    metodoPago: 'NEQUI',
    subtotal: { valor: '100000', moneda: 'COP' },
    costoEnvio: { valor: '10000', moneda: 'COP' },
    total: { valor: '110000', moneda: 'COP' },
    lineas: [],
    historial: [],
  };

  /**
   * Cada respuesta de la cola atiende una llamada, en orden, y se anota la llave que viajó. Sin
   * `window`, `baseUrl()` toma la rama absoluta: el `fetch` de Node bajo jsdom exige un origen.
   * Mismo montaje que `envio-http.repositorio.spec.ts`.
   */
  function conRespuestas(respuestas: (() => Response | Promise<Response>)[]): {
    repositorio: PedidoHttpRepositorio;
    llaves: string[];
  } {
    const llaves: string[] = [];
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      // `openapi-fetch` llama a `fetch(new Request(...))`, con un solo argumento: las cabeceras
      // viajan dentro de la petición, no en un segundo parámetro de opciones.
      vi.fn(async (entrada: Request | string, opciones?: RequestInit) => {
        const cabeceras =
          entrada instanceof Request ? entrada.headers : new Headers(opciones?.headers);
        llaves.push(cabeceras.get('Idempotency-Key') ?? '');
        const siguiente = respuestas.shift();
        if (!siguiente) {
          throw new Error('el fetch se llamó más veces de las que la prueba preparó');
        }
        return siguiente();
      }),
    );
    return { repositorio: new PedidoHttpRepositorio(), llaves };
  }

  function json(cuerpo: unknown, status: number): Response {
    return new Response(JSON.stringify(cuerpo), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('repite la misma llave cuando el primer intento no llegó a completarse', async () => {
    const { repositorio, llaves } = conRespuestas([
      () => {
        throw new TypeError('Failed to fetch');
      },
      () => json(PEDIDO_CREADO, 201),
    ]);

    await expect(repositorio.crear(COMANDO)).rejects.toBeTruthy();
    await repositorio.crear(COMANDO);

    expect(llaves).toHaveLength(2);
    expect(llaves[0]).toBeTruthy();
    expect(llaves[1]).toBe(llaves[0]);
  });

  it('usa una llave nueva para la compra siguiente, cuando la anterior sí se completó', async () => {
    const { repositorio, llaves } = conRespuestas([
      () => json(PEDIDO_CREADO, 201),
      () => json(PEDIDO_CREADO, 201),
    ]);

    await repositorio.crear(COMANDO);
    await repositorio.crear(COMANDO);

    expect(llaves[0]).toBeTruthy();
    expect(llaves[1]).not.toBe(llaves[0]);
  });
});
