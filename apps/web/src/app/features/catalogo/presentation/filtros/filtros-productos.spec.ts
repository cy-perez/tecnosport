import { Type } from '@angular/core';
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

/**
 * Un árbol pequeño pero con las tres formas que importan: una hoja de primer nivel (Celulares), una
 * rama con hojas debajo (Dama › Blusas, Dama › Busos) y una línea sin nada (Bolsos).
 */
class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [
      { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
      { id: 'd0', nombre: 'Dama', slug: 'ropa-dama', linea: 'ROPA', padreId: null },
      { id: 'd1', nombre: 'Blusas', slug: 'ropa-dama-blusas', linea: 'ROPA', padreId: 'd0' },
      { id: 'd2', nombre: 'Busos', slug: 'ropa-dama-busos', linea: 'ROPA', padreId: 'd0' },
      { id: 'u0', nombre: 'Unisex', slug: 'calzado-unisex', linea: 'CALZADO', padreId: null },
    ];
  }
}

/** Solo tecnología: el catálogo que este negocio va a tener el día que abra. */
class RepositorioCategoriasSoloTecnologia implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [
      { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'TECNOLOGIA', padreId: null },
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

async function renderFiltros(categorias: Type<RepositorioCategorias> = RepositorioCategoriasFalso) {
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
      { provide: REPOSITORIO_CATEGORIAS, useClass: categorias },
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

/**
 * El selector de línea ya no se arma de una constante sino de las categorías que trae el servidor,
 * así que hay un instante en el que solo está el placeholder. Se espera por el resultado y no con
 * un `esperar(ms)` fijo, como pide `apps/web/CLAUDE.md`.
 */
async function selectConOpciones(etiqueta: string): Promise<HTMLElement> {
  const select = await screen.findByLabelText(etiqueta);
  await vi.waitFor(() => expect(etiquetasDe(select).length).toBeGreaterThan(1));
  return select;
}

describe('FiltrosProductos', () => {
  // En el teléfono los siete controles ocupaban la primera pantalla entera.
  // El botón solo se pinta por debajo del primer punto de quiebre (clase
  // `desde-movil:hidden`, que jsdom no evalúa), así que aquí se prueba el
  // contrato del *disclosure*: el estado anunciado, a qué región apunta y que
  // el clic lo alterna.
  /**
   * Esta prueba afirmaba lo contrario —que una línea sin categorías cargadas no se ofrecía—, y la
   * decisión se invirtió el 24 de septiembre de 2026 con el árbol: el menú lateral pinta las cuatro
   * ramas siempre, y un desplegable que ofrece tres mientras el menú ofrece cuatro son dos
   * respuestas distintas a la misma pregunta en la misma pantalla. La rejilla ya sabe decir que no
   * encontró nada con esos filtros.
   */
  it('el selector de línea ofrece las cuatro aunque el catálogo sea de pura tecnología', async () => {
    await renderFiltros(RepositorioCategoriasSoloTecnologia);

    fireEvent.click(await screen.findByRole('button', { name: 'Filtrar y ordenar' }));
    const linea = await selectConOpciones('Línea');

    expect(etiquetasDe(linea)).toContain('Ropa');
    expect(etiquetasDe(linea)).toContain('Calzado deportivo');
    expect(etiquetasDe(linea)).toContain('Bolsos');
    expect(etiquetasDe(linea)).toContain('Tecnología');
  });

  it('las cuatro líneas salen en el orden del modelo, no en el alfabético', async () => {
    await renderFiltros();

    fireEvent.click(await screen.findByRole('button', { name: 'Filtrar y ordenar' }));
    const linea = await selectConOpciones('Línea');
    await vi.waitFor(() => expect(etiquetasDe(linea)).toHaveLength(5));

    // Sin el placeholder: el orden es el de LINEAS, que es el del negocio y no el alfabético.
    expect(etiquetasDe(linea).slice(-4)).toEqual([
      'Tecnología',
      'Ropa',
      'Calzado deportivo',
      'Bolsos',
    ]);
  });

  /**
   * De una rama no cuelga ningún producto —lo defiende el backend—, así que ofrecer "Dama" en el
   * filtro sería ofrecer un camino que siempre lleva a una rejilla vacía. Y la ruta en la etiqueta
   * no es adorno: "Busos" existe bajo Dama y bajo Caballero, y sin ella el desplegable tiene dos
   * entradas idénticas.
   */
  it('el selector de categoría ofrece solo hojas, con su ruta completa', async () => {
    await renderFiltros();

    fireEvent.click(await screen.findByRole('button', { name: 'Filtrar y ordenar' }));
    const categoria = await selectConOpciones('Categoría');
    await vi.waitFor(() => expect(etiquetasDe(categoria).length).toBeGreaterThan(1));

    const etiquetas = etiquetasDe(categoria);
    expect(etiquetas).toContain('Ropa › Dama › Blusas');
    expect(etiquetas).toContain('Tecnología › Celulares');
    // "Dama" tiene hojas debajo: no se ofrece ella misma.
    expect(etiquetas).not.toContain('Ropa › Dama');
  });

  it('sin filtros en la URL, arranca plegado y el botón lo despliega', async () => {
    await renderFiltros();

    const boton = await screen.findByRole('button', { name: 'Filtrar y ordenar' });
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(boton.getAttribute('aria-controls')).toBe('filtros-productos');

    fireEvent.click(boton);

    const abierto = await screen.findByRole('button', { name: 'Ocultar filtros' });
    expect(abierto.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById('filtros-productos')).toBeTruthy();
  });

  it('con un filtro en la URL, arranca desplegado: lo aplicado tiene que verse', async () => {
    TestBed.configureTestingModule({
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
    await TestBed.inject(Router).navigateByUrl('/?linea=BOLSOS');

    const fixture = TestBed.createComponent(FiltrosProductos);
    fixture.detectChanges();

    const boton = fixture.nativeElement.querySelector('button[aria-controls]') as HTMLElement;
    expect(boton.getAttribute('aria-expanded')).toBe('true');
  });

  it('elegir una línea navega con ese query param, con debounce', async () => {
    const { fixture } = await renderFiltros();
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    // Hay que esperar a que las opciones existan: un `<select>` ignora un valor que no está entre
    // ellas, y desde que la línea se deduce de las categorías ya no están desde el primer render.
    fireEvent.change(await selectConOpciones('Línea'), { target: { value: 'BOLSOS' } });
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

    expect(etiquetasDe(linea)).toContain('Ropa');
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

  /**
   * El rango de precio salió de la barra el 24 de septiembre de 2026, y con él salió del modelo
   * entero (`filtro-productos.model.ts` explica por qué no bastaba con quitar los dos controles).
   * Se comprueba por tipo de control y no solo por etiqueta: un `<input type="number">` que
   * sobreviva sin etiqueta es exactamente el resto que esta prueba tiene que ver.
   */
  it('ya no hay controles de precio', async () => {
    const { container } = await renderFiltros();

    expect(screen.queryByLabelText('Precio mínimo')).toBeNull();
    expect(screen.queryByLabelText('Precio máximo')).toBeNull();
    expect(container.querySelectorAll('input[type="number"]')).toHaveLength(0);
  });

  /**
   * Las etiquetas de la barra ya no se ven —el nombre del filtro lo dice la opción elegida, y el
   * de la búsqueda su placeholder—, pero siguen existiendo. Es justo la propiedad que se paga con
   * `etiquetaOculta` y la que hay que vigilar: `sr-only` esconde a la vista, no del árbol de
   * accesibilidad, y confundir las dos cosas es la forma fácil de dejar cuatro controles sin
   * nombre.
   */
  it('los controles conservan su etiqueta aunque no se vea, y la búsqueda su placeholder', async () => {
    await renderFiltros();

    for (const etiqueta of ['Línea', 'Categoría', 'Marca', 'Ordenar por']) {
      expect(await screen.findByLabelText(etiqueta)).toBeTruthy();
    }

    const busqueda = await screen.findByLabelText('Buscar');
    expect(busqueda.getAttribute('placeholder')).toBe('Buscar productos…');
  });
});
