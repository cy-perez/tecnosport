import { ESPERA_DEL_PAGO, intervaloDeEsperaDelPago } from './seguimiento-pedido.consulta';

describe('intervaloDeEsperaDelPago', () => {
  it('con el pago pendiente y la pestaña que salió a pagarlo, vuelve a preguntar', () => {
    expect(intervaloDeEsperaDelPago('PAGO_PENDIENTE', true, 0)).toBe(ESPERA_DEL_PAGO.intervaloMs);
    expect(intervaloDeEsperaDelPago('PAGO_PENDIENTE', true, ESPERA_DEL_PAGO.topeMs - 1)).toBe(
      ESPERA_DEL_PAGO.intervaloMs,
    );
  });

  it('pasado el tope deja de preguntar', () => {
    expect(intervaloDeEsperaDelPago('PAGO_PENDIENTE', true, ESPERA_DEL_PAGO.topeMs)).toBe(false);
  });

  it('quien llega por el enlace de un correo consulta una sola vez', () => {
    expect(intervaloDeEsperaDelPago('PAGO_PENDIENTE', false, 0)).toBe(false);
  });

  it('con el pago ya resuelto no vuelve a preguntar', () => {
    for (const estado of ['PAGADO', 'PAGO_FALLIDO', 'EN_PREPARACION'] as const) {
      expect(intervaloDeEsperaDelPago(estado, true, 0)).toBe(false);
    }
  });

  it('cada 3 segundos durante 2 minutos, como se decidió el 29 de octubre de 2026', () => {
    expect(ESPERA_DEL_PAGO).toEqual({ intervaloMs: 3_000, topeMs: 120_000 });
  });
});
