import { aBorrador } from '../infrastructure/mapeador-borrador';
import { categoriaSugerida } from './borrador.model';

describe('categoriaSugerida', () => {
  /** Un tenis va a la rama de su género (ADR-0089): la foto de Tenis Al Pie lo dice. */
  it('propone la rama del calzado del género del tenis', () => {
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'DAMA' })).toEqual(['calzado-dama']);
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'CABALLERO' })).toEqual([
      'calzado-caballero',
    ]);
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: 'UNISEX' })).toEqual([
      'calzado-unisex',
    ]);
  });

  it('sin género no propone ninguna, y lo demás sigue con la de su tipo', () => {
    expect(categoriaSugerida({ tipo: 'TENIS', generoDeCalzado: null })).toEqual([]);
    expect(categoriaSugerida({ tipo: 'BLUSA', generoDeCalzado: null })).toEqual([
      'ropa-dama-blusas',
    ]);
    expect(categoriaSugerida({ tipo: 'BUSO', generoDeCalzado: null })).toEqual([]);
  });

  /**
   * Los borradores de Meraki del 28 de octubre de 2026: ningún anuncio decía para quién era, y con
   * el género que leyó la foto cada uno cae en su hoja.
   */
  it('una prenda con género propone su hoja en la rama de ese género', () => {
    expect(categoriaSugerida({ tipo: 'BUSO', generoDeCalzado: 'DAMA' })).toEqual([
      'ropa-dama-buzos',
      'ropa-dama-busos',
    ]);
    expect(categoriaSugerida({ tipo: 'CONJUNTO_PANTALON', generoDeCalzado: 'DAMA' })).toEqual([
      'ropa-dama-conjuntos',
    ]);
    expect(categoriaSugerida({ tipo: 'ENTERIZO', generoDeCalzado: 'DAMA' })).toEqual([
      'ropa-dama-enterizos',
    ]);
    expect(categoriaSugerida({ tipo: 'GORRA', generoDeCalzado: 'CABALLERO' })).toEqual([
      'ropa-caballero-gorras',
    ]);
    expect(categoriaSugerida({ tipo: 'BERMUDA', generoDeCalzado: 'CABALLERO' })[0]).toBe(
      'ropa-caballero-bermudas',
    );
    expect(categoriaSugerida({ tipo: 'CAMISA', generoDeCalzado: 'DAMA' })).toEqual([
      'ropa-dama-camisas',
    ]);
  });

  /** El polo de dama va a los polos de dama, y la fija de caballero queda de respaldo. */
  it('el género manda sobre la hoja fija del tipo, que queda después', () => {
    expect(categoriaSugerida({ tipo: 'POLO', generoDeCalzado: 'DAMA' })).toEqual([
      'ropa-dama-polos',
      'ropa-caballero-polos',
    ]);
  });

  it('la ropa no tiene rama unisex: una prenda unisex solo propone la fija', () => {
    expect(categoriaSugerida({ tipo: 'CAMISETA', generoDeCalzado: 'UNISEX' })).toEqual([]);
    expect(categoriaSugerida({ tipo: 'POLO', generoDeCalzado: 'UNISEX' })).toEqual([
      'ropa-caballero-polos',
    ]);
  });

  it('el género llega del servidor, o nulo', () => {
    expect(aBorrador({ id: 'b1', generoDeCalzado: 'UNISEX' }).generoDeCalzado).toBe('UNISEX');
    expect(aBorrador({ id: 'b2' }).generoDeCalzado).toBeNull();
  });
});
