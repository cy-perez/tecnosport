import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
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
  CrearProductoAdmin,
  ExistenciaAjustada,
  ExistenciasDelCatalogo,
  InventarioSinMedir,
  MedidasDelCatalogo,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  VarianteMedida,
  ImagenDeGaleriaAdmin,
  ProductoAdminDetalle,
} from '../../domain/producto-admin.model';
import {
  REPOSITORIO_PRODUCTOS_ADMIN,
  RepositorioProductosAdmin,
} from '../../domain/repositorio-productos-admin.puerto';
import { CrearProductoAdminPage } from './crear-producto-admin.page';

const MARCA: Marca = { id: 'm1', nombre: 'TecnoSport' };
const CATEGORIA: Categoria = {
  id: 'c1',
  nombre: 'Bolsos',
  slug: 'bolsos',
  linea: 'BOLSOS',
  padreId: null,
};

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

  async obtener(): Promise<ProductoAdminDetalle> {
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

  subirImagenDeGaleria(): Promise<ImagenDeGaleriaAdmin> {
    throw new Error('no usado por esta prueba');
  }

  quitarImagenDeGaleria(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }

  reordenarGaleria(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }
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
}

describe('CrearProductoAdminPage', () => {
  /**
   * El botón ya no arranca deshabilitado: pulsa, marca los campos y dice qué falta. Un
   * `<button disabled>` sale del orden de tabulación, así que quien navega con teclado no lo
   * encuentra y nada le explica por qué no pasa nada — el criterio que marcas, medidas y
   * existencias ya tenían escrito en un comentario cada una.
   */
  it('con el formulario vacío dice qué falta y no crea nada', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    await renderPagina(repositorio);

    const boton = screen.getByRole('button', { name: 'Crear producto' });
    expect(boton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(boton);

    expect(
      await screen.findByText(
        'Faltan datos obligatorios: el nombre, la descripción, la marca y la categoría.',
      ),
    ).toBeTruthy();
    expect(repositorio.llamadasCrear).toEqual([]);
  });

  /**
   * La categoría se ofrece con su **ruta** desde el 24 de septiembre de 2026 —"Bolsos › Bolsos"
   * aquí, porque el doble monta una hoja de primer nivel llamada igual que su línea—, y no con el
   * nombre a secas. Sin la ruta el desplegable tiene entradas que no se distinguen: "Busos" está
   * bajo Dama y bajo Caballero, y "Dama" en tres líneas.
   */
  it('carga las opciones de marca y categoría, la categoría con su ruta', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    expect(await screen.findByRole('option', { name: 'TecnoSport' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Bolsos › Bolsos' })).toBeTruthy();
  });

  it('al enviar exitosamente, crea el producto y navega a la lista', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio);
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    await llenarYEnviar();

    // La navegación es lo último que ocurre, así que es por lo que hay que
    // esperar: con `llamadasCrear` bastaba para la primera aserción y dejaba la
    // segunda corriendo antes de tiempo.
    await vi.waitFor(() => expect(navegar).toHaveBeenCalled());

    expect(repositorio.llamadasCrear).toEqual([
      { nombre: 'Morral urbano', descripcion: '', marcaId: 'm1', categoriaId: 'c1' },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos']);
  });

  it('con un error del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(true));

    await llenarYEnviar();

    expect(await screen.findByText('No se pudo crear el producto. Intenta de nuevo.')).toBeTruthy();
  });
});
