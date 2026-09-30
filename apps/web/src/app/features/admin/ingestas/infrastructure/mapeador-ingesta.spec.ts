import { loteAbierto } from '../domain/ingesta.model';
import { aLoteIngesta } from './mapeador-ingesta';

describe('aLoteIngesta', () => {
  /**
   * El lote recién recibido llega sin resumen. Si el mapeador dejara el resumen en `undefined`,
   * la fila reventaría al pintar las cifras antes de que el lote empiece —que es justo cuando
   * quien acaba de subir el zip está mirando.
   */
  it('un lote recién recibido tiene las cifras en cero y cuenta como abierto', () => {
    const lote = aLoteIngesta({ id: 'l1', estado: 'RECIBIDO', creadoEn: '2026-09-30T15:00:00Z' });

    expect(lote.resumen.borradoresNuevos).toBe(0);
    expect(lote.terminadoEn).toBeNull();
    expect(loteAbierto(lote)).toBe(true);
  });

  it('un lote terminado deja de estar abierto, y el sondeo se apaga con él', () => {
    const lote = aLoteIngesta({
      id: 'l1',
      estado: 'TERMINADO',
      creadoEn: '2026-09-30T15:00:00Z',
      terminadoEn: '2026-09-30T15:01:00Z',
      resumen: { publicaciones: 9, borradoresNuevos: 9 },
    });

    expect(lote.resumen).toMatchObject({ publicaciones: 9, borradoresNuevos: 9, alertas: 0 });
    expect(loteAbierto(lote)).toBe(false);
  });
});
