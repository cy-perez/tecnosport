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
    return { items: this.items, pagina: 0, totalPaginas: this.totalPaginas, totalProductos: this.items.length };
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

  it('la paginación deshabilita "Anterior" y "Siguiente" en una sola página', async () => {
    await renderLista([productoDePrueba()]);
    await screen.findByText('Morral urbano');

    expect(screen.getByRole('button', { name: 'Anterior' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: 'Siguiente' }).hasAttribute('disabled')).toBe(true);
  });

  it('"Siguiente" queda habilitado cuando hay más páginas y navega con el query param', async () => {
    const { fixture } = await renderLista([productoDePrueba()], 2);
    await screen.findByText('Morral urbano');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { pagina: 2 } }));
  });
});
