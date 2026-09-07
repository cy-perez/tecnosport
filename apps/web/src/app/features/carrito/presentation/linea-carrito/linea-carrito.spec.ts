import { TranslocoTestingModule } from '@jsverse/transloco';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCarrito from '../../../../../assets/i18n/scopes/carrito/es.json';
import { LineaCarrito } from '../../domain/carrito.model';
import { SnapshotLinea } from '../../domain/snapshot-linea.model';
import { LineaCarritoComponent } from './linea-carrito';

function lineaDePrueba(): LineaCarrito {
  return { id: 'linea-1', varianteId: 'variante-1', cantidad: 2 };
}

function snapshotDePrueba(): SnapshotLinea {
  return {
    varianteId: 'variante-1',
    nombreProducto: 'Morral urbano',
    slugProducto: 'morral-urbano',
    sku: 'SKU-1',
    imagenUrl: null,
    imagenAlt: 'Morral urbano',
    precioValor: 150_000,
    precioMoneda: 'COP',
  };
}

async function renderLinea(inputs: Record<string, unknown>, on: Record<string, unknown> = {}) {
  return render(LineaCarritoComponent, {
    inputs,
    on,
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'carrito/es': esCarrito } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
  });
}

describe('LineaCarritoComponent', () => {
  it('muestra el nombre y el sku de la foto guardada', async () => {
    await renderLinea({ linea: lineaDePrueba(), snapshot: snapshotDePrueba() });

    expect(screen.getByText('Morral urbano')).toBeTruthy();
    expect(screen.getByText('SKU-1')).toBeTruthy();
  });

  it('sin foto guardada muestra un texto genérico', async () => {
    await renderLinea({ linea: lineaDePrueba(), snapshot: null });

    expect(screen.getByText('Producto')).toBeTruthy();
  });

  it('sumar emite la cantidad actual más uno', async () => {
    let emitido: number | undefined;
    await renderLinea(
      { linea: lineaDePrueba(), snapshot: snapshotDePrueba() },
      { cantidadCambio: (valor: number) => (emitido = valor) },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Aumentar cantidad' }));

    expect(emitido).toBe(3);
  });

  it('restar emite la cantidad actual menos uno', async () => {
    let emitido: number | undefined;
    await renderLinea(
      { linea: lineaDePrueba(), snapshot: snapshotDePrueba() },
      { cantidadCambio: (valor: number) => (emitido = valor) },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Disminuir cantidad' }));

    expect(emitido).toBe(1);
  });

  it('restar en uno no emite (usa eliminar en vez de bajar a cero)', async () => {
    let emitido: number | undefined;
    await renderLinea(
      {
        linea: { id: 'linea-1', varianteId: 'variante-1', cantidad: 1 },
        snapshot: snapshotDePrueba(),
      },
      { cantidadCambio: (valor: number) => (emitido = valor) },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Disminuir cantidad' }));

    expect(emitido).toBeUndefined();
  });

  it('eliminar emite el evento', async () => {
    let eliminado = false;
    await renderLinea(
      { linea: lineaDePrueba(), snapshot: snapshotDePrueba() },
      { eliminar: () => (eliminado = true) },
    );

    fireEvent.click(screen.getByText('Eliminar'));

    expect(eliminado).toBe(true);
  });
});
