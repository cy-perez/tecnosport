import { esLimiteDeIntentos } from '../../../core/errores/mensaje-de-error';
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
    nombreComprador: 'Cliente de prueba',
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
    metodoPago: 'WOMPI',
    autorizaDatos: true,
    transportadora: null,
  };

  const PEDIDO_CREADO = {
    id: 'pedido-1',
    numeroPedido: 'TS-2026-000001',
    estado: 'PAGO_PENDIENTE',
    correo: 'cliente@tecnosport.co',
    metodoPago: 'WOMPI',
    subtotal: { valor: '100000', moneda: 'COP' },
    descuento: { valor: '0', moneda: 'COP' },
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

/**
 * ADR-0073: la transportadora elegida viaja en el cuerpo de las dos peticiones. Si el adaptador la
 * perdiera, el servidor cobraría la más económica mientras la confirmación mostraba la elegida.
 */
describe('PedidoHttpRepositorio: la transportadora elegida', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function capturarCuerpos(respuesta: unknown): unknown[] {
    const cuerpos: unknown[] = [];
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: Request) => {
        cuerpos.push(await entrada.clone().json());
        return new Response(JSON.stringify(respuesta), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
    return cuerpos;
  }

  const DIRECCION = {
    codigoDaneDepartamento: '05',
    departamento: 'Antioquia',
    codigoDaneCiudad: '05001',
    ciudad: 'Medellín',
    direccion: 'Circular 4 # 70-20',
    indicaciones: null,
    barrio: null,
  };

  it('va en el cuerpo al crear el pedido', async () => {
    const cuerpos = capturarCuerpos({
      id: 'pedido-1',
      lineas: [],
      historial: [],
      descuento: { valor: 0, moneda: 'COP' },
    });

    await new PedidoHttpRepositorio().crear({
      correo: 'cliente@tecnosport.co',
      nombreComprador: 'Carlos Ruiz',
      contacto: { nombre: 'Cliente de prueba', telefono: '3001234567' },
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: DIRECCION,
      metodoPago: 'WOMPI',
      autorizaDatos: true,
      transportadora: 'Servientrega',
    });

    // Quien compra y quien recibe viajan por separado (ADR-0099).
    expect(cuerpos[0]).toMatchObject({
      transportadora: 'Servientrega',
      nombreComprador: 'Carlos Ruiz',
      nombre: 'Cliente de prueba',
    });
  });

  it('va en el cuerpo al pedir los métodos de pago', async () => {
    const cuerpos = capturarCuerpos({ metodos: [] });

    await new PedidoHttpRepositorio().metodosDePagoConPrecio({
      correo: 'cliente@tecnosport.co',
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: DIRECCION,
      transportadora: 'Servientrega',
      telefono: '3138816711',
    });

    expect(cuerpos[0]).toMatchObject({ transportadora: 'Servientrega', telefono: '3138816711' });
  });

  /** ADR-0095: la ruta nueva, y lo que devuelve, mapeado al modelo del front. */
  it('pide los métodos con precio a su ruta y devuelve cada uno con su subtotal y descuento', async () => {
    const urls: string[] = [];
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: Request) => {
        urls.push(entrada.url);
        return new Response(
          JSON.stringify({
            metodos: [
              {
                metodo: 'WOMPI',
                subtotal: { valor: 239_000, moneda: 'COP' },
                descuento: { valor: 0, moneda: 'COP' },
              },
              {
                metodo: 'TRANSFERENCIA_MANUAL',
                subtotal: { valor: 230_000, moneda: 'COP' },
                descuento: { valor: 9_000, moneda: 'COP' },
              },
            ],
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        );
      }),
    );

    const metodos = await new PedidoHttpRepositorio().metodosDePagoConPrecio({
      correo: 'cliente@tecnosport.co',
      lineas: [{ varianteId: 'variante-1', cantidad: 1 }],
      tipoEntrega: 'RETIRO_EN_PUNTO',
      direccion: null,
      transportadora: null,
      telefono: '3138816711',
    });

    expect(urls[0]).toContain('/api/v1/pedidos/metodos-de-pago-con-precio');
    expect(metodos).toEqual([
      {
        metodo: 'WOMPI',
        subtotal: { valor: 239_000, moneda: 'COP' },
        descuento: { valor: 0, moneda: 'COP' },
        costoEnvio: null,
      },
      {
        metodo: 'TRANSFERENCIA_MANUAL',
        subtotal: { valor: 230_000, moneda: 'COP' },
        descuento: { valor: 9_000, moneda: 'COP' },
        costoEnvio: null,
      },
    ]);
  });

  /**
   * ADR-0086: la línea de un color de tecnología confirmado con el proveedor lleva su enlace de
   * compra. Si el adaptador lo perdiera, el servidor rechazaría el pedido con el color ya
   * confirmado y la unidad apartada.
   */
  it('el enlace de compra de una línea va en el cuerpo al crear el pedido', async () => {
    const cuerpos = capturarCuerpos({
      id: 'pedido-1',
      lineas: [],
      historial: [],
      descuento: { valor: 0, moneda: 'COP' },
    });

    await new PedidoHttpRepositorio().crear({
      correo: 'cliente@tecnosport.co',
      nombreComprador: 'Cliente de prueba',
      contacto: { nombre: 'Cliente de prueba', telefono: '3001234567' },
      lineas: [
        { varianteId: 'variante-1', cantidad: 1, enlaceCompra: 'token-1' },
        { varianteId: 'variante-2', cantidad: 2 },
      ],
      tipoEntrega: 'ENVIO_A_DOMICILIO',
      direccion: DIRECCION,
      metodoPago: 'WOMPI',
      autorizaDatos: true,
      transportadora: 'Servientrega',
    });

    expect(cuerpos[0]).toMatchObject({
      lineas: [
        { varianteId: 'variante-1', cantidad: 1, enlaceCompra: 'token-1' },
        { varianteId: 'variante-2', cantidad: 2 },
      ],
    });
  });
});

/**
 * ADR-0088. El servidor responde 202 sin cuerpo acierte o no, y eso no puede leerse como un fallo;
 * el 429 sí tiene que salir como error y con el código que la pantalla reconoce, o le diría "te
 * enviamos el enlace" a quien chocó con el límite.
 */
describe('PedidoHttpRepositorio: pedir el enlace de seguimiento', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function responder(respuesta: () => Response): Request[] {
    const peticiones: Request[] = [];
    vi.stubGlobal('window', undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: Request) => {
        peticiones.push(entrada.clone());
        return respuesta();
      }),
    );
    return peticiones;
  }

  it('un 202 sin cuerpo termina sin error y el correo viaja en el cuerpo', async () => {
    const peticiones = responder(() => new Response(null, { status: 202 }));

    await new PedidoHttpRepositorio().solicitarEnlaceDeSeguimiento(
      'TS-2026-000001',
      'cliente@tecnosport.co',
    );

    expect(peticiones[0].method).toBe('POST');
    expect(new URL(peticiones[0].url).pathname).toBe('/api/v1/pedidos/seguimiento');
    expect(new URL(peticiones[0].url).search).toBe('');
    expect(await peticiones[0].json()).toEqual({
      numeroPedido: 'TS-2026-000001',
      correo: 'cliente@tecnosport.co',
    });
  });

  it('un 429 sale como límite de intentos', async () => {
    responder(
      () =>
        new Response(JSON.stringify({ status: 429, codigo: 'LIMITE_DE_INTENTOS_EXCEDIDO' }), {
          status: 429,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
    );

    const fallo = await new PedidoHttpRepositorio()
      .solicitarEnlaceDeSeguimiento('TS-2026-000001', 'cliente@tecnosport.co')
      .then(
        () => null,
        (error: unknown) => error,
      );

    expect(esLimiteDeIntentos(fallo)).toBe(true);
  });
});
