import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { Categoria, Marca } from '../../../../catalogo/domain/producto.model';
import { REPOSITORIO_CATEGORIAS, RepositorioCategorias } from '../../../../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../../../../catalogo/domain/repositorio-marcas.puerto';
import { EditarProductoAdmin, ProductoAdmin, ProductosPaginadosAdmin } from '../../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN, RepositorioProductosAdmin } from '../../domain/repositorio-productos-admin.puerto';
import { EditarProductoAdminPage } from './editar-producto-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'TecnoSport' };
const OTRA_MARCA: Marca = { id: 'm2', nombre: 'Under Trail' };
const CATEGORIA: Categoria = { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' };
const OTRA_CATEGORIA: Categoria = { id: 'c2', nombre: 'Celulares', slug: 'celulares', linea: 'CELULARES' };

function productoDePrueba(): ProductoAdmin {
  return {
    id: 'p1',
    nombre: 'Morral urbano',
    descripcion: 'Descripción original',
    slug: 'morral-urbano',
    estado: 'BORRADOR',
    marca: MARCA,
    categoria: CATEGORIA,
    imagenPrincipalUrl: null,
    totalVariantes: 0,
  };
}

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [MARCA, OTRA_MARCA];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CATEGORIA, OTRA_CATEGORIA];
  }
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasEditar: { id: string; comando: EditarProductoAdmin }[] = [];

  constructor(
    private producto: ProductoAdmin | null = productoDePrueba(),
    private errorAlEditar = false,
  ) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async crear(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async obtener(): Promise<ProductoAdmin> {
    if (!this.producto) {
      throw new Error('no encontrado');
    }
    return this.producto;
  }

  async editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin> {
    this.llamadasEditar.push({ id, comando });
    if (this.errorAlEditar) {
      throw new Error('falló');
    }
    return { ...productoDePrueba(), nombre: comando.nombre, descripcion: comando.descripcion };
  }

  async agregarVariante(): Promise<void> {
    throw new Error('No usado en estas pruebas.');
  }
}

function activatedRouteConId(id: string) {
  const paramMap = convertToParamMap({ id });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(repositorioProductos: RepositorioProductosAdmin, id = 'p1') {
  return render(EditarProductoAdminPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'admin/es': esAdmin } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: REPOSITORIO_PRODUCTOS_ADMIN, useValue: repositorioProductos },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
      { provide: ActivatedRoute, useValue: activatedRouteConId(id) },
    ],
  });
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe('EditarProductoAdminPage', () => {
  it('prellena el formulario con los datos del producto', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(await screen.findByDisplayValue('Morral urbano')).toBeTruthy();
    expect(screen.getByDisplayValue('Descripción original')).toBeTruthy();
  });

  it('con un id inexistente, muestra el error de carga', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(null));

    expect(await screen.findByText('No se pudo cargar el producto.')).toBeTruthy();
  });

  it('al guardar, edita el producto con los datos del formulario y navega a la lista', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');
    await screen.findByDisplayValue('Morral urbano');

    fireEvent.input(screen.getByLabelText('Nombre'), { target: { value: 'Morral renovado' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await esperar(50);

    expect(repositorio.llamadasEditar).toEqual([
      {
        id: 'p1',
        comando: { nombre: 'Morral renovado', descripcion: 'Descripción original', marcaId: 'm1', categoriaId: 'c1' },
      },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos']);
  });

  it('con un error del servidor al guardar, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(productoDePrueba(), true));
    await screen.findByDisplayValue('Morral urbano');

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await esperar(50);

    expect(screen.getByText('No se pudo editar el producto. Intenta de nuevo.')).toBeTruthy();
  });
});
