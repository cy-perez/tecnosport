import { APP_ID, TransferState } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  CLAVE_ESTADO_CONSULTAS,
  CLIENTE_DE_CONSULTAS,
  provideEstadoDeConsultasDesdeElServidor,
  provideEstadoDeConsultasHaciaElNavegador,
} from './transferencia-estado-consultas';

const CLAVE_CATALOGO = ['catalogo', 'productos', { orden: 'MAS_RECIENTES' }] as const;
const PAGINAS = {
  pages: [{ items: [{ slug: 'zapatilla-trail' }], cursorSiguiente: null }],
  pageParams: [null],
};

/** Lo que hace el servidor: llenar la caché durante el render y serializar el estado al final. */
async function serializarComoElServidor(
  sembrar: (cliente: QueryClient) => Promise<void> | void,
): Promise<string> {
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(CLIENTE_DE_CONSULTAS),
      provideEstadoDeConsultasHaciaElNavegador(),
    ],
  });
  await sembrar(TestBed.inject(QueryClient));
  return TestBed.inject(TransferState).toJson();
}

const APP_ID_DE_PRUEBA = 'prueba';

/**
 * Lo que hace el navegador: leer el `<script id="<APP_ID>-state">` que vino en el HTML.
 *
 * El script va al documento **antes** del primer `TestBed.inject`: ahí se crea el inyector y
 * corren los inicializadores de entorno, y el `TransferState` lee el DOM una sola vez al nacer.
 */
function arrancarComoElNavegador(json: string): QueryClient {
  TestBed.resetTestingModule();
  const script = document.createElement('script');
  script.id = `${APP_ID_DE_PRUEBA}-state`;
  script.type = 'application/json';
  script.textContent = json;
  document.body.appendChild(script);

  TestBed.configureTestingModule({
    providers: [
      { provide: APP_ID, useValue: APP_ID_DE_PRUEBA },
      provideTanStackQuery(CLIENTE_DE_CONSULTAS),
      provideEstadoDeConsultasDesdeElServidor(),
    ],
  });
  return TestBed.inject(QueryClient);
}

describe('transferencia del estado de consultas entre servidor y navegador', () => {
  afterEach(() => {
    document.querySelectorAll('script[id$="-state"]').forEach((script) => script.remove());
  });

  it('el servidor deshidrata la caché al serializar el estado', async () => {
    const json = await serializarComoElServidor((cliente) => {
      cliente.setQueryData(CLAVE_CATALOGO, PAGINAS);
    });

    const deshidratado = JSON.parse(json)[CLAVE_ESTADO_CONSULTAS];
    expect(deshidratado.queries).toHaveLength(1);
    expect(deshidratado.queries[0].queryKey).toEqual(CLAVE_CATALOGO);
    expect(deshidratado.queries[0].state.data).toEqual(PAGINAS);
  });

  it('el navegador arranca con la caché del servidor y el resolver no vuelve a pedir', async () => {
    const json = await serializarComoElServidor((cliente) => {
      cliente.setQueryData(CLAVE_CATALOGO, PAGINAS);
    });

    const cliente = arrancarComoElNavegador(json);

    expect(cliente.getQueryState(CLAVE_CATALOGO)?.status).toBe('success');
    expect(cliente.getQueryData(CLAVE_CATALOGO)).toEqual(PAGINAS);

    // Lo mismo que hace `precargarProductos` en el `resolve` de la ruta, con el mismo staleTime.
    const pedirAlBackend = vi.fn(() => Promise.resolve(PAGINAS.pages[0]));
    await cliente.prefetchInfiniteQuery({
      queryKey: CLAVE_CATALOGO,
      queryFn: pedirAlBackend,
      initialPageParam: null,
      staleTime: 60_000,
    });
    expect(pedirAlBackend).not.toHaveBeenCalled();

    // Y el estado no se queda duplicado en el TransferState una vez hidratado.
    expect(TestBed.inject(TransferState).hasKey(CLAVE_ESTADO_CONSULTAS)).toBe(false);
  });

  it('una consulta que falló en el servidor no viaja: el navegador la vuelve a pedir', async () => {
    const json = await serializarComoElServidor(async (cliente) => {
      cliente.setQueryData(CLAVE_CATALOGO, PAGINAS);
      await cliente.prefetchQuery({
        queryKey: ['catalogo', 'marcas'],
        queryFn: () => Promise.reject(new Error('la API no respondió')),
        retry: false,
      });
    });

    const claves = JSON.parse(json)[CLAVE_ESTADO_CONSULTAS].queries.map(
      (consulta: { queryKey: unknown }) => consulta.queryKey,
    );
    expect(claves).toEqual([CLAVE_CATALOGO]);
  });

  it('sin estado transferido, el navegador arranca con la caché vacía y sin error', () => {
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(CLIENTE_DE_CONSULTAS),
        provideEstadoDeConsultasDesdeElServidor(),
      ],
    });

    expect(TestBed.inject(QueryClient).getQueryCache().getAll()).toHaveLength(0);
  });

  it('el cliente de consultas es uno por aplicación, no uno por módulo', () => {
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(CLIENTE_DE_CONSULTAS)] });
    const primero = TestBed.inject(QueryClient);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(CLIENTE_DE_CONSULTAS)] });
    const segundo = TestBed.inject(QueryClient);

    expect(segundo).not.toBe(primero);
  });
});
