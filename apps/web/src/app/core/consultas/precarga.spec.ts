import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { focusManager, onlineManager, QueryClient } from '@tanstack/angular-query-experimental';
import {
  ESPERA_MAXIMA_EN_EL_NAVEGADOR_MS,
  PRECARGA_NO_BLOQUEANTE,
  sinBloquearLaNavegacion,
} from './precarga';

/**
 * Lo que se mide aquí no es el valor que devuelve la promesa, es **si termina**.
 * Un resolver de ruta la espera, así que una promesa eternamente pendiente
 * congela la navegación entera — sin error, sin cancelación y sin cambiar la
 * URL.
 */
async function termina(promesa: Promise<unknown>, ms = 300): Promise<boolean> {
  const pendiente = Symbol('pendiente');
  const cual = await Promise.race([
    promesa.then(
      () => 'lista',
      () => 'lista',
    ),
    new Promise((resolver) => setTimeout(() => resolver(pendiente), ms)),
  ]);
  return cual !== pendiente;
}

/**
 * Una consulta que falla, con reintentos **explícitos**.
 *
 * El `retry: 1` no es decoración: en el navegador el valor por omisión es 3,
 * pero aquí sería 0. TanStack lo decide con `retry ?? (isServer() ? 0 : 3)`, y
 * su `isServer` es `typeof window === 'undefined'` **evaluado al cargar el
 * módulo** — en Vitest eso ocurre antes de que jsdom monte `window`, así que el
 * paquete se cree en un servidor. Sin fijarlo, la prueba no reproduciría el
 * camino del reintento, que es justo el que se rompe.
 *
 * `retryDelay` corto para que la espera del reintento no domine la medición.
 */
const CONSULTA_QUE_FALLA = {
  queryKey: ['precarga', 'prueba'] as const,
  queryFn: () => Promise.reject(new Error('HTTP 502')),
  retry: 1,
  retryDelay: 10,
};

describe('PRECARGA_NO_BLOQUEANTE', () => {
  afterEach(() => {
    // Los dos son singletons del paquete: si una prueba los deja tocados, se
    // lleva por delante a las demás.
    focusManager.setFocused(undefined);
    onlineManager.setOnline(true);
  });

  // El caso que apareció de verdad: la API caída y la pestaña en otra ventana.
  // `canContinue()` exige foco antes de cada reintento, así que el reintento se
  // queda en `pause()` esperando un evento de foco que no va a llegar.
  describe('con la pestaña en segundo plano y la consulta fallando', () => {
    beforeEach(() => focusManager.setFocused(false));

    it('la precarga termina', async () => {
      const precarga = new QueryClient().prefetchQuery({
        ...CONSULTA_QUE_FALLA,
        ...PRECARGA_NO_BLOQUEANTE,
      });

      expect(await termina(precarga)).toBe(true);
    });

    it('sin esas opciones se queda pendiente para siempre', async () => {
      const precarga = new QueryClient().prefetchQuery(CONSULTA_QUE_FALLA);

      expect(await termina(precarga)).toBe(false);
    });
  });

  // El otro camino, y el que le puede tocar a un visitante real: sin conexión,
  // con el `networkMode` por omisión el primer intento ni se lanza —
  // `canStart()` es falso y se va directo a `pause()`.
  describe('sin conexión', () => {
    beforeEach(() => onlineManager.setOnline(false));

    it('la precarga termina en vez de esperar a que vuelva la red', async () => {
      const precarga = new QueryClient().prefetchQuery({
        ...CONSULTA_QUE_FALLA,
        ...PRECARGA_NO_BLOQUEANTE,
      });

      expect(await termina(precarga)).toBe(true);
    });

    it('sin esas opciones se queda pendiente para siempre', async () => {
      const precarga = new QueryClient().prefetchQuery(CONSULTA_QUE_FALLA);

      expect(await termina(precarga)).toBe(false);
    });
  });

  // El orden del spread importa: las opciones de la precarga van **después** de
  // las de la consulta, o el `retry` de la consulta las pisa y vuelve el
  // defecto sin que nada se ponga rojo.
  it('sobreescriben el reintento de la consulta, no al revés', async () => {
    focusManager.setFocused(false);

    const alReves = new QueryClient().prefetchQuery({
      ...PRECARGA_NO_BLOQUEANTE,
      ...CONSULTA_QUE_FALLA,
    });

    expect(await termina(alReves)).toBe(false);
  });

  /**
   * El caso que el arreglo de opciones **no** cubre, y que motivó acotar la
   * espera: si la consulta ya tiene un fetch en vuelo, `Query.fetch` devuelve
   * ese y descarta las opciones nuevas. El fetch en vuelo es el del componente
   * que está en pantalla, con los valores por omisión, y por eso se pausa.
   */
  describe('cuando la precarga se engancha a un fetch ajeno ya pausado', () => {
    beforeEach(() => focusManager.setFocused(false));

    /** Deja al cliente con un fetch en vuelo y pausado, como el del componente. */
    async function conUnFetchAjenoYaPausado(): Promise<QueryClient> {
      const cliente = new QueryClient();
      // Lo que hace el componente en pantalla: sus propias opciones, que sí
      // reintentan y por tanto sí se pausan al no haber foco.
      void cliente.fetchQuery(CONSULTA_QUE_FALLA).catch(() => undefined);
      await new Promise((resolver) => setTimeout(resolver, 50));
      return cliente;
    }

    function precargaDelResolver(cliente: QueryClient): Promise<void> {
      return cliente.prefetchQuery({ ...CONSULTA_QUE_FALLA, ...PRECARGA_NO_BLOQUEANTE });
    }

    it('las opciones de la precarga no bastan: la promesa se queda pendiente', async () => {
      const cliente = await conUnFetchAjenoYaPausado();

      expect(await termina(precargaDelResolver(cliente))).toBe(false);
    });

    it('acotar la espera sí la desbloquea', async () => {
      TestBed.configureTestingModule({
        providers: [{ provide: PLATFORM_ID, useValue: 'browser' }],
      });
      const cliente = await conUnFetchAjenoYaPausado();

      const acotada = TestBed.runInInjectionContext(() =>
        sinBloquearLaNavegacion(precargaDelResolver(cliente)),
      );

      // Colgado del propio límite y no de un número repetido: si alguien lo
      // sube, la prueba lo sigue en vez de ponerse roja por nada.
      expect(await termina(acotada, ESPERA_MAXIMA_EN_EL_NAVEGADOR_MS + 500)).toBe(true);
    });

    // Por identidad y no por reloj: esperar a que *no* pase nada durante más
    // del límite son cinco segundos de prueba para comprobar algo que se ve en
    // una línea. Que devuelva la misma promesa **es** no haber carrera.
    it('en el servidor devuelve la promesa tal cual: ahí no se acota', () => {
      TestBed.configureTestingModule({
        providers: [{ provide: PLATFORM_ID, useValue: 'server' }],
      });
      const precarga = Promise.resolve();

      const resultado = TestBed.runInInjectionContext(() => sinBloquearLaNavegacion(precarga));

      expect(resultado).toBe(precarga);
    });
  });
});
