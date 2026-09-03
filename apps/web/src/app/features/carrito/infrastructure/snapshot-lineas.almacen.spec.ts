import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { guardarSnapshot, leerSnapshot } from './snapshot-lineas.almacen';

function snapshotDePrueba(varianteId: string): SnapshotLinea {
  return {
    varianteId,
    nombreProducto: 'Morral urbano',
    slugProducto: 'morral-urbano',
    sku: 'SKU-1',
    imagenUrl: 'https://x/img.jpg',
    imagenAlt: 'Morral urbano',
    precioValor: 150_000,
    precioMoneda: 'COP',
  };
}

describe('snapshot-lineas.almacen', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('devuelve null si no hay snapshot para esa variante', () => {
    expect(leerSnapshot('no-existe')).toBeNull();
  });

  it('guarda y vuelve a leer el snapshot de una variante', () => {
    const snapshot = snapshotDePrueba('variante-1');

    guardarSnapshot(snapshot);

    expect(leerSnapshot('variante-1')).toEqual(snapshot);
  });

  it('guardar varios no pisa los de otras variantes', () => {
    guardarSnapshot(snapshotDePrueba('variante-1'));
    guardarSnapshot(snapshotDePrueba('variante-2'));

    expect(leerSnapshot('variante-1')?.varianteId).toBe('variante-1');
    expect(leerSnapshot('variante-2')?.varianteId).toBe('variante-2');
  });
});
