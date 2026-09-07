import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { Categoria, Marca } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../../../catalogo/domain/repositorio-categorias.puerto';
import {
  REPOSITORIO_MARCAS,
  RepositorioMarcas,
} from '../../../../catalogo/domain/repositorio-marcas.puerto';
import {
  EditarProductoAdmin,
  ImagenAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  SubirImagenPrincipalAdmin,
} from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../../domain/repositorio-productos-admin.puerto';
import { EditarProductoAdminPage } from './editar-producto-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'TecnoSport' };
const OTRA_MARCA: Marca = { id: 'm2', nombre: 'Under Trail' };
const CATEGORIA: Categoria = { id: 'c1', nombre: 'Bolsos', slug: 'bolsos', linea: 'BOLSOS' };
const OTRA_CATEGORIA: Categoria = {
  id: 'c2',
  nombre: 'Celulares',
  slug: 'celulares',
  linea: 'CELULARES',
};

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
  llamadasSubirImagen: SubirImagenPrincipalAdmin[] = [];

  constructor(
    private producto: ProductoAdmin | null = productoDePrueba(),
    private errorAlEditar = false,
    private errorAlSubirImagen = false,
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

  async subirImagenPrincipal(comando: SubirImagenPrincipalAdmin): Promise<ImagenAdmin> {
    this.llamadasSubirImagen.push(comando);
    if (this.errorAlSubirImagen) {
      throw new Error('falló');
    }
    return {
      url: 'https://storage.googleapis.com/tecnosport-dev-imagenes/objeto.webp',
      urlWebp: 'https://storage.googleapis.com/tecnosport-dev-imagenes/objeto.webp',
      ancho: comando.ancho,
      alto: comando.alto,
      altEs: comando.altEs,
      altEn: comando.altEn,
    };
  }
}

class ImagenDePrueba {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  naturalWidth = 800;
  naturalHeight = 600;

  set src(_valor: string) {
    queueMicrotask(() => this.onload?.());
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


describe('EditarProductoAdminPage', () => {
  it('prellena el formulario con los datos del producto', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(await screen.findByDisplayValue('Morral urbano')).toBeTruthy();
    expect(screen.getByDisplayValue('Descripción original')).toBeTruthy();
  });

  // La marca y la categoría vienen de una consulta distinta a la del producto.
  // Si el producto llega primero, el <select> todavía no tiene la <option> que
  // le corresponde y el valor no engancha: la pantalla mostraba "Selecciona una
  // opción" con el producto ya cargado, y como los dos campos son obligatorios,
  // editar solo el nombre obligaba a volver a elegirlos. Encontrado en el
  // navegador; la prueba de arriba no lo veía porque solo miraba los textos.
  it('prellena también la marca y la categoría, que vienen de otra consulta', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());
    await screen.findByDisplayValue('Morral urbano');

    expect((screen.getByLabelText('Marca') as HTMLSelectElement).value).toBe(MARCA.id);
    expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe(CATEGORIA.id);
  });

  // El enlace relativo generaba `productos/{id}/{id}/variantes/crear` — la ruta
  // de esta pantalla es `:id/editar`, dos segmentos, así que `..` sube uno solo.
  // No existía, y al hacer clic la aplicación caía en la portada.
  it('el enlace de agregar variante apunta a la ruta real, con el id una sola vez', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    const enlace = await screen.findByRole('link', { name: 'Agregar variante' });

    expect(enlace.getAttribute('href')).toBe('/es/admin/productos/p1/variantes/crear');
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
    await vi.waitFor(() => expect(repositorio.llamadasEditar).toHaveLength(1));

    expect(repositorio.llamadasEditar).toEqual([
      {
        id: 'p1',
        comando: {
          nombre: 'Morral renovado',
          descripcion: 'Descripción original',
          marcaId: 'm1',
          categoriaId: 'c1',
        },
      },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos']);
  });

  it('con un error del servidor al guardar, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(productoDePrueba(), true));
    await screen.findByDisplayValue('Morral urbano');

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('No se pudo editar el producto. Intenta de nuevo.')).toBeTruthy();
  });

  describe('imagen principal', () => {
    beforeEach(() => {
      vi.stubGlobal('Image', ImagenDePrueba);
      vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
      vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined);
    });

    afterEach(() => {
      vi.unstubAllGlobals();
      vi.restoreAllMocks();
    });

    function archivoValido(): File {
      return new File(['contenido'], 'imagen.webp', { type: 'image/webp' });
    }

    it('con una imagen válida y los textos alternativos completos, la sube con las dimensiones leídas', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [archivoValido()] },
      });
      await screen.findByLabelText('Texto alternativo (español)');
      fireEvent.input(screen.getByLabelText('Texto alternativo (español)'), {
        target: { value: 'alt es' },
      });
      fireEvent.input(screen.getByLabelText('Texto alternativo (inglés)'), {
        target: { value: 'alt en' },
      });

      fireEvent.click(screen.getByRole('button', { name: 'Subir imagen' }));
      await vi.waitFor(() => expect(repositorio.llamadasSubirImagen).toHaveLength(1));

      expect(repositorio.llamadasSubirImagen).toEqual([
        {
          productoId: 'p1',
          archivo: expect.any(File),
          ancho: 800,
          alto: 600,
          altEs: 'alt es',
          altEn: 'alt en',
        },
      ]);
    });

    it('sin completar los textos alternativos, el botón de subir queda deshabilitado', async () => {
      await renderPagina(new RepositorioProductosAdminFalso());
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [archivoValido()] },
      });
      await vi.waitFor(() =>
        expect(
          (screen.getByRole('button', { name: 'Subir imagen' }) as HTMLButtonElement).disabled,
        ).toBe(true),
      );
    });

    it('con un tipo de archivo no soportado, muestra un error y no ofrece subirlo', async () => {
      await renderPagina(new RepositorioProductosAdminFalso());
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [new File(['x'], 'documento.pdf', { type: 'application/pdf' })] },
      });
      await screen.findByText('Ese tipo de archivo no está soportado. Usa JPEG, PNG o WebP.');

      expect(
        screen.getByText('Ese tipo de archivo no está soportado. Usa JPEG, PNG o WebP.'),
      ).toBeTruthy();
      expect(
        (screen.getByRole('button', { name: 'Subir imagen' }) as HTMLButtonElement).disabled,
      ).toBe(true);
    });

    it('con un error del servidor al subir, muestra el mensaje genérico', async () => {
      const repositorio = new RepositorioProductosAdminFalso(productoDePrueba(), false, true);
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [archivoValido()] },
      });
      await screen.findByLabelText('Texto alternativo (español)');
      fireEvent.input(screen.getByLabelText('Texto alternativo (español)'), {
        target: { value: 'alt es' },
      });
      fireEvent.input(screen.getByLabelText('Texto alternativo (inglés)'), {
        target: { value: 'alt en' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Subir imagen' }));
      expect(await screen.findByText('No se pudo subir la imagen. Intenta de nuevo.')).toBeTruthy();
    });
  });
});
