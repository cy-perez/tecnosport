import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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

function repositorioCon(items: readonly MedidaDeVariante[]) {
  return new RepositorioMedicionFalso(
    { total: 0, totalEnPublicados: 0, items: [] },
    { total: 0, totalSinExistencia: 0, totalSinExistenciaEnPublicados: 0, items: [] },
    catalogo(items),
  );
}

async function renderPagina(items: readonly MedidaDeVariante[]) {
  const repositorio = repositorioCon(items);
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

/**
 * Con un router de verdad y la página montada en una ruta, porque el filtro vive en la URL: lo que
 * se prueba es que la URL y la tabla digan lo mismo, y un `ActivatedRoute` falso solo comprobaría
 * la mitad.
 */
async function navegarAPagina(url: string, items: readonly MedidaDeVariante[]) {
  const repositorio = repositorioCon(items);
  TestBed.configureTestingModule({
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([{ path: 'medidas', component: MedidasAdminPage }]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_PRODUCTOS_ADMIN, useValue: repositorio },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  return { repositorio, router: TestBed.inject(Router), harness };
}

function casillaSoloSinMedir(total: number): HTMLInputElement {
  return screen.getByLabelText(
    esAdmin.productos.medidas.soloSinMedir.replace('{{total}}', String(total)),
  ) as HTMLInputElement;
}

async function abrirFormulario(etiqueta: string, sku: string) {
  const boton = await screen.findByRole('button', { name: etiqueta.replace('{{sku}}', sku) });
  fireEvent.click(boton);
}

describe('MedidasAdminPage', () => {
  /**
   * Lo que separaba esta pantalla de la de sin-medir, cuando eran dos: la variante ya medida sale,
   * y con sus cifras. En la otra desaparecía justo cuando se medía.
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

  // Las cuatro son `Validators.required` y ninguna lo declaraba: "Peso, editar" sin "obligatorio".
  it('las cuatro medidas se declaran obligatorias', async () => {
    await renderPagina([sinMedir()]);
    await abrirFormulario(esAdmin.productos.medidas.medirVariante, 'TS-MOTO-G17');
    await screen.findByLabelText(esAdmin.productos.agregarVariante.pesoGramos);

    for (const etiqueta of [
      esAdmin.productos.agregarVariante.pesoGramos,
      esAdmin.productos.agregarVariante.largoCm,
      esAdmin.productos.agregarVariante.anchoCm,
      esAdmin.productos.agregarVariante.altoCm,
    ]) {
      expect(screen.getByLabelText(etiqueta).getAttribute('aria-required'), etiqueta).toBe('true');
    }
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

    expect(await screen.findByText(esAdmin.productos.medidas.faltanCampos)).toBeTruthy();
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

  // Con el formulario cerrado no se auditan ni las cuatro etiquetas del paquete ni el
  // `aria-controls` de la fila, que es justo la mitad interactiva de la pantalla.
  it('tampoco con el formulario de medida abierto', async () => {
    const { container } = await renderPagina([medida(), sinMedir()]);
    await abrirFormulario(esAdmin.productos.medidas.corregirVariante, 'JBL-GO-5');
    await screen.findByLabelText(esAdmin.productos.agregarVariante.pesoGramos);

    await esperarSinViolaciones(container);
  });

  /**
   * El filtro reemplazó a la pantalla de sin-medir, y es adonde lleva el aviso del panel: entrar
   * con él puesto tiene que enseñar solo lo que falta, con la casilla marcada.
   */
  describe('con el filtro de lo que falta', () => {
    it('al entrar con ?filtro=sin-medir solo enseña las que faltan', async () => {
      await navegarAPagina('/medidas?filtro=sin-medir', [medida(), sinMedir()]);

      expect(await screen.findByText('Moto G17')).toBeTruthy();
      expect(screen.queryByText('JBL Go 5')).toBeNull();
      expect(casillaSoloSinMedir(1).checked).toBe(true);
    });

    it('sin el filtro enseña todas, con la casilla sin marcar', async () => {
      await navegarAPagina('/medidas', [medida(), sinMedir()]);

      expect(await screen.findByText('JBL Go 5')).toBeTruthy();
      expect(screen.getByText('Moto G17')).toBeTruthy();
      expect(casillaSoloSinMedir(1).checked).toBe(false);
    });

    /** Ida y vuelta: el filtro que se pone se puede quitar, y la URL sigue a la casilla. */
    it('la casilla pone y quita el filtro en la URL', async () => {
      const { router } = await navegarAPagina('/medidas', [medida(), sinMedir()]);
      await screen.findByText('JBL Go 5');

      fireEvent.click(casillaSoloSinMedir(1));
      await waitFor(() => expect(router.url).toBe('/medidas?filtro=sin-medir'));
      await waitFor(() => expect(screen.queryByText('JBL Go 5')).toBeNull());

      fireEvent.click(casillaSoloSinMedir(1));
      await waitFor(() => expect(router.url).toBe('/medidas'));
      expect(await screen.findByText('JBL Go 5')).toBeTruthy();
    });

    /**
     * Lo que hacía la pantalla vieja y no se puede perder: la fila medida sale de la lista, y la
     * confirmación queda, porque vive fuera de la tabla.
     */
    it('medir una variante la saca de la lista y deja la confirmación', async () => {
      const { repositorio } = await navegarAPagina('/medidas?filtro=sin-medir', [
        medida(),
        sinMedir(),
      ]);
      await abrirFormulario(esAdmin.productos.medidas.medirVariante, 'TS-MOTO-G17');

      for (const [etiqueta, valor] of [
        [esAdmin.productos.agregarVariante.pesoGramos, '430'],
        [esAdmin.productos.agregarVariante.largoCm, '18'],
        [esAdmin.productos.agregarVariante.anchoCm, '10'],
        [esAdmin.productos.agregarVariante.altoCm, '5'],
      ]) {
        fireEvent.input(await screen.findByLabelText(etiqueta), { target: { value: valor } });
      }
      fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.medidas.guardar }));

      expect(
        await screen.findByText(esAdmin.productos.medidas.medida.replace('{{sku}}', 'TS-MOTO-G17')),
      ).toBeTruthy();
      await waitFor(() => expect(screen.queryByText('Moto G17')).toBeNull());
      expect(repositorio.medidas).toHaveLength(1);
      expect(await screen.findByText(esAdmin.productos.medidas.ningunaSinMedir)).toBeTruthy();
    });

    /**
     * Con todo medido no queda una tabla vacía, y la casilla sigue ahí: sin ella no habría por
     * dónde volver a ver las medidas ya tomadas.
     */
    it('si no falta ninguna lo dice, y deja la casilla para quitar el filtro', async () => {
      await navegarAPagina('/medidas?filtro=sin-medir', [medida()]);

      expect(await screen.findByText(esAdmin.productos.medidas.ningunaSinMedir)).toBeTruthy();
      expect(screen.queryByRole('table')).toBeNull();
      expect(casillaSoloSinMedir(0).checked).toBe(true);
    });

    it('no tiene violaciones de accesibilidad', async () => {
      const { harness } = await navegarAPagina('/medidas?filtro=sin-medir', [medida(), sinMedir()]);
      await screen.findByText('Moto G17');

      await esperarSinViolaciones(harness.routeNativeElement as HTMLElement);
    });
  });
});
