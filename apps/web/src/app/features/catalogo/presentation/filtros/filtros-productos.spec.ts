import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import {
  provideTransloco,
  provideTranslocoScope,
  Translation,
  TranslocoLoader,
  TranslocoService,
  TranslocoTestingModule,
} from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { firstValueFrom } from 'rxjs';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import esCatalogo from '../../../../../assets/i18n/scopes/catalogo/es.json';
import { Categoria, Marca } from '../../domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../../domain/repositorio-marcas.puerto';
import { FiltrosProductos } from './filtros-productos';

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [
      { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
      { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'CELULARES' },
    ];
  }
}

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [{ id: 'm1', nombre: 'TecnoSport' }];
  }
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function renderFiltros() {
  return render(FiltrosProductos, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'catalogo/es': esCatalogo } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
      { provide: REPOSITORIO_MARCAS, useClass: RepositorioMarcasFalso },
    ],
  });
}

/**
 * Reproduce el escenario real que `TranslocoTestingModule` con
 * `preloadLangs: true` no reproduce: el scope `catalogo` es perezoso
 * (`catalogo.routes.ts`) y su JSON llega por HTTP *después* del primer
 * render. Con un `transloco.translate()` dentro de un `computed()`, las
 * etiquetas de "Línea" y "Ordenar por" se quedaban con la clave cruda en
 * pantalla para siempre — el computed se evaluaba una sola vez, antes de la
 * carga, y nada reactivo lo volvía a disparar.
 */
class LoaderRetardado implements TranslocoLoader {
  async getTranslation(langOScope: string): Promise<Translation> {
    await esperar(150);
    const disponibles: Record<string, Translation> = {
      es: es as Translation,
      en: en as Translation,
      'catalogo/es': esCatalogo as Translation,
    };
    return disponibles[langOScope] ?? {};
  }
}

function proveedoresConScopePerezoso() {
  return [
    provideTransloco({
      config: { availableLangs: ['es', 'en'], defaultLang: 'es', reRenderOnLangChange: true },
      loader: LoaderRetardado,
    }),
    provideTranslocoScope('catalogo'),
    provideRouter([]),
    provideTanStackQuery(new QueryClient()),
    { provide: REPOSITORIO_CATEGORIAS, useClass: RepositorioCategoriasFalso },
    { provide: REPOSITORIO_MARCAS, useClass: RepositorioMarcasFalso },
  ];
}

async function renderFiltrosConScopePerezoso() {
  return render(FiltrosProductos, {
    providers: proveedoresConScopePerezoso(),
  });
}

function etiquetasDe(select: HTMLElement): string[] {
  return Array.from(select.querySelectorAll('option')).map(
    (opcion) => opcion.textContent?.trim() ?? '',
  );
}

describe('FiltrosProductos', () => {
  it('elegir una línea navega con ese query param, con debounce', async () => {
    const { fixture } = await renderFiltros();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.change(await screen.findByLabelText('Línea'), { target: { value: 'BOLSOS' } });
    expect(navegar).not.toHaveBeenCalled();

    await esperar(350);

    // `orden` viaja en la URL desde que el visitante toca cualquier filtro: el
    // control arranca en el orden por defecto (el mismo que ya aplica el
    // backend), no en vacío. Efecto lateral aceptado a cambio de que el select
    // no salga en blanco en la primera pintada.
    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { linea: 'BOLSOS', orden: 'RELEVANCIA' } }),
    );
  });

  it('con el scope precargado en la ruta, el primer render ya trae las etiquetas', async () => {
    // Lo que hace `precargarScopeI18n` en el `resolve` de catalogo.routes.ts,
    // aquí a mano. Sin él el primer render sale con las opciones en blanco;
    // con él, ya vienen traducidas y no hay parpadeo.
    TestBed.configureTestingModule({ providers: proveedoresConScopePerezoso() });
    await firstValueFrom(TestBed.inject(TranslocoService).load('catalogo/es'));

    const fixture = TestBed.createComponent(FiltrosProductos);
    fixture.detectChanges();

    const orden = fixture.nativeElement.querySelector('#filtro-orden') as HTMLElement;
    expect(etiquetasDe(orden)).toEqual([
      'Relevancia',
      'Precio: menor a mayor',
      'Precio: mayor a menor',
      'Más recientes',
    ]);
  });

  it('si el scope llega tarde, las etiquetas se corrigen solas', async () => {
    await renderFiltrosConScopePerezoso();

    await esperar(400);

    const linea = await screen.findByLabelText('Línea');
    const orden = await screen.findByLabelText('Ordenar por');

    expect(etiquetasDe(linea)).toContain('Ropa y calzado');
    expect(etiquetasDe(orden)).toContain('Relevancia');

    // Ni la clave cruda ni la etiqueta vacía: los dos síntomas de leer una
    // traducción antes de que su scope perezoso haya cargado.
    const todas = [...etiquetasDe(linea), ...etiquetasDe(orden)];
    expect(todas.filter((etiqueta) => etiqueta.startsWith('catalogo.') || etiqueta === '')).toEqual(
      [],
    );
  });

  it('sin `orden` en la URL, "Ordenar por" ya muestra Relevancia', async () => {
    // El defecto del recorrido en el navegador: el control arrancaba en `''`,
    // que no corresponde a ninguna `<option>`, y este es el único select de los
    // filtros sin placeholder que lo cubriera — salía en blanco mientras el
    // backend sí estaba ordenando por relevancia.
    await renderFiltros();

    const orden = (await screen.findByLabelText('Ordenar por')) as HTMLSelectElement;

    expect(orden.value).toBe('RELEVANCIA');
    expect(orden.selectedOptions[0]?.textContent?.trim()).toBe('Relevancia');
  });

  it('"Limpiar filtros" devuelve "Ordenar por" a Relevancia, no a vacío', async () => {
    await renderFiltros();

    const orden = (await screen.findByLabelText('Ordenar por')) as HTMLSelectElement;
    fireEvent.change(orden, { target: { value: 'PRECIO_ASC' } });
    expect(orden.value).toBe('PRECIO_ASC');

    fireEvent.click(await screen.findByText('Limpiar filtros'));

    // `form.reset()` vuelve al valor inicial del control, y por eso ese valor
    // inicial es el orden por defecto y no `''`: si no, limpiar reproducía el
    // mismo blanco que se acaba de corregir.
    expect(orden.value).toBe('RELEVANCIA');
  });

  it('"Limpiar filtros" navega sin query params, sin esperar el debounce', async () => {
    const { fixture } = await renderFiltros();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(await screen.findByText('Limpiar filtros'));

    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {} }));
  });
});
