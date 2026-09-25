import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { esperarSinViolaciones } from '../../../../../../testing/axe';
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

  readonly eliminados: string[] = [];
  fallaAlEliminar: Error | null = null;

  async despublicar(id: string): Promise<ProductoAdmin> {
    this.retirados.push(id);
    this.items = this.items.map((p) => (p.id === id ? { ...p, estado: 'BORRADOR' } : p));
    return this.items.find((p) => p.id === id)!;
  }

  async publicar(id: string): Promise<ProductoAdmin> {
    if (this.fallaAlPublicar) throw this.fallaAlPublicar;
    this.publicados.push(id);
    // Como el servidor: la fila vuelve publicada, así que el menú de esa fila ofrece lo contrario.
    this.items = this.items.map((p) => (p.id === id ? { ...p, estado: 'PUBLICADO' } : p));
    return this.items.find((p) => p.id === id)!;
  }

  async eliminar(id: string): Promise<void> {
    if (this.fallaAlEliminar) throw this.fallaAlEliminar;
    this.eliminados.push(id);
    // Como el servidor: la fila desaparece de la siguiente lectura de la lista.
    this.items = this.items.filter((p) => p.id !== id);
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

/**
 * Abre el menú de tres puntos de una fila. Es el paso previo de casi todo lo de abajo desde que
 * las acciones viven ahí, y el menú se pinta en un portal del CDK a nivel de `body` — por eso las
 * opciones se buscan con `screen` y no dentro del contenedor del componente.
 */
async function abrirMenuDe(nombre: string) {
  fireEvent.click(
    await screen.findByRole('button', {
      name: esAdmin.productos.acciones.menu.replace('{{nombre}}', nombre),
    }),
  );
}

function opcion(nombre: string) {
  return screen.getByRole('menuitem', { name: nombre });
}

describe('ListaProductosAdminPage', () => {
  /**
   * Las cuatro acciones de una fila, y que son exactamente cuatro: si alguien añade una quinta sin
   * decidir dónde va, esta prueba lo para. Editar y capturar son enlaces —se abren en otra
   * pestaña—, las otras dos abren una confirmación.
   */
  it('el menú de una fila ofrece las cuatro acciones, y publicar cuando está en borrador', async () => {
    await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');

    expect(screen.getAllByRole('menuitem')).toHaveLength(4);
    expect(opcion(esAdmin.productos.editarEnlace).tagName).toBe('A');
    expect(opcion(esAdmin.productos.capturar360).tagName).toBe('A');
    expect(opcion(esAdmin.productos.publicar.accion)).toBeTruthy();
    expect(opcion(esAdmin.productos.acciones.eliminar)).toBeTruthy();
  });

  /** Un producto publicado ofrece lo contrario: retirarlo, no publicarlo otra vez. */
  it('un producto publicado ofrece retirar y no publicar', async () => {
    await renderLista([productoDePrueba({ estado: 'PUBLICADO' })]);
    await abrirMenuDe('Morral urbano');

    expect(screen.queryByRole('menuitem', { name: esAdmin.productos.publicar.accion })).toBeNull();
    expect(opcion(esAdmin.productos.publicar.accionRetirar)).toBeTruthy();
  });

  /**
   * Las tres transiciones preguntan antes, y esta prueba comprueba lo que de verdad importa de esa
   * pregunta: que elegir la opción **no** cambia nada.
   */
  it('publicar pregunta antes, y elegir la opción no publica', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');

    fireEvent.click(opcion(esAdmin.productos.publicar.accion));

    expect(
      screen.getByText(esAdmin.productos.publicar.confirmar.replace('{{nombre}}', 'Morral urbano')),
    ).toBeTruthy();
    expect(screen.getByText(esAdmin.productos.publicar.loQueImplica)).toBeTruthy();
    expect(repositorio.publicados).toEqual([]);
  });

  it('al confirmar publica y lo dice', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.publicar.accion));

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
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.publicar.accion));

    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.publicar.cancelar }));

    expect(
      screen.queryByText(
        esAdmin.productos.publicar.confirmar.replace('{{nombre}}', 'Morral urbano'),
      ),
    ).toBeNull();
    expect(repositorio.publicados).toEqual([]);
  });

  /**
   * Retirar arrastra más que publicar —el enlace pasa a 404, sale del sitemap— y hay una cosa que
   * **no** arrastra y conviene que se lea antes de pulsar: los pedidos ya hechos siguen su curso.
   */
  it('al retirar dice qué se lleva por delante y qué no', async () => {
    const { repositorio } = await renderLista([productoDePrueba({ estado: 'PUBLICADO' })]);
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.publicar.accionRetirar));

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
   * El borrado es lo único de esta pantalla que no tiene vuelta, así que la pregunta dice qué más
   * se lleva —variantes, inventario, fotos y visor— antes de que alguien pulse.
   */
  it('eliminar pregunta antes y dice todo lo que se lleva', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');

    fireEvent.click(opcion(esAdmin.productos.acciones.eliminar));

    expect(
      screen.getByText(esAdmin.productos.eliminar.confirmar.replace('{{nombre}}', 'Morral urbano')),
    ).toBeTruthy();
    expect(screen.getByText(esAdmin.productos.eliminar.loQueImplica)).toBeTruthy();
    expect(repositorio.eliminados).toEqual([]);
  });

  it('al confirmar elimina y lo dice', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.acciones.eliminar));

    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.eliminar.accion }));

    expect(
      await screen.findByText(
        esAdmin.productos.eliminar.hecho.replace('{{nombre}}', 'Morral urbano'),
      ),
    ).toBeTruthy();
    expect(repositorio.eliminados).toEqual(['p1']);
  });

  /**
   * Los dos rechazos del servidor llevan instrucciones distintas —"retíralo primero" y "tiene
   * ventas"— y el panel no puede adelantar ninguno de los dos: no ve los pedidos. Con el mensaje
   * genérico, quien borra no sabría cuál de las dos le tocó.
   */
  it('si el producto tiene ventas lo dice con sus palabras, no con el error genérico', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    repositorio.fallaAlEliminar = new ErrorHttp(409, 'tiene ventas', 'PRODUCTO_CON_VENTAS');
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.acciones.eliminar));

    fireEvent.click(screen.getByRole('button', { name: esAdmin.productos.eliminar.accion }));

    expect((await screen.findByRole('alert')).textContent).toContain(
      esAdmin.errores.producto_con_ventas,
    );
  });

  /**
   * El 409 de "sin imagen principal" es accionable: hay que subir la foto. Decir "no se pudo
   * completar la acción" mandaría a mirar el sitio equivocado.
   */
  it('si falta la imagen principal lo dice con sus palabras, no con el error genérico', async () => {
    const { repositorio } = await renderLista([productoDePrueba()]);
    repositorio.fallaAlPublicar = new ErrorHttp(409, 'sin imagen', 'PRODUCTO_SIN_IMAGEN_PRINCIPAL');
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.publicar.accion));

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
    expect(screen.getByText('morral-urbano')).toBeTruthy();
    expect(screen.getByText('TecnoSport')).toBeTruthy();
    expect(screen.getByText('Bolsos')).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'Borrador' })).toBeTruthy();
  });

  /**
   * La miniatura es decorativa —el nombre del producto va al lado, en texto— así que su `alt` va
   * vacío: repetirlo haría que un lector de pantalla leyera dos veces lo mismo por fila.
   */
  it('pinta la miniatura del producto cuando tiene imagen principal', async () => {
    const { container } = await renderLista([
      productoDePrueba({ imagenPrincipalUrl: 'https://cdn.tecnosport.co/p1.avif' }),
    ]);
    await screen.findByText('Morral urbano');

    const miniatura = container.querySelector('img')!;
    expect(miniatura.getAttribute('src')).toBe('https://cdn.tecnosport.co/p1.avif');
    expect(miniatura.getAttribute('alt')).toBe('');
  });

  /**
   * Y sin imagen no deja el hueco vacío: un producto sin imagen principal no se puede publicar, así
   * que la ausencia es un dato que hay que poder leer, no un espacio en blanco.
   */
  it('sin imagen principal lo dice en vez de dejar el hueco', async () => {
    await renderLista([productoDePrueba()]);
    await screen.findByText('Morral urbano');

    expect(screen.getByText(esAdmin.productos.sinImagen)).toBeTruthy();
  });

  it('sin productos lo dice en vez de dejar una tabla vacía', async () => {
    await renderLista([], 0);

    expect(
      await screen.findByText('Todavía no hay productos. Crea el primero con «Nuevo producto».'),
    ).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Siguiente' })).toBeNull();
    // El botón de crear sigue arriba, fuera de la rama vacía: es la salida.
    expect(screen.getByRole('link', { name: 'Nuevo producto' })).toBeTruthy();
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

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { pagina: 2 } }),
    );
  });

  // La pantalla con más superficie interactiva nueva del panel no tenía ni una comprobación de
  // axe. Dos casos, porque la confirmación en línea solo existe en el segundo.
  it('no tiene violaciones de accesibilidad', async () => {
    const { container } = await renderLista([productoDePrueba()]);
    await screen.findByRole('table');

    await esperarSinViolaciones(container);
  });

  it('tampoco con la confirmación de eliminar abierta', async () => {
    const { container } = await renderLista([productoDePrueba()]);
    await abrirMenuDe('Morral urbano');
    fireEvent.click(opcion(esAdmin.productos.acciones.eliminar));
    await screen.findByRole('button', { name: esAdmin.productos.eliminar.accion });

    await esperarSinViolaciones(container);
  });
});
