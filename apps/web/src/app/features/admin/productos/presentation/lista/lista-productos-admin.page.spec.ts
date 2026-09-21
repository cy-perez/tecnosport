import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import {
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

  /** Lo publicado, y el fallo que se quiera provocar. */
  readonly publicados: string[] = [];
  fallaAlPublicar: Error | null = null;

  readonly retirados: string[] = [];

  async despublicar(id: string): Promise<ProductoAdmin> {
    this.retirados.push(id);
    this.items = this.items.map((p) => (p.id === id ? { ...p, estado: 'BORRADOR' } : p));
    return this.items.find((p) => p.id === id)!;
  }

  async publicar(id: string): Promise<ProductoAdmin> {
    if (this.fallaAlPublicar) throw this.fallaAlPublicar;
    this.publicados.push(id);
    // Como el servidor: la fila vuelve publicada, así que el botón desaparece de esa fila.
    this.items = this.items.map((p) => (p.id === id ? { ...p, estado: 'PUBLICADO' } : p));
    return this.items.find((p) => p.id === id)!;
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
  /**
   * Las dos transiciones preguntan antes, y esta prueba comprueba lo que de verdad importa de esa
   * pregunta: que un solo clic **no** cambia nada.
   */
  it('publicar pregunta antes, y el primer clic no publica', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);

    fireEvent.click(
      await screen.findByRole('button', {
        name: esAdmin.productos.publicar.publicarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    );

    expect(
      screen.getByText(esAdmin.productos.publicar.confirmar.replace('{{nombre}}', 'Morral urbano')),
    ).toBeTruthy();
    expect(screen.getByText(esAdmin.productos.publicar.loQueImplica)).toBeTruthy();
    expect(repositorio.publicados).toEqual([]);
  });

  it('al confirmar publica y lo dice', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);

    fireEvent.click(
      await screen.findByRole('button', {
        name: esAdmin.productos.publicar.publicarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    );
    fireEvent.click(
      screen.getByRole('button', { name: esAdmin.productos.publicar.confirmarAccion }),
    );

    expect(
      await screen.findByText(
        esAdmin.productos.publicar.hecho.replace('{{nombre}}', 'Morral urbano'),
      ),
    ).toBeTruthy();
    expect(repositorio.publicados).toEqual(['p1']);
  });

  it('cancelar cierra la pregunta sin publicar', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);

    fireEvent.click(
      await screen.findByRole('button', {
        name: esAdmin.productos.publicar.publicarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.publicar.cancelar }));

    expect(
      screen.queryByText(
        esAdmin.productos.publicar.confirmar.replace('{{nombre}}', 'Morral urbano'),
      ),
    ).toBeNull();
    expect(repositorio.publicados).toEqual([]);
  });

  /** Un producto publicado ofrece lo contrario: retirarlo, no publicarlo otra vez. */
  it('un producto publicado ofrece retirar y no publicar', async () => {
    await renderLista([productoDePrueba({ estado: 'PUBLICADO' })]);

    await screen.findByText('Morral urbano');
    expect(
      screen.queryByRole('button', {
        name: esAdmin.productos.publicar.publicarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    ).toBeNull();
    expect(
      screen.getByRole('button', {
        name: esAdmin.productos.publicar.retirarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    ).toBeTruthy();
  });

  /**
   * Retirar arrastra más que publicar —el enlace pasa a 404, sale del sitemap— y hay una cosa que
   * **no** arrastra y conviene que se lea antes de pulsar: los pedidos ya hechos siguen su curso.
   */
  it('al retirar dice qué se lleva por delante y qué no', async () => {
    const { repositorio } = await renderLista([productoDePrueba({ estado: 'PUBLICADO' })]);

    fireEvent.click(
      await screen.findByRole('button', {
        name: esAdmin.productos.publicar.retirarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    );

    expect(screen.getByText(esAdmin.productos.publicar.loQueImplicaRetirar)).toBeTruthy();
    expect(repositorio.retirados).toEqual([]);

    fireEvent.click(
      screen.getByRole('button', { name: esAdmin.productos.publicar.confirmarRetirarAccion }),
    );

    expect(
      await screen.findByText(
        esAdmin.productos.publicar.retirado.replace('{{nombre}}', 'Morral urbano'),
      ),
    ).toBeTruthy();
    expect(repositorio.retirados).toEqual(['p1']);
  });

  /**
   * El 409 de "sin imagen principal" es accionable: hay que subir la foto. Decir "no se pudo
   * completar la acción" mandaría a mirar el sitio equivocado.
   */
  it('si falta la imagen principal lo dice con sus palabras, no con el error genérico', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    repositorio.fallaAlPublicar = new ErrorHttp(409, 'sin imagen', 'PRODUCTO_SIN_IMAGEN_PRINCIPAL');

    fireEvent.click(
      await screen.findByRole('button', {
        name: esAdmin.productos.publicar.publicarProducto.replace('{{nombre}}', 'Morral urbano'),
      }),
    );
    fireEvent.click(
      screen.getByRole('button', { name: esAdmin.productos.publicar.confirmarAccion }),
    );

    expect((await screen.findByRole('alert')).textContent).toContain(
      esAdmin.errores.producto_sin_imagen_principal,
    );
  });

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

    expect(screen.getByRole('link', { name: 'Nuevo producto' }).className).toContain('anillo-foco');
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
