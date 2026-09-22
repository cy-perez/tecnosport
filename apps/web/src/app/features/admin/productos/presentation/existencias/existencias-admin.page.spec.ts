import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { RepositorioExistenciasFalso } from '../../../../../../testing/productos-admin';
import { ExistenciaDeVariante, ExistenciasDelCatalogo } from '../../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../../domain/repositorio-productos-admin.puerto';
import { ExistenciasAdminPage } from './existencias-admin.page';

function existencia(overrides: Partial<ExistenciaDeVariante> = {}): ExistenciaDeVariante {
  return {
    varianteId: 'v1',
    productoId: 'p1',
    nombreProducto: 'Moto G17',
    sku: 'TS-MOTO-G17-NEGRO',
    estadoProducto: 'PUBLICADO',
    saldoTotal: 5,
    disponible: 5,
    reservadas: 0,
    ...overrides,
  };
}

function catalogo(items: readonly ExistenciaDeVariante[]): ExistenciasDelCatalogo {
  const sinExistencia = items.filter((v) => v.saldoTotal === 0);
  return {
    total: items.length,
    totalSinExistencia: sinExistencia.length,
    totalSinExistenciaEnPublicados: sinExistencia.filter((v) => v.estadoProducto === 'PUBLICADO')
      .length,
    items,
  };
}

async function renderPagina(items: readonly ExistenciaDeVariante[]) {
  const repositorio = new RepositorioExistenciasFalso(catalogo(items));
  const resultado = await render(ExistenciasAdminPage, {
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

async function abrirFormulario(sku: string) {
  const boton = await screen.findByRole('button', {
    name: esAdmin.productos.existencias.contarVariante.replace('{{sku}}', sku),
  });
  fireEvent.click(boton);
}

describe('ExistenciasAdminPage', () => {
  it('enseña las dos cifras por separado, que es de lo que trata la pantalla', async () => {
    await renderPagina([existencia({ saldoTotal: 5, disponible: 3, reservadas: 2 })]);

    expect(await screen.findByText('Moto G17')).toBeTruthy();
    const celdas = screen.getAllByRole('cell');
    const textos = celdas.map((celda) => celda.textContent?.trim());
    expect(textos).toContain('5');
    expect(textos.some((texto) => texto?.startsWith('3'))).toBe(true);
  });

  /**
   * Que algo publicado no tenga ni una unidad se dice con palabras: un color no lo lee quien usa
   * lector de pantalla, y es además el criterio que ordena la lista.
   */
  it('marca con texto la variante que el libro deja en cero', async () => {
    await renderPagina([existencia({ saldoTotal: 0, disponible: 0 })]);

    expect(await screen.findByText(esAdmin.productos.existencias.sinExistencia)).toBeTruthy();
  });

  it('sin variantes que contar lo dice, en vez de dejar la pantalla en blanco', async () => {
    await renderPagina([]);

    expect(await screen.findByText(esAdmin.productos.existencias.ninguna)).toBeTruthy();
  });

  it('manda el conteo y el motivo, no la diferencia', async () => {
    const { repositorio } = await renderPagina([existencia({ saldoTotal: 5 })]);
    await abrirFormulario('TS-MOTO-G17-NEGRO');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.existencias.cantidadContada), {
      target: { value: '8' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.existencias.motivo), {
      target: { value: 'Conteo físico de bodega' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.existencias.guardar }));

    await waitFor(() => {
      expect(repositorio.ajustes).toEqual([
        { varianteId: 'v1', cantidadContada: 8, motivo: 'Conteo físico de bodega' },
      ]);
    });
  });

  /**
   * Sin motivo no se manda nada, y el botón <b>no</b> se deshabilita: un `<button disabled>` sale
   * del orden de tabulación y quien navega con teclado no llega a enterarse de por qué no pasa
   * nada.
   */
  it('sin motivo no manda nada y dice qué falta', async () => {
    const { repositorio } = await renderPagina([existencia()]);
    await abrirFormulario('TS-MOTO-G17-NEGRO');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.existencias.cantidadContada), {
      target: { value: '8' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.existencias.guardar }));

    expect(await screen.findByText(esAdmin.productos.existencias.faltanCampos)).toBeTruthy();
    expect(repositorio.ajustes).toEqual([]);
  });

  /** Contar lo mismo es un resultado, no un error, y tiene su propio mensaje. */
  it('contar lo mismo se confirma como "sin novedad"', async () => {
    await renderPagina([existencia({ saldoTotal: 5 })]);
    await abrirFormulario('TS-MOTO-G17-NEGRO');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.existencias.cantidadContada), {
      target: { value: '5' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.existencias.motivo), {
      target: { value: 'Conteo físico de bodega' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.existencias.guardar }));

    expect(
      await screen.findByText(
        esAdmin.productos.existencias.sinCambios
          .replace('{{sku}}', 'TS-MOTO-G17-NEGRO')
          .replace('{{unidades}}', '5'),
      ),
    ).toBeTruthy();
  });

  /**
   * El aviso que no se puede callar: hay compras aceptadas por encima de lo contado. Se graba
   * igual —la realidad es la que es— pero quien acaba de contar tiene que leerlo.
   */
  it('avisa cuando el conteo deja pedidos en vuelo sin respaldo', async () => {
    await renderPagina([existencia({ saldoTotal: 5, disponible: 3, reservadas: 2 })]);
    await abrirFormulario('TS-MOTO-G17-NEGRO');

    fireEvent.input(await screen.findByLabelText(esAdmin.productos.existencias.cantidadContada), {
      target: { value: '1' },
    });
    fireEvent.input(screen.getByLabelText(esAdmin.productos.existencias.motivo), {
      target: { value: 'Solo queda una' },
    });
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.existencias.guardar }));

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina([existencia({ saldoTotal: 0, disponible: 0 })]);
    await screen.findByText('Moto G17');

    await esperarSinViolaciones(container);
  });

  // La mitad interactiva de esta pantalla vive dentro del formulario, y con el formulario cerrado
  // no se audita ninguna de sus etiquetas, ni el `aria-controls` que la fila acaba de estrenar.
  it('tampoco con el formulario de conteo abierto', async () => {
    const { container } = await renderPagina([existencia({ saldoTotal: 0, disponible: 0 })]);
    await abrirFormulario('TS-MOTO-G17-NEGRO');

    await esperarSinViolaciones(container);
  });
});
