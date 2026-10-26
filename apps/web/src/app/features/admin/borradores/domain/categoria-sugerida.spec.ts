import { aBorrador } from '../infrastructure/mapeador-borrador';
import { categoriaSugerida } from './borrador.model';

describe('categoriaSugerida', () => {
  /** Un tenis va a la rama de su género (ADR-0089): la foto de Tenis Al Pie lo dice. */
  it('propone la rama del calzado del género del tenis', () => {
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'DAMA' })).toBe('calzado-dama');
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'CABALLERO' })).toBe(
      'calzado-caballero',
    );
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'UNISEX' })).toBe('calzado-unisex');
  });

  it('sin género no propone ninguna, y lo demás sigue con la de su tipo', () => {
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: null })).toBeUndefined();
    expect(categoriaSugerida({ tipo: 'BLUSA', generoDeCalzado: null })).toBe('ropa-dama-blusas');
  });

  it('el género llega del servidor, o nulo', () => {
    expect(aBorrador({ id: 'b1', generoDeCalzado: 'UNISEX' }).generoDeCalzado).toBe('UNISEX');
    expect(aBorrador({ id: 'b2' }).generoDeCalzado).toBeNull();
  });
});
