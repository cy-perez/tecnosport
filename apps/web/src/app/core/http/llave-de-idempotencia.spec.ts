import { nuevaLlaveDeIdempotencia } from './llave-de-idempotencia';

describe('nuevaLlaveDeIdempotencia', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('usa randomUUID cuando existe', () => {
    expect(nuevaLlaveDeIdempotencia()).toMatch(/^[0-9a-f-]{36}$/);
  });

  /** Safari anterior a 15.4, o HTTP en la red local: sin randomUUID no puede lanzar. */
  it('sin randomUUID sigue dando llaves distintas', () => {
    const real = globalThis.crypto;
    vi.stubGlobal('crypto', {
      getRandomValues: <T extends ArrayBufferView>(a: T) => real.getRandomValues(a as never) as T,
    });
    const una = nuevaLlaveDeIdempotencia();
    const otra = nuevaLlaveDeIdempotencia();
    expect(una).toMatch(/^[0-9a-f]{32}$/);
    expect(una).not.toBe(otra);
  });

  it('sin crypto en absoluto, tampoco lanza', () => {
    vi.stubGlobal('crypto', undefined);
    expect(nuevaLlaveDeIdempotencia().length).toBeGreaterThan(10);
  });
});
