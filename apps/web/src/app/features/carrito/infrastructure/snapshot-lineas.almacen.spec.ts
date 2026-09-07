import { SnapshotLinea } from '../domain/snapshot-linea.model';
import { SnapshotLineasLocalStorageAlmacen } from './snapshot-lineas.almacen';

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

describe('SnapshotLineasLocalStorageAlmacen', () => {
  let almacen: SnapshotLineasLocalStorageAlmacen;

  beforeEach(() => {
    window.localStorage.clear();
    almacen = new SnapshotLineasLocalStorageAlmacen();
  });

  it('devuelve null si no hay snapshot para esa variante', () => {
    expect(almacen.leer('no-existe')).toBeNull();
  });

  it('guarda y vuelve a leer el snapshot de una variante', () => {
    const snapshot = snapshotDePrueba('variante-1');

    almacen.guardar(snapshot);

    expect(almacen.leer('variante-1')).toEqual(snapshot);
  });

  it('guardar varios no pisa los de otras variantes', () => {
    almacen.guardar(snapshotDePrueba('variante-1'));
    almacen.guardar(snapshotDePrueba('variante-2'));

    expect(almacen.leer('variante-1')?.varianteId).toBe('variante-1');
    expect(almacen.leer('variante-2')?.varianteId).toBe('variante-2');
  });
});
