import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { InventarioSinMedir, VarianteSinMedir } from '../../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../../domain/repositorio-productos-admin.puerto';
import { RepositorioMedicionFalso } from '../../../../../../testing/productos-admin';
import { VariantesSinMedirAdminPage } from './variantes-sin-medir-admin.page';

function sinMedir(overrides: Partial<VarianteSinMedir> = {}): VarianteSinMedir {
  return {
    varianteId: 'v1',
    productoId: 'p1',
    nombreProducto: 'Moto G17',
    sku: 'TS-MOTO-G17-NEGRO',
    estadoProducto: 'PUBLICADO',
    ...overrides,
  };
}

function inventario(items: readonly VarianteSinMedir[]): InventarioSinMedir {
  return {
    total: items.length,
    totalEnPublicados: items.filter((v) => v.estadoProducto === 'PUBLICADO').length,
    items,
  };
}

async function renderPagina(items: readonly VarianteSinMedir[]) {
  const repositorio = new RepositorioMedicionFalso(inventario(items));
  const resultado = await render(VariantesSinMedirAdminPage, {
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

describe('VariantesSinMedirAdminPage', () => {
  it('lista cada variante con su producto, su SKU y si ya está publicado', async () => {
    await renderPagina([sinMedir()]);

    expect(await screen.findByText('Moto G17')).toBeTruthy();
    expect(screen.getByText('TS-MOTO-G17-NEGRO')).toBeTruthy();
    expect(screen.getByText(esAdmin.productos.estados.publicado)).toBeTruthy();
  });

  /**
   * Con la lista vacía hay que decirlo. Una pantalla en blanco no distingue "ya está todo medido"
   * de "no cargó", y son cosas opuestas — la misma lección que la bandeja de revisión de envíos.
   */
  it('sin nada que medir lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.productos.sinMedir.ninguna)).toBeTruthy();
  });

  it('mide una variante y manda las cuatro cifras al servidor', async () => {
    const { repositorio } = await renderPagina([sinMedir()]);

    fireEvent.click(await screen.findByRole('button', { name: /Medir la variante/ }));

    fireEvent.input(screen.getByLabelText(esAdmin.productos.agregarVariante.pesoGramos), {
      target: { value: '430' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.agregarVariante.largoCm), {
      target: { value: '17' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.agregarVariante.anchoCm), {
      target: { value: '9' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.agregarVariante.altoCm), {
      target: { value: '5' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.sinMedir.guardar }));

    await vi.waitFor(() => {
      expect(repositorio.medidas).toEqual([
        { varianteId: 'v1', pesoGramos: 430, largoCm: 17, anchoCm: 9, altoCm: 5 },
      ]);
    });
  });

  /**
   * Al medir, la fila desaparece de la lista: el resultado del trabajo es que ya no está. Sin una
   * confirmación aparte, guardar no produce ninguna señal visible de haber funcionado.
   */
  it('confirma nombrando el SKU, porque su fila ya no está para decirlo', async () => {
    await renderPagina([sinMedir()]);

    fireEvent.click(await screen.findByRole('button', { name: /Medir la variante/ }));
    for (const [etiqueta, valor] of [
      [esAdmin.productos.agregarVariante.pesoGramos, '430'],
      [esAdmin.productos.agregarVariante.largoCm, '17'],
      [esAdmin.productos.agregarVariante.anchoCm, '9'],
      [esAdmin.productos.agregarVariante.altoCm, '5'],
    ] as const) {
      fireEvent.input(screen.getByLabelText(etiqueta), { target: { value: valor } });
    }
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.sinMedir.guardar }));

    expect(await screen.findByText(/TS-MOTO-G17-NEGRO/)).toBeTruthy();
  });

  /**
   * El botón no se deshabilita —un `<button disabled>` sale del orden de tabulación—, así que
   * enviar con campos vacíos tiene que decir qué falta en vez de no hacer nada.
   */
  it('con las medidas en blanco dice qué falta y no llama al servidor', async () => {
    const { repositorio } = await renderPagina([sinMedir()]);

    fireEvent.click(await screen.findByRole('button', { name: /Medir la variante/ }));
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.sinMedir.guardar }));

    expect(await screen.findByText(esAdmin.productos.sinMedir.faltanCampos)).toBeTruthy();
    expect(repositorio.medidas).toEqual([]);
  });

  it('no tiene violaciones de accesibilidad con el formulario abierto', async () => {
    const { fixture } = await renderPagina([sinMedir()]);

    fireEvent.click(await screen.findByRole('button', { name: /Medir la variante/ }));
    fixture.detectChanges();

    await esperarSinViolaciones(fixture.nativeElement);
  });

  /**
   * El acuse vive siempre en el DOM y lo que cambia es su contenido: un `role="status"` que nace
   * ya lleno no lo anuncia NVDA (`docs/06-testing.md`). Y el resumen de la tabla ya no es una
   * region viva, porque cada revalidacion en segundo plano lo volveria a leer en voz alta.
   */
  it('deja el acuse en su sitio aunque no haya nada medido, y el resumen fuera de la region', async () => {
    await renderPagina([sinMedir()]);
    await screen.findByText('Moto G17');

    const regiones = screen.getAllByRole('status');
    expect(regiones).toHaveLength(1);
    expect(regiones[0].textContent?.trim()).toBe('');
  });
});
