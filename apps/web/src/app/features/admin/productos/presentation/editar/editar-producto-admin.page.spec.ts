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
  ExistenciaAjustada,
  ExistenciasDelCatalogo,
  ImagenAdmin,
  ImagenDeGaleriaAdmin,
  InventarioSinMedir,
  MedidasDelCatalogo,
  ProductoAdmin,
  ProductoAdminDetalle,
  ProductosPaginadosAdmin,
  QuitarImagenDeGaleriaAdmin,
  ReordenarGaleriaAdmin,
  SubirImagenDeGaleriaAdmin,
  SubirImagenPrincipalAdmin,
  VarianteMedida,
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
  linea: 'TECNOLOGIA',
};

function productoDePrueba(galeria: readonly ImagenDeGaleriaAdmin[] = []): ProductoAdminDetalle {
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
    galeria,
  };
}

function imagenDeGaleria(orden: number): ImagenDeGaleriaAdmin {
  return {
    id: 'img' + orden,
    url: 'https://storage.googleapis.com/tecnosport-dev-imagenes/galeria-' + orden + '.jpg',
    urlWebp: 'https://storage.googleapis.com/tecnosport-dev-imagenes/galeria-' + orden + '.jpg',
    ancho: 2000,
    alto: 2000,
    orden,
    altEs: 'Vista ' + orden,
    altEn: 'View ' + orden,
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
  llamadasSubirGaleria: SubirImagenDeGaleriaAdmin[] = [];
  llamadasQuitarDeGaleria: QuitarImagenDeGaleriaAdmin[] = [];
  llamadasReordenarGaleria: ReordenarGaleriaAdmin[] = [];
  errorAlTocarLaGaleria = false;

  constructor(
    private producto: ProductoAdminDetalle | null = productoDePrueba(),
    private errorAlEditar = false,
    private errorAlSubirImagen = false,
  ) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async crear(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async obtener(): Promise<ProductoAdminDetalle> {
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

  listarSinMedir(): Promise<InventarioSinMedir> {
    throw new Error('no usado por esta prueba');
  }

  listarMedidas(): Promise<MedidasDelCatalogo> {
    throw new Error('no usado por esta prueba');
  }

  publicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por esta prueba');
  }

  despublicar(): Promise<ProductoAdmin> {
    throw new Error('no usado por esta prueba');
  }

  medirVariante(): Promise<VarianteMedida> {
    throw new Error('no usado por esta prueba');
  }

  listarExistencias(): Promise<ExistenciasDelCatalogo> {
    throw new Error('no usado por esta prueba');
  }

  ajustarExistencia(): Promise<ExistenciaAjustada> {
    throw new Error('no usado por esta prueba');
  }

  async subirImagenDeGaleria(comando: SubirImagenDeGaleriaAdmin): Promise<ImagenDeGaleriaAdmin> {
    this.llamadasSubirGaleria.push(comando);
    if (this.errorAlTocarLaGaleria) {
      throw new Error('falló');
    }
    const agregada = imagenDeGaleria(this.producto?.galeria.length ?? 0);
    if (this.producto) {
      this.producto = { ...this.producto, galeria: [...this.producto.galeria, agregada] };
    }
    return agregada;
  }

  async reordenarGaleria(comando: ReordenarGaleriaAdmin): Promise<void> {
    this.llamadasReordenarGaleria.push(comando);
    if (this.errorAlTocarLaGaleria) {
      throw new Error('falló');
    }
    if (this.producto) {
      const porId = new Map(this.producto.galeria.map((imagen) => [imagen.id, imagen]));
      this.producto = {
        ...this.producto,
        galeria: comando.imagenIds.map((id, orden) => ({ ...porId.get(id)!, orden })),
      };
    }
  }

  async quitarImagenDeGaleria(comando: QuitarImagenDeGaleriaAdmin): Promise<void> {
    this.llamadasQuitarDeGaleria.push(comando);
    if (this.errorAlTocarLaGaleria) {
      throw new Error('falló');
    }
    if (this.producto) {
      this.producto = {
        ...this.producto,
        galeria: this.producto.galeria.filter((imagen) => imagen.id !== comando.imagenId),
      };
    }
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
    expect(
      await screen.findByText('No se pudo editar el producto. Intenta de nuevo.'),
    ).toBeTruthy();
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

  describe('galería', () => {
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
      return new File(['contenido'], 'galeria.jpg', { type: 'image/jpeg' });
    }

    async function completarYAgregar() {
      fireEvent.change(screen.getByLabelText('Agrega una imagen (JPEG, PNG o WebP)'), {
        target: { files: [archivoValido()] },
      });
      await screen.findByLabelText('Texto alternativo de la galería (español)');
      fireEvent.input(screen.getByLabelText('Texto alternativo de la galería (español)'), {
        target: { value: 'alt es' },
      });
      fireEvent.input(screen.getByLabelText('Texto alternativo de la galería (inglés)'), {
        target: { value: 'alt en' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Agregar a la galería' }));
    }

    it('sin imágenes, lo dice en vez de enseñar una lista vacía', async () => {
      await renderPagina(new RepositorioProductosAdminFalso());
      await screen.findByDisplayValue('Morral urbano');

      expect(
        screen.getByText(
          'Este producto no tiene imágenes de galería. La ficha enseña solo la principal.',
        ),
      ).toBeTruthy();
    });

    it('enseña las que ya hay, nombradas por su posición y no por su orden guardado', async () => {
      await renderPagina(
        new RepositorioProductosAdminFalso(
          productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
        ),
      );
      await screen.findByDisplayValue('Morral urbano');

      expect(await screen.findByAltText('Imagen 1 de la galería')).toBeTruthy();
      expect(screen.getByAltText('Imagen 2 de la galería')).toBeTruthy();
    });

    // El defecto que esto cierra: `orden` deja huecos a propósito, así que tras quitar la del medio
    // la pantalla ofrecía "imagen 1" e "imagen 3" sobre dos fotos, y el nombre accesible del único
    // control que las distingue nombraba una imagen que no existe.
    it('con un hueco en el orden guardado, las numera por su posición visible', async () => {
      await renderPagina(
        new RepositorioProductosAdminFalso(
          productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(5)]),
        ),
      );
      await screen.findByDisplayValue('Morral urbano');

      expect(await screen.findByAltText('Imagen 2 de la galería')).toBeTruthy();
      expect(screen.queryByAltText('Imagen 6 de la galería')).toBeNull();
      expect(screen.getByRole('button', { name: 'Quitar la imagen 2 de la galería' })).toBeTruthy();
    });

    it('pinta el texto alternativo guardado, que es un dato que se está revisando', async () => {
      await renderPagina(
        new RepositorioProductosAdminFalso(productoDePrueba([imagenDeGaleria(0)])),
      );
      await screen.findByDisplayValue('Morral urbano');

      expect(await screen.findByText('Vista 0')).toBeTruthy();
    });

    it('agrega la imagen con las dimensiones leídas del archivo', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      await completarYAgregar();
      await vi.waitFor(() => expect(repositorio.llamadasSubirGaleria).toHaveLength(1));

      expect(repositorio.llamadasSubirGaleria).toEqual([
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

    // Lo mismo que pide el botón de publicar: un solo clic no puede borrar un archivo.
    it('un solo clic en Quitar no quita nada: primero pregunta', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Quitar la imagen 1 de la galería' }),
      );

      expect(
        await screen.findByText(
          'Sale de la ficha y su archivo se borra. No tiene vuelta: para recuperarla hay que volver a subirla.',
        ),
      ).toBeTruthy();
      expect(repositorio.llamadasQuitarDeGaleria).toHaveLength(0);
    });

    it('al confirmar, la quita', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Quitar la imagen 1 de la galería' }),
      );
      fireEvent.click(await screen.findByRole('button', { name: 'Sí, quitar' }));

      await vi.waitFor(() =>
        expect(repositorio.llamadasQuitarDeGaleria).toEqual([
          { productoId: 'p1', imagenId: 'img0' },
        ]),
      );
    });

    it('al cancelar la pregunta, no quita nada', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Quitar la imagen 1 de la galería' }),
      );
      fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

      await vi.waitFor(() =>
        expect(
          screen.queryByText(
            'Sale de la ficha y su archivo se borra. No tiene vuelta: para recuperarla hay que volver a subirla.',
          ),
        ).toBeNull(),
      );
      expect(repositorio.llamadasQuitarDeGaleria).toHaveLength(0);
    });

    it('subir una imagen manda la galería entera con el orden nuevo', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1), imagenDeGaleria(2)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(await screen.findByRole('button', { name: 'Subir la imagen 3 un puesto' }));

      // La lista completa, no "mueve la tercera": es lo que impide que dos pestañas se pisen.
      await vi.waitFor(() =>
        expect(repositorio.llamadasReordenarGaleria).toEqual([
          { productoId: 'p1', imagenIds: ['img0', 'img2', 'img1'] },
        ]),
      );
    });

    it('en los extremos no ofrece el movimiento que no lleva a ningún sitio', async () => {
      await renderPagina(
        new RepositorioProductosAdminFalso(
          productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
        ),
      );
      await screen.findByDisplayValue('Morral urbano');

      // Y no está deshabilitado: un control deshabilitado no es enfocable, así que para quien
      // navega con teclado sería un botón que existe y no responde.
      expect(screen.queryByRole('button', { name: 'Subir la imagen 1 un puesto' })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Bajar la imagen 2 un puesto' })).toBeNull();
      expect(screen.getByRole('button', { name: 'Bajar la imagen 1 un puesto' })).toBeTruthy();
      expect(screen.getByRole('button', { name: 'Subir la imagen 2 un puesto' })).toBeTruthy();
    });

    it('con una sola imagen no ofrece mover nada', async () => {
      await renderPagina(
        new RepositorioProductosAdminFalso(productoDePrueba([imagenDeGaleria(0)])),
      );
      await screen.findByDisplayValue('Morral urbano');

      expect(screen.queryByRole('button', { name: 'Subir la imagen 1 un puesto' })).toBeNull();
      expect(screen.queryByRole('button', { name: 'Bajar la imagen 1 un puesto' })).toBeNull();
    });

    it('si falla, lo dice junto a la galería y no dentro del formulario de agregar', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
      );
      repositorio.errorAlTocarLaGaleria = true;
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(await screen.findByRole('button', { name: 'Bajar la imagen 1 un puesto' }));

      expect(
        await screen.findByText('No se pudo cambiar el orden. Intenta de nuevo.'),
      ).toBeTruthy();
    });

    it('con la galería llena, no ofrece el formulario de agregar', async () => {
      const llena = Array.from({ length: 8 }, (_, i) => imagenDeGaleria(i));
      await renderPagina(new RepositorioProductosAdminFalso(productoDePrueba(llena)));
      await screen.findByDisplayValue('Morral urbano');

      await vi.waitFor(() =>
        expect(screen.queryByLabelText('Agrega una imagen (JPEG, PNG o WebP)')).toBeNull(),
      );
      expect(screen.queryByRole('button', { name: 'Agregar a la galería' })).toBeNull();
    });

    it('si quitar falla, el error sale en la fila y no en el formulario de agregar', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0)]),
      );
      repositorio.errorAlTocarLaGaleria = true;
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(
        await screen.findByRole('button', { name: 'Quitar la imagen 1 de la galería' }),
      );
      fireEvent.click(await screen.findByRole('button', { name: 'Sí, quitar' }));

      const mensaje = await screen.findByText('No se pudo quitar la imagen. Intenta de nuevo.');
      // Dentro del bloque de confirmación, que es la fila que falló.
      expect(mensaje.closest('[role="group"]')).toBeTruthy();
    });

    it('al agregar lo anuncia, en vez de crecer en silencio', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      await completarYAgregar();

      expect(await screen.findByText('La imagen se agregó a la galería.')).toBeTruthy();
    });

    it('con la galería llena lo dice, en vez de apagar el formulario en silencio', async () => {
      const llena = Array.from({ length: 8 }, (_, i) => imagenDeGaleria(i));
      await renderPagina(new RepositorioProductosAdminFalso(productoDePrueba(llena)));
      await screen.findByDisplayValue('Morral urbano');

      expect(
        await screen.findByText(
          'La galería ya está llena. Quita una imagen antes de agregar otra.',
        ),
      ).toBeTruthy();
    });

    it('con un error del servidor al agregar, lo dice', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      repositorio.errorAlTocarLaGaleria = true;
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      await completarYAgregar();

      expect(
        await screen.findByText('No se pudo agregar la imagen. Intenta de nuevo.'),
      ).toBeTruthy();
    });
  });
});
