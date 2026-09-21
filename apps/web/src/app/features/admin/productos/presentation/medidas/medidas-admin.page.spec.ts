import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { RepositorioMedicionFalso } from '../../../../../../testing/productos-admin';
import { MedidaDeVariante, MedidasDelCatalogo } from '../../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../../domain/repositorio-productos-admin.puerto';
import { MedidasAdminPage } from './medidas-admin.page';

function medida(overrides: Partial<MedidaDeVariante> = {}): MedidaDeVariante {
  return {
    varianteId: 'v1',
    productoId: 'p1',
    nombreProducto: 'JBL Go 5',
    sku: 'JBL-GO-5',
    estadoProducto: 'PUBLICADO',
    pesoGramos: 320,
    largoCm: 13,
    anchoCm: 9,
    altoCm: 6,
    sinMedir: false,
    ...overrides,
  };
}

function sinMedir(overrides: Partial<MedidaDeVariante> = {}): MedidaDeVariante {
  return medida({
    varianteId: 'v2',
    nombreProducto: 'Moto G17',
    sku: 'TS-MOTO-G17',
    pesoGramos: null,
    largoCm: null,
    anchoCm: null,
    altoCm: null,
    sinMedir: true,
    ...overrides,
  });
}

function catalogo(items: readonly MedidaDeVariante[]): MedidasDelCatalogo {
  const faltan = items.filter((v) => v.sinMedir);
  return {
    total: items.length,
    totalSinMedir: faltan.length,
    totalSinMedirEnPublicados: faltan.filter((v) => v.estadoProducto === 'PUBLICADO').length,
    items,
  };
}

async function renderPagina(items: readonly MedidaDeVariante[]) {
  const repositorio = new RepositorioMedicionFalso(
    { total: 0, totalEnPublicados: 0, items: [] },
    { total: 0, totalSinExistencia: 0, totalSinExistenciaEnPublicados: 0, items: [] },
    catalogo(items),
  );
  const resultado = await render(MedidasAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PRODUCTOS_ADMIN, useValue: repositorio },
    ],
  });
  return { ...resultado, repositorio };
}

async function abrirFormulario(etiqueta: string, sku: string) {
  const boton = await screen.findByRole('button', { name: etiqueta.replace('{{sku}}', sku) });
  fireEvent.click(boton);
}

describe('MedidasAdminPage', () => {
  /**
   * Lo que separa esta pantalla de la de sin-medir: la variante ya medida sale, y con sus cifras.
   * Es la razón entera de que exista — en la otra desaparece justo cuando se mide.
   */
  it('lista también las variantes ya medidas, con su peso y sus dimensiones', async () => {
    await renderPagina([medida()]);

    expect(await screen.findByText('JBL Go 5')).toBeTruthy();
    expect(screen.getByText('320 g')).toBeTruthy();
    expect(screen.getByText('13 × 9 × 6 cm')).toBeTruthy();
  });

  it('dice con palabras cuál está sin medir, no con una celda vacía', async () => {
    await renderPagina([sinMedir()]);

    expect(await screen.findByText(esAdmin.productos.medidas.sinMedir)).toBeTruthy();
  });

  /**
   * El formulario llega con lo que ya hay. Corregir un peso mal tecleado es cambiar un número, y
   * un formulario en blanco obliga a copiar tres cifras correctas para tocar la cuarta.
   */
  it('al corregir, el formulario arranca con las medidas actuales', async () => {
    await renderPagina([medida()]);
    await abrirFormulario(esAdmin.productos.medidas.corregirVariante, 'JBL-GO-5');

    const peso = await screen.findByLabelText(esAdmin.productos.agregarVariante.pesoGramos);
    expect((peso as HTMLInputElement).value).toBe('320');
    expect(
      (screen.getByLabelText(esAdmin.productos.agregarVariante.largoCm) as HTMLInputElement).value,
    ).toBe('13');
  });

  it('medir una que no tenía medidas deja el formulario en blanco', async () => {
    await renderPagina([sinMedir()]);
    await abrirFormulario(esAdmin.productos.medidas.medirVariante, 'TS-MOTO-G17');

    const peso = await screen.findByLabelText(esAdmin.productos.agregarVariante.pesoGramos);
    expect((peso as HTMLInputElement).value).toBe('');
  });

  it('guarda la corrección y lo dice como corrección, no como medición nueva', async () => {
    const { repositorio } = await renderPagina([medida()]);
    await abrirFormulario(esAdmin.productos.medidas.corregirVariante, 'JBL-GO-5');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.agregarVariante.largoCm), {
      target: { value: '14' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.agregarVariante.anchoCm), {
      target: { value: '10' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.medidas.guardar }));

    await waitFor(() => {
      expect(repositorio.medidas).toEqual([
        { varianteId: 'v1', pesoGramos: 320, largoCm: 14, anchoCm: 10, altoCm: 6 },
      ]);
    });
    expect(
      await screen.findByText(esAdmin.productos.medidas.corregida.replace('{{sku}}', 'JBL-GO-5')),
    ).toBeTruthy();
  });

  /** Sin las cuatro cifras no se manda nada, y se dice qué falta en vez de solo marcar en rojo. */
  it('un formulario incompleto no llega al servidor', async () => {
    const { repositorio } = await renderPagina([sinMedir()]);
    await abrirFormulario(esAdmin.productos.medidas.medirVariante, 'TS-MOTO-G17');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.agregarVariante.pesoGramos), {
      target: { value: '430' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.medidas.guardar }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(repositorio.medidas).toEqual([]);
  });

  it('sin variantes activas lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.productos.medidas.ninguna)).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([medida(), sinMedir()]);
    await screen.findByText('JBL Go 5');

    await esperarSinViolaciones(container);
  });
});
