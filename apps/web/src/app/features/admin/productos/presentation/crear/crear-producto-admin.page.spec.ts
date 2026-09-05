import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { Categoria, Marca } from '../../../../catalogo/domain/producto.model';
import { REPOSITORIO_CATEGORIAS, RepositorioCategorias } from '../../../../catalogo/domain/repositorio-categorias.puerto';
import { REPOSITORIO_MARCAS, RepositorioMarcas } from '../../../../catalogo/domain/repositorio-marcas.puerto';
import { CrearProductoAdmin, ProductoAdmin, ProductosPaginadosAdmin } from '../../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN, RepositorioProductosAdmin } from '../../domain/repositorio-productos-admin.puerto';
import { CrearProductoAdminPage } from './crear-producto-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'TecnoSport' };
const CATEGORIA: Categoria = { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' };

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [MARCA];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CATEGORIA];
  }
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasCrear: CrearProductoAdmin[] = [];

  constructor(private errorAlCrear = false) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    return { items: [], pagina: 0, totalPaginas: 0, totalProductos: 0 };
  }

  async crear(comando: CrearProductoAdmin): Promise<ProductoAdmin> {
    this.llamadasCrear.push(comando);
    if (this.errorAlCrear) {
      throw new Error('falló');
    }
    return {
      id: 'p1',
      nombre: comando.nombre,
      descripcion: comando.descripcion,
      slug: 'slug-generado',
      estado: 'BORRADOR',
      marca: MARCA,
      categoria: CATEGORIA,
      imagenPrincipalUrl: null,
      totalVariantes: 0,
    };
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
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function renderPagina(repositorioProductos: RepositorioProductosAdmin) {
  return render(CrearProductoAdminPage, {
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
      { provide: REPOSITORIO_PRODUCTOS_ADMIN, useValue: repositorioProductos },
      { provide: REPOSITORIO_MARCAS, useValue: new RepositorioMarcasFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
    ],
  });
}

async function llenarYEnviar() {
  fireEvent.input(screen.getByLabelText('Nombre'), { target: { value: 'Morral urbano' } });
  await screen.findByRole('option', { name: 'TecnoSport' });
  fireEvent.change(screen.getByLabelText('Marca'), { target: { value: 'm1' } });
  fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'c1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear producto' }));
  await esperar(50);
}

describe('CrearProductoAdminPage', () => {
  it('el botón crear arranca deshabilitado con el formulario vacío', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(screen.getByRole('button', { name: 'Crear producto' }).hasAttribute('disabled')).toBe(true);
  });

  it('carga las opciones de marca y categoría en los selects', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(await screen.findByRole('option', { name: 'TecnoSport' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Bolsos' })).toBeTruthy();
  });

  it('al enviar exitosamente, crea el producto y navega a la lista', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    await llenarYEnviar();

    expect(repositorio.llamadasCrear).toEqual([
      { nombre: 'Morral urbano', descripcion: '', marcaId: 'm1', categoriaId: 'c1' },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos']);
  });

  it('con un error del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(true));

    await llenarYEnviar();

    expect(screen.getByText('No se pudo crear el producto. Intenta de nuevo.')).toBeTruthy();
  });
});
