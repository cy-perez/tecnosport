import { recogidaVencida } from './recogida-vencida';

describe('recogidaVencida', () => {
  const vence = '2026-10-27T15:00:00Z';
  const instante = Date.parse(vence);

  it('antes del plazo no ha vencido', () => {
    expect(recogidaVencida({ recogidaVenceEn: vence }, instante - 1)).toBe(false);
  });

  it('en el instante exacto del plazo ya venció, como en el servidor', () => {
    expect(recogidaVencida({ recogidaVenceEn: vence }, instante)).toBe(true);
  });

  it('después del plazo venció', () => {
    expect(recogidaVencida({ recogidaVenceEn: vence }, instante + 60_000)).toBe(true);
  });

  it('un pedido que no espera recogida nunca vence', () => {
    expect(recogidaVencida({ recogidaVenceEn: null }, instante + 60_000)).toBe(false);
  });
});
