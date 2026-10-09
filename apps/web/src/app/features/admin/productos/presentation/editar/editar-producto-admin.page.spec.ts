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
  AgregarColorDesdeLaPrincipalAdmin,
  AsignarColorAImagenAdmin,
  CambiarTallaAdmin,
  UsarImagenComoPrincipalAdmin,
  SubirImagenDeGaleriaAdmin,
  SubirImagenPrincipalAdmin,
  VarianteMedida,
} from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../../domain/repositorio-productos-admin.puerto';
import { REPOSITORIO_DIFUSION } from '../../../difusion/domain/repositorio-difusion.puerto';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
import { proveerPaletaDePrueba } from '../../../../../../testing/paleta-colores';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { EditarProductoAdminPage } from './editar-producto-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'TecnoSport' };
const OTRA_MARCA: Marca = { id: 'm2', nombre: 'Under Trail' };
const CATEGORIA: Categoria = {
  id: 'c1',
  nombre: 'Bolsos',
  slug: 'bolsos',
  linea: 'BOLSOS',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};
const OTRA_CATEGORIA: Categoria = {
  id: 'c2',
  nombre: 'Celulares',
  slug: 'celulares',
  linea: 'TECNOLOGIA',
  padreId: null,
  hashtags: [],
  escalaTallas: [],
};

/** Una categoría con escala: la talla nueva se elige de la lista, o se escribe con «Otra talla». */
const CONJUNTOS: Categoria = {
  id: 'c3',
  nombre: 'Conjuntos',
  slug: 'conjuntos',
  linea: 'ROPA',
  padreId: null,
  hashtags: [],
  escalaTallas: ['S', 'M', 'L'],
};

/** Un conjunto en dos colores; «S-M» no está en la escala de su categoría. */
function conjuntoDePrueba(): ProductoAdminDetalle {
  const atributos = (color: string, hex: string, talla: string) => [
    { nombre: 'Color', valor: color, colorHex: hex },
    { nombre: 'Talla', valor: talla, colorHex: null },
  ];
  return {
    ...productoDePrueba(),
    nombre: 'Conjunto deportivo',
    categoria: { id: CONJUNTOS.id, nombre: 'Conjuntos', slug: 'conjuntos', linea: 'ROPA' },
    variantes: [
      { id: 'n-sm', sku: 'C-N-SM', atributos: atributos('Negro', '#111111', 'S-M') },
      { id: 'n-l', sku: 'C-N-L', atributos: atributos('Negro', '#111111', 'L') },
      { id: 'v-sm', sku: 'C-V-SM', atributos: atributos('Vino', '#722F37', 'S-M') },
    ],
  };
}

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
    imagenPrincipalVarianteId: null,
    totalVariantes: 0,
    galeria,
    tallaSirveHasta: null,
    variantes: [],
  };
}

/** Un bodi de talla única en dos colores, con dos fotos en la galería. */
function bodiDePrueba(): ProductoAdminDetalle {
  const atributos = (color: string, hex: string) => [
    { nombre: 'Color', valor: color, colorHex: hex },
    { nombre: 'Talla', valor: 'Única', colorHex: null },
  ];
  return {
    ...productoDePrueba([{ ...imagenDeGaleria(0), varianteId: 'v-vino' }, imagenDeGaleria(1)]),
    nombre: 'Bodi herraje',
    tallaSirveHasta: 'L',
    variantes: [
      { id: 'v-negro', sku: 'PRV-1', atributos: atributos('Negro', '#111111') },
      { id: 'v-vino', sku: 'PRV-2', atributos: atributos('Vino', '#722F37') },
    ],
  };
}

function imagenDeGaleria(orden: number): ImagenDeGaleriaAdmin {
  return {
    id: 'img' + orden,
    url: 'https://storage.googleapis.com/tecnosport-imagenes-de-prueba/galeria-' + orden + '.jpg',
    ancho: 2000,
    alto: 2000,
    orden,
    altEs: 'Vista ' + orden,
    altEn: 'View ' + orden,
    varianteId: null,
  };
}

class RepositorioMarcasFalso implements RepositorioMarcas {
  async listarTodas(): Promise<Marca[]> {
    return [MARCA, OTRA_MARCA];
  }
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [CATEGORIA, OTRA_CATEGORIA, CONJUNTOS];
  }
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasEditar: { id: string; comando: EditarProductoAdmin }[] = [];
  llamadasSubirImagen: SubirImagenPrincipalAdmin[] = [];
  llamadasSubirGaleria: SubirImagenDeGaleriaAdmin[] = [];
  llamadasQuitarDeGaleria: QuitarImagenDeGaleriaAdmin[] = [];
  llamadasReordenarGaleria: ReordenarGaleriaAdmin[] = [];
  llamadasAsignarColor: AsignarColorAImagenAdmin[] = [];
  llamadasPublicar: string[] = [];
  llamadasRetirar: string[] = [];
  llamadasEliminar: string[] = [];
  llamadasUsarComoPrincipal: UsarImagenComoPrincipalAdmin[] = [];
  errorAlTocarLaGaleria = false;
  errorAlEliminar = false;

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
      url: 'https://storage.googleapis.com/tecnosport-imagenes-de-prueba/objeto.webp',
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

  async publicar(id: string): Promise<ProductoAdmin> {
    this.llamadasPublicar.push(id);
    if (this.producto) {
      this.producto = { ...this.producto, estado: 'PUBLICADO' };
    }
    return this.producto!;
  }

  async eliminar(id: string): Promise<void> {
    this.llamadasEliminar.push(id);
    if (this.errorAlEliminar) {
      throw new Error('falló');
    }
    // Como el servidor: después de borrarlo, pedirlo da 404.
    this.producto = null;
  }

  async despublicar(id: string): Promise<ProductoAdmin> {
    this.llamadasRetirar.push(id);
    if (this.producto) {
      this.producto = { ...this.producto, estado: 'BORRADOR' };
    }
    return this.producto!;
  }

  async usarImagenComoPrincipal(comando: UsarImagenComoPrincipalAdmin): Promise<void> {
    this.llamadasUsarComoPrincipal.push(comando);
    if (this.errorAlTocarLaGaleria) {
      throw new Error('falló');
    }
    if (this.producto) {
      this.producto = {
        ...this.producto,
        imagenPrincipalUrl: this.producto.galeria.find((i) => i.id === comando.imagenId)!.url,
        galeria: this.producto.galeria.filter((i) => i.id !== comando.imagenId),
      };
    }
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

  async asignarColorAImagen(comando: AsignarColorAImagenAdmin): Promise<void> {
    this.llamadasAsignarColor.push(comando);
  }

  tallasCambiadas: CambiarTallaAdmin[] = [];
  falloAlCambiarTalla: unknown = null;

  async cambiarTalla(comando: CambiarTallaAdmin): Promise<void> {
    this.tallasCambiadas.push(comando);
    if (this.falloAlCambiarTalla) {
      throw this.falloAlCambiarTalla;
    }
  }

  coloresNuevos: AgregarColorDesdeLaPrincipalAdmin[] = [];
  falloAlAgregarColor = false;

  async agregarColorDesdeLaPrincipal(comando: AgregarColorDesdeLaPrincipalAdmin): Promise<void> {
    this.coloresNuevos.push(comando);
    if (this.falloAlAgregarColor) {
      throw new Error('falló');
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
      proveerPaletaDePrueba(),
      // La ficha monta el panel de difusión al final. No se prueba aquí —tiene su propio spec—
      // pero sin su puerto el componente ni se construye.
      {
        provide: REPOSITORIO_DIFUSION,
        useValue: {
          difundir: async () => ({ tipo: 'OK', publicaciones: [] }),
          historial: async () => [],
          proponerPie: async () => '',
        },
      },
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

  /** Como en la revisión de un borrador: la descripción es la de la ficha y no se deja vacía. */
  it('sin descripción no guarda y dice qué falta', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    await renderPagina(repositorio);
    await screen.findByDisplayValue('Morral urbano');

    fireEvent.input(screen.getByLabelText('Descripción'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(
      await screen.findByText(
        'Faltan datos obligatorios: el nombre, la descripción, la marca y la categoría.',
      ),
    ).toBeTruthy();
    expect(repositorio.llamadasEditar).toEqual([]);
  });

  it('una prenda de talla única edita hasta dónde sirve', async () => {
    const repositorio = new RepositorioProductosAdminFalso(bodiDePrueba());
    const { fixture } = await renderPagina(repositorio);
    // Guardar navega a la lista, que esta prueba no registra.
    vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate').mockResolvedValue(true);
    const sirveHasta = (await screen.findByLabelText('Sirve hasta')) as HTMLInputElement;
    await vi.waitFor(() => expect(sirveHasta.value).toBe('L'));

    fireEvent.input(sirveHasta, { target: { value: 'XL' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await vi.waitFor(() => expect(repositorio.llamadasEditar).toHaveLength(1));
    expect(repositorio.llamadasEditar[0].comando.tallaSirveHasta).toBe('XL');
    expect(screen.getByText(/PRV-1/)).toBeTruthy();
    expect(screen.getByText(/Negro · Única/)).toBeTruthy();
  });

  describe('tallas', () => {
    /**
     * Una fila por talla y no por variante: «S-M» está en dos colores y se corrige una vez. Como no
     * está en la escala, la caja abre con «Otra talla» y la talla escrita; elegir una de la escala
     * cierra el campo.
     */
    it('corrige la talla de un modelo eligiéndola de la escala', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoDePrueba());
      const { container } = await renderPagina(repositorio);

      const cambiar = await screen.findByRole('button', { name: 'Cambiar la talla S-M' });
      expect(screen.getAllByRole('button', { name: /^Cambiar la talla / })).toHaveLength(2);
      fireEvent.click(cambiar);

      const lista = (await screen.findByLabelText(/^Talla nueva/)) as HTMLSelectElement;
      await vi.waitFor(() => expect(lista.value).toBe('__otra__'));
      expect((screen.getByLabelText(/^Escribe la talla/) as HTMLInputElement).value).toBe('S-M');
      expect(cambiar.getAttribute('aria-expanded')).toBe('true');
      await esperarSinViolaciones(container);

      fireEvent.change(lista, { target: { value: 'M' } });
      await vi.waitFor(() => expect(screen.queryByLabelText(/^Escribe la talla/)).toBeNull());
      fireEvent.click(screen.getByRole('button', { name: 'Guardar talla' }));

      await vi.waitFor(() =>
        expect(repositorio.tallasCambiadas).toEqual([
          { productoId: 'p1', modeloId: 'n-sm', talla: 'M' },
        ]),
      );
      expect(await screen.findByText('Talla cambiada a M.')).toBeTruthy();
      expect(screen.queryByLabelText(/^Talla nueva/)).toBeNull();
    });

    it('sin escala la talla se escribe, y vacía no se manda: dice qué falta', async () => {
      const repositorio = new RepositorioProductosAdminFalso(bodiDePrueba());
      await renderPagina(repositorio);

      fireEvent.click(await screen.findByRole('button', { name: 'Cambiar la talla Única' }));
      const escrita = (await screen.findByLabelText(/^Talla nueva/)) as HTMLInputElement;
      expect(escrita.tagName).toBe('INPUT');
      expect(escrita.value).toBe('Única');

      fireEvent.input(escrita, { target: { value: '  ' } });
      fireEvent.click(screen.getByRole('button', { name: 'Guardar talla' }));
      expect(await screen.findByText('Elige o escribe la talla nueva.')).toBeTruthy();
      expect(repositorio.tallasCambiadas).toEqual([]);

      fireEvent.input(escrita, { target: { value: ' S-M ' } });
      fireEvent.click(screen.getByRole('button', { name: 'Guardar talla' }));
      await vi.waitFor(() =>
        expect(repositorio.tallasCambiadas).toEqual([
          { productoId: 'p1', modeloId: 'v-negro', talla: 'S-M' },
        ]),
      );
    });

    it('si ya existe en algún color, lo dice y deja la caja abierta', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoDePrueba());
      repositorio.falloAlCambiarTalla = new ErrorHttp(422, 'repetida', 'TALLA_REPETIDA');
      await renderPagina(repositorio);

      fireEvent.click(await screen.findByRole('button', { name: 'Cambiar la talla S-M' }));
      fireEvent.change(await screen.findByLabelText(/^Talla nueva/), { target: { value: 'L' } });
      fireEvent.click(screen.getByRole('button', { name: 'Guardar talla' }));

      expect(
        await screen.findByText(
          'Ese modelo ya tiene esa talla en alguno de sus colores: quedarían dos variantes iguales.',
        ),
      ).toBeTruthy();
      expect(screen.getByLabelText(/^Talla nueva/)).toBeTruthy();
      expect(screen.queryByText(/Talla cambiada/)).toBeNull();
    });

    it('cancelar cierra la caja sin mandar nada', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoDePrueba());
      await renderPagina(repositorio);

      fireEvent.click(await screen.findByRole('button', { name: 'Cambiar la talla L' }));
      fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));

      await vi.waitFor(() => expect(screen.queryByLabelText(/^Talla nueva/)).toBeNull());
      expect(repositorio.tallasCambiadas).toEqual([]);
    });

    it('un producto que no talla no ofrece cambiar tallas', async () => {
      const morral: ProductoAdminDetalle = {
        ...productoDePrueba(),
        variantes: [
          {
            id: 'v1',
            sku: 'MOR-1',
            atributos: [{ nombre: 'Color', valor: 'Negro', colorHex: '#111111' }],
          },
        ],
      };
      await renderPagina(new RepositorioProductosAdminFalso(morral));
      await screen.findByText(/MOR-1/);

      expect(screen.queryByRole('button', { name: /^Cambiar la talla/ })).toBeNull();
      expect(screen.queryByRole('heading', { name: 'Tallas' })).toBeNull();
    });
  });

  it('un producto que no es de talla única no pregunta hasta dónde sirve', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());
    await screen.findByDisplayValue('Morral urbano');

    expect(screen.queryByLabelText('Sirve hasta')).toBeNull();
  });

  /** El mismo color por foto que la revisión de un borrador, sobre un producto que ya existe. */
  it('cada foto de la galería dice su color y se puede cambiar', async () => {
    const repositorio = new RepositorioProductosAdminFalso(bodiDePrueba());
    await renderPagina(repositorio);

    const primera = (await screen.findByLabelText('Color de la foto 1')) as HTMLSelectElement;
    await vi.waitFor(() => expect(primera.value).toBe('v-vino'));
    expect((screen.getByLabelText('Color de la foto 2') as HTMLSelectElement).value).toBe('');

    fireEvent.change(screen.getByLabelText('Color de la foto 2'), {
      target: { value: 'v-negro' },
    });

    await vi.waitFor(() =>
      expect(repositorio.llamadasAsignarColor).toEqual([
        { productoId: 'p1', imagenId: 'img1', varianteId: 'v-negro' },
      ]),
    );
  });

  /** ADR-0069: sin color, la ficha no enseña la principal en su galería. */
  it('la foto principal dice su color y se puede cambiar', async () => {
    const repositorio = new RepositorioProductosAdminFalso({
      ...bodiDePrueba(),
      imagenPrincipalUrl: 'https://storage.googleapis.com/tecnosport-imagenes-de-prueba/p.jpg',
      imagenPrincipalVarianteId: 'v-negro',
    });
    await renderPagina(repositorio);

    const principal = (await screen.findByLabelText(
      'Color de la foto principal',
    )) as HTMLSelectElement;
    await vi.waitFor(() => expect(principal.value).toBe('v-negro'));

    fireEvent.change(principal, { target: { value: 'v-vino' } });

    await vi.waitFor(() =>
      expect(repositorio.llamadasAsignarColor).toEqual([
        { productoId: 'p1', imagenId: null, varianteId: 'v-vino' },
      ]),
    );
    expect(await screen.findAllByText('Color de la foto guardado.')).not.toHaveLength(0);
  });

  describe('un color nuevo para la foto principal', () => {
    const URL_PRINCIPAL = 'https://storage.googleapis.com/tecnosport-imagenes-de-prueba/p.jpg';

    /** Un conjunto negro en dos tallas: la paleta de prueba deja Vino como color nuevo. */
    function conjuntoNegro(): ProductoAdminDetalle {
      const atributos = (talla: string) => [
        { nombre: 'Color', valor: 'Negro', colorHex: '#111111' },
        { nombre: 'Talla', valor: talla, colorHex: null },
      ];
      return {
        ...productoDePrueba(),
        imagenPrincipalUrl: URL_PRINCIPAL,
        variantes: [
          { id: 'v-s', sku: 'PRV-S', atributos: atributos('S-M') },
          { id: 'v-l', sku: 'PRV-L', atributos: atributos('L-XL') },
        ],
      };
    }

    /** El borrador aprobado sin tono: tallas, ningún color. */
    function conjuntoSinColor(): ProductoAdminDetalle {
      return {
        ...productoDePrueba(),
        imagenPrincipalUrl: URL_PRINCIPAL,
        variantes: [
          {
            id: 'v-s',
            sku: 'PRV-S',
            atributos: [{ nombre: 'Talla', valor: 'S-M', colorHex: null }],
          },
        ],
      };
    }

    async function abrirColorNuevo(): Promise<HTMLSelectElement> {
      const principal = (await screen.findByLabelText(
        'Color de la foto principal',
      )) as HTMLSelectElement;
      await screen.findByRole('option', { name: 'Un color nuevo…' });
      fireEvent.change(principal, { target: { value: '__otro__' } });
      await screen.findByRole('group', { name: 'Color nuevo para la foto principal' });
      return principal;
    }

    async function elegirColor(nombre: string): Promise<void> {
      fireEvent.click(await screen.findByRole('button', { name: /^Color Elige el color/ }));
      fireEvent.click(await screen.findByLabelText(nombre));
    }

    /**
     * El caso que lo pidió: la principal muestra un color que ninguna otra foto tiene. Se empieza a
     * vender en él, talla por talla y con las unidades que se escriben.
     */
    it('con colores, pide las unidades de cada talla y agrega el color', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoNegro());
      await renderPagina(repositorio);
      await abrirColorNuevo();

      await elegirColor('Vino');
      // Negro ya se vende: se elige en el selector de arriba, no aquí.
      expect(screen.queryByRole('checkbox', { name: 'Negro' })).toBeNull();
      // Una unidad por talla de entrada, como al aprobar un borrador; se cambia si hay más.
      expect(
        (screen.getByLabelText('Unidades disponibles en L-XL') as HTMLInputElement).value,
      ).toBe('1');
      fireEvent.input(screen.getByLabelText('Unidades disponibles en S-M'), {
        target: { value: '4' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Agregar el color' }));

      await vi.waitFor(() =>
        expect(repositorio.coloresNuevos).toEqual([
          {
            productoId: 'p1',
            color: 'Vino',
            existencias: [
              { modeloId: 'v-s', existencia: 4 },
              { modeloId: 'v-l', existencia: 1 },
            ],
          },
        ]),
      );
      expect(await screen.findAllByText('Color de la foto guardado.')).not.toHaveLength(0);
      expect(
        screen.queryByRole('group', { name: 'Color nuevo para la foto principal' }),
      ).toBeNull();
    });

    it('sin ningún color, sus variantes lo toman y no se piden unidades', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoSinColor());
      await renderPagina(repositorio);
      await abrirColorNuevo();

      expect(screen.getByText(/todavía no tiene color/)).toBeTruthy();
      expect(screen.queryByLabelText(/Unidades disponibles/)).toBeNull();
      await elegirColor('Negro');
      fireEvent.click(screen.getByRole('button', { name: 'Agregar el color' }));

      await vi.waitFor(() =>
        expect(repositorio.coloresNuevos).toEqual([
          { productoId: 'p1', color: 'Negro', existencias: [] },
        ]),
      );
    });

    it('con la caja de una talla vacía no agrega nada y dice qué falta', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoNegro());
      await renderPagina(repositorio);
      await abrirColorNuevo();

      await elegirColor('Vino');
      fireEvent.input(screen.getByLabelText('Unidades disponibles en S-M'), {
        target: { value: '' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Agregar el color' }));

      expect(
        await screen.findByText('Falta el color o las unidades de alguna talla.'),
      ).toBeTruthy();
      expect(repositorio.coloresNuevos).toEqual([]);
    });

    it('sin color elegido tampoco', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoSinColor());
      await renderPagina(repositorio);
      await abrirColorNuevo();

      fireEvent.click(screen.getByRole('button', { name: 'Agregar el color' }));

      expect(
        await screen.findByText('Falta el color o las unidades de alguna talla.'),
      ).toBeTruthy();
      expect(repositorio.coloresNuevos).toEqual([]);
    });

    it('cancelar cierra la caja y el selector vuelve al color que tenía', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoNegro());
      await renderPagina(repositorio);
      await abrirColorNuevo();

      fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

      await vi.waitFor(() =>
        expect(
          screen.queryByRole('group', { name: 'Color nuevo para la foto principal' }),
        ).toBeNull(),
      );
      const principal = screen.getByLabelText('Color de la foto principal') as HTMLSelectElement;
      expect(principal.value).toBe('');
      expect(repositorio.coloresNuevos).toEqual([]);
    });

    it('si el servidor lo rechaza, lo dice y deja la caja abierta', async () => {
      const repositorio = new RepositorioProductosAdminFalso(conjuntoSinColor());
      repositorio.falloAlAgregarColor = true;
      await renderPagina(repositorio);
      await abrirColorNuevo();

      await elegirColor('Negro');
      fireEvent.click(screen.getByRole('button', { name: 'Agregar el color' }));

      expect(
        await screen.findByText('No se pudo agregar el color. Intenta de nuevo.'),
      ).toBeTruthy();
      expect(
        screen.getByRole('group', { name: 'Color nuevo para la foto principal' }),
      ).toBeTruthy();
    });
  });

  it('sin foto principal no hay color que elegir para ella', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(bodiDePrueba()));
    await screen.findByLabelText('Color de la foto 1');

    expect(screen.queryByLabelText('Color de la foto principal')).toBeNull();
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

    /**
     * El botón sigue vivo y dice qué falta, en vez de deshabilitarse. Un `<button disabled>` sale
     * del orden de tabulación: quien navega con teclado no lo encuentra y nada le explica por qué
     * no pasa nada. Es el criterio que marcas, medidas y existencias ya tenían escrito, y que esta
     * pantalla contradecía en sus tres formularios.
     */
    it('sin completar los textos alternativos, subir dice qué falta y no sube nada', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [archivoValido()] },
      });
      const boton = screen.getByRole('button', { name: 'Subir imagen' }) as HTMLButtonElement;
      expect(boton.disabled).toBe(false);

      fireEvent.click(boton);

      expect(
        await screen.findByText('Falta el archivo o alguno de los dos textos alternativos.'),
      ).toBeTruthy();
      expect(repositorio.llamadasSubirImagen).toEqual([]);
    });

    it('con un tipo de archivo no soportado, muestra un error y no sube nada', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.change(screen.getByLabelText('Selecciona una imagen (JPEG, PNG o WebP)'), {
        target: { files: [new File(['x'], 'documento.pdf', { type: 'application/pdf' })] },
      });
      await screen.findByText('Ese tipo de archivo no está soportado. Usa JPEG, PNG o WebP.');

      expect(
        screen.getByText('Ese tipo de archivo no está soportado. Usa JPEG, PNG o WebP.'),
      ).toBeTruthy();
      // Y pulsarlo no sube nada: el botón está vivo, la guarda está en el manejador.
      fireEvent.click(screen.getByRole('button', { name: 'Subir imagen' }));
      expect(repositorio.llamadasSubirImagen).toEqual([]);
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

  // La pantalla con la galería, las dos subidas y los botones de reordenar tampoco tenía ninguna
  // comprobación de axe, y es la que más ARIA escribe a mano de todo el panel.
  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderPagina(new RepositorioProductosAdminFalso());
    await screen.findByDisplayValue('Morral urbano');

    await esperarSinViolaciones(container);
  });

  /**
   * Un `role="status"` que nace ya lleno dentro de un `@if` no lo anuncia NVDA: medido el 22 de
   * septiembre de 2026, ver `docs/06-testing.md`. El acuse llega despues de pulsar, con la region
   * ya viva, y esta prueba falla si alguien la devuelve adentro de la condicion.
   */
  it('deja viva la region de la galeria llena aunque todavia quepan fotos', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());
    await screen.findByDisplayValue('Morral urbano');

    const regiones = screen.getAllByRole('status');
    expect(regiones.some((region) => region.textContent?.trim() === '')).toBe(true);
  });

  describe('como la revisión de un borrador', () => {
    it('dice en qué estado está el producto', async () => {
      await renderPagina(new RepositorioProductosAdminFalso());
      await screen.findByDisplayValue('Morral urbano');

      expect(screen.getByText('Estado:').textContent).toContain('Borrador');
    });

    it('publica desde la edición, preguntando antes, y lo anuncia', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(screen.getByRole('button', { name: 'Publicar' }));
      expect(repositorio.llamadasPublicar).toEqual([]);
      expect(
        screen.getByText('¿Publicar Morral urbano? Va a quedar visible en la tienda.'),
      ).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: 'Sí, publicar' }));

      expect(await screen.findByText('Morral urbano quedó publicado.')).toBeTruthy();
      expect(repositorio.llamadasPublicar).toEqual(['p1']);
      // Ya publicado, el mismo botón ofrece lo contrario.
      expect(await screen.findByRole('button', { name: 'Retirar' })).toBeTruthy();
    });

    it('dos clics en confirmar publican una sola vez', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(screen.getByRole('button', { name: 'Publicar' }));
      const confirmar = screen.getByRole('button', { name: 'Sí, publicar' });
      fireEvent.click(confirmar);
      fireEvent.click(confirmar);

      await screen.findByText('Morral urbano quedó publicado.');
      expect(repositorio.llamadasPublicar).toEqual(['p1']);
    });

    it('dos clics en usar como principal llaman una sola vez', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      const boton = screen.getByRole('button', { name: 'Usar la imagen 2 como principal' });
      fireEvent.click(boton);
      fireEvent.click(boton);

      await screen.findByText(esAdmin.productos.editar.galeria.principalCambiada);
      expect(repositorio.llamadasUsarComoPrincipal).toHaveLength(1);
    });

    it('cancelar la pregunta no publica nada', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(screen.getByRole('button', { name: 'Publicar' }));
      fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

      expect(screen.queryByRole('button', { name: 'Sí, publicar' })).toBeNull();
      expect(repositorio.llamadasPublicar).toEqual([]);
    });

    it('elimina desde la edición y vuelve a la lista', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      const { fixture } = await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');
      const navegar = vi
        .spyOn(fixture.debugElement.injector.get(Router), 'navigate')
        .mockResolvedValue(true);

      fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
      fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

      await vi.waitFor(() => {
        expect(repositorio.llamadasEliminar).toEqual(['p1']);
        expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos']);
      });
      // El detalle salió de la caché: no se vuelve a pedir y no se pinta un error de carga.
      expect(screen.queryByText(esAdmin.productos.editar.error_carga)).toBeNull();
    });

    it('si eliminar falla, lo dice y no se va', async () => {
      const repositorio = new RepositorioProductosAdminFalso();
      repositorio.errorAlEliminar = true;
      const { fixture } = await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');
      const navegar = vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate');

      fireEvent.click(screen.getByRole('button', { name: 'Eliminar' }));
      fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

      expect(
        await screen.findByText('No pudimos eliminar el producto. Vuelve a intentarlo.'),
      ).toBeTruthy();
      expect(navegar).not.toHaveBeenCalled();
    });

    it('usa una foto de la galería como principal y lo anuncia', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
      );
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(screen.getByRole('button', { name: 'Usar la imagen 2 como principal' }));

      expect(
        await screen.findByText(
          'La foto elegida es ahora la principal. Si había otra, quedó en su puesto de la galería.',
        ),
      ).toBeTruthy();
      expect(repositorio.llamadasUsarComoPrincipal).toEqual([
        { productoId: 'p1', imagenId: 'img1' },
      ]);
    });

    it('si usar la foto como principal falla, lo dice', async () => {
      const repositorio = new RepositorioProductosAdminFalso(
        productoDePrueba([imagenDeGaleria(0), imagenDeGaleria(1)]),
      );
      repositorio.errorAlTocarLaGaleria = true;
      await renderPagina(repositorio);
      await screen.findByDisplayValue('Morral urbano');

      fireEvent.click(screen.getByRole('button', { name: 'Usar la imagen 1 como principal' }));

      expect(await screen.findByText(esAdmin.productos.editar.galeria.errorPrincipal)).toBeTruthy();
    });

    it('pinta la muestra del color junto a cada variante', async () => {
      const { container } = await renderPagina(new RepositorioProductosAdminFalso(bodiDePrueba()));
      await screen.findByDisplayValue('Bodi herraje');

      const fila = screen.getByText('PRV-2').closest('li')!;
      await vi.waitFor(() =>
        expect(fila.querySelector('ts-muestra-color path')?.getAttribute('fill')).toBe('#722F37'),
      );
      expect(container.querySelectorAll('ts-muestra-color').length).toBeGreaterThanOrEqual(2);
    });
  });
});
