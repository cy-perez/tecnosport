import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { ProductoAdmin, ProductosPaginadosAdmin } from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../../domain/repositorio-productos-admin.puerto';
import { ListaProductosAdminPage } from './lista-productos-admin.page';

function productoDePrueba(overrides: Partial<ProductoAdmin> = {}): ProductoAdmin {
  return {
    id: 'p1',
    nombre: 'Morral urbano',
    descripcion: '',
    slug: 'morral-urbano',
    estado: 'BORRADOR',
    marca: { id: 'm1', nombre: 'TecnoSport' },
    categoria: { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' },
    imagenPrincipalUrl: null,
    totalVariantes: 2,
    ...overrides,
  };
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasListar = 0;

  constructor(
    private items: ProductoAdmin[],
    private totalPaginas = 1,
  ) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    this.llamadasListar++;
    return {
      items: this.items,
      pagina: 0,
      totalPaginas: this.totalPaginas,
      totalProductos: this.items.length,
    };
  }

  async crear(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async obtener(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async editar(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async agregarVariante(): Promise<void> {
    throw new Error('No usado en estas pruebas.');
  }

  async subirImagenPrincipal(): Promise<never> {
    throw new Error('No usado en estas pruebas.');
  }
}

async function renderLista(items: ProductoAdmin[], totalPaginas = 1) {
  const repositorio = new RepositorioProductosAdminFalso(items, totalPaginas);
  const resultado = await render(ListaProductosAdminPage, {
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

describe('ListaProductosAdminPage', () => {
  it('lista los productos con sus columnas principales', async () => {
    await renderLista([productoDePrueba()]);

    expect(await screen.findByText('Morral urbano')).toBeTruthy();
    expect(screen.getByText('TecnoSport')).toBeTruthy();
    expect(screen.getByText('Bolsos')).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'Borrador' })).toBeTruthy();
  });

  it('sin productos lo dice en vez de dejar una tabla vacía', async () => {
    await renderLista([], 0);

    expect(
      await screen.findByText('Todavía no hay productos. Crea el primero con «Nuevo producto».'),
    ).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Siguiente' })).toBeNull();
    // El enlace de crear sigue arriba, fuera de la rama vacía: es la salida.
    expect(screen.getByRole('link', { name: 'Nuevo producto' })).toBeTruthy();
  });

  it('la paginación deshabilita "Anterior" y "Siguiente" en una sola página', async () => {
    await renderLista([productoDePrueba()]);
    await screen.findByText('Morral urbano');

    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  // Los dos enlaces de acción se pintaban pegados ("EditarCapturar 360"): entre
  // dos elementos en línea sin espacio en el HTML no hay nada que separe. Estaba
  // anotado como pendiente cosmético desde la Fase 5 y sobrevivió entero a la
  // migración a Tailwind, porque ninguna prueba miraba separaciones.
  //
  // Se afirma sobre el padre común y no sobre el texto renderizado: en jsdom
  // `innerText` no existe y `textContent` concatena igual estén separados o no,
  // así que la única forma de que esta prueba falle si alguien quita el `flex`
  // es mirar las clases que producen la separación.
  it('los enlaces de acción de una fila van separados, no pegados', async () => {
    await renderLista([productoDePrueba()]);
    await screen.findByText('Morral urbano');

    const editar = screen.getByRole('link', { name: 'Editar' });
    const capturar = screen.getByRole('link', { name: 'Capturar 360' });
    const contenedor = editar.parentElement!;

    expect(capturar.parentElement).toBe(contenedor);
    expect(contenedor.className).toContain('flex');
    expect(contenedor.className).toContain('gap-16');
  });

  // El anillo de foco de la marca, no el del navegador. Encontrado recorriendo
  // el sitio: este enlace y otros cinco no lo llevaban y caían al `outline: auto`
  // por omisión — visible en Chrome, pero no es el del sistema y cada navegador
  // dibuja el suyo. Que `anillo-foco` exista como clase lo garantiza
  // `npm run clases`; que esté puesta, esta prueba.
  it('"Nuevo producto" lleva el anillo de foco de la marca', async () => {
    await renderLista([productoDePrueba()]);
    await screen.findByText('Morral urbano');

    expect(screen.getByRole('link', { name: 'Nuevo producto' }).className).toContain(
      'anillo-foco',
    );
  });

  it('"Siguiente" queda habilitado cuando hay más páginas y navega con el query param', async () => {
    const { fixture } = await renderLista([productoDePrueba()], 2);
    await screen.findByText('Morral urbano');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { pagina: 2 } }),
    );
  });
});
