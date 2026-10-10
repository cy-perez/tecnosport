import {
  REPOSITORIO_PALETA_COLORES,
  RepositorioPaletaColores,
} from '../../../../catalogo/domain/repositorio-paleta-colores.puerto';
import { Categoria, ColorDePaleta } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../../../../catalogo/domain/repositorio-categorias.puerto';
import { vi } from 'vitest';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { fireEvent, render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import en from '../../../../../../assets/i18n/en.json';
import es from '../../../../../../assets/i18n/es.json';
import esAdmin from '../../../../../../assets/i18n/scopes/admin/es.json';
import { Atributo } from '../../../../catalogo/domain/producto.model';
import {
  REPOSITORIO_ATRIBUTOS,
  RepositorioAtributos,
} from '../../../../catalogo/domain/repositorio-atributos.puerto';
import {
  AgregarVarianteAdmin,
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
import { AgregarVarianteAdminPage } from './agregar-variante-admin.page';

const COLOR: Atributo = {
  id: 'a1',
  nombre: 'Color',
  tipo: 'COLOR',
  valoresPermitidos: [],
  unidad: null,
};
const TALLA: Atributo = {
  id: 'a2',
  nombre: 'Talla',
  tipo: 'TEXTO',
  valoresPermitidos: ['S', 'M', 'L'],
  unidad: null,
};

class RepositorioAtributosFalso implements RepositorioAtributos {
  async listarTodas(): Promise<Atributo[]> {
    return [COLOR, TALLA];
  }
}

/** Ropa › Dama tiene la escala; Conjuntos la hereda. Bolsos no talla. */
const ESCALA_ROPA = ['XS', 'XS-S', 'S', 'S-M', 'M', 'M-L', 'L'];

function categoria(id: string, padreId: string | null, escalaTallas: string[]): Categoria {
  return { id, nombre: id, slug: id, linea: 'ropa', padreId, hashtags: [], escalaTallas };
}

class RepositorioCategoriasFalso implements RepositorioCategorias {
  async listarTodas(): Promise<Categoria[]> {
    return [
      categoria('ropa-dama', null, ESCALA_ROPA),
      categoria('ropa-dama-conjuntos', 'ropa-dama', []),
      categoria('bolsos-dama', null, []),
    ];
  }
}

class RepositorioProductosAdminFalso implements RepositorioProductosAdmin {
  llamadasAgregarVariante: AgregarVarianteAdmin[] = [];

  constructor(
    private errorAlAgregar = false,
    private categoriaId = 'ropa-dama-conjuntos',
  ) {}

  async listar(): Promise<ProductosPaginadosAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async crear(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async obtener(): Promise<ProductoAdminDetalle> {
    // Solo la categoría: es lo único que la pantalla lee del producto.
    return { categoria: { id: this.categoriaId } } as unknown as ProductoAdminDetalle;
  }

  async editar(): Promise<ProductoAdmin> {
    throw new Error('No usado en estas pruebas.');
  }

  async agregarVariante(comando: AgregarVarianteAdmin): Promise<void> {
    this.llamadasAgregarVariante.push(comando);
    if (this.errorAlAgregar) {
      throw new Error('falló');
    }
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

  eliminar(): Promise<void> {
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

  asignarColorAImagen(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }

  agregarColorDesdeLaPrincipal(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }

  usarImagenComoPrincipal(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }

  cambiarTalla(): Promise<void> {
    throw new Error('no usado por esta prueba');
  }
}

function activatedRouteConProductoId(productoId: string) {
  const paramMap = convertToParamMap({ productoId });
  return { paramMap: of(paramMap), snapshot: { paramMap } };
}

async function renderPagina(repositorioProductos: RepositorioProductosAdmin, productoId = 'p1') {
  return render(AgregarVarianteAdminPage, {
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
      { provide: REPOSITORIO_ATRIBUTOS, useValue: new RepositorioAtributosFalso() },
      { provide: REPOSITORIO_PALETA_COLORES, useValue: new RepositorioPaletaFalso() },
      { provide: REPOSITORIO_CATEGORIAS, useValue: new RepositorioCategoriasFalso() },
      { provide: ActivatedRoute, useValue: activatedRouteConProductoId(productoId) },
    ],
  });
}

class RepositorioPaletaFalso implements RepositorioPaletaColores {
  async listarTodos(): Promise<ColorDePaleta[]> {
    // Fuera de orden a propósito: la pantalla los ordena alfabéticamente.
    return [
      { nombre: 'Vino', nombreEn: 'Burgundy', hex: '#722F37' },
      { nombre: 'Negro', nombreEn: 'Black', hex: '#111111' },
      { nombre: 'Café', nombreEn: 'Brown', hex: '#6F4E37' },
    ];
  }
}

/** El paquete es obligatorio desde adr/0021, así que el mínimo enviable ya no es SKU y precio. */
function llenarPaquete() {
  fireEvent.input(screen.getByLabelText('Peso (gramos)'), { target: { value: '180' } });
  fireEvent.input(screen.getByLabelText('Largo (cm)'), { target: { value: '30' } });
  fireEvent.input(screen.getByLabelText('Ancho (cm)'), { target: { value: '25' } });
  fireEvent.input(screen.getByLabelText('Alto (cm)'), { target: { value: '4' } });
}

describe('AgregarVarianteAdminPage', () => {
  /**
   * El botón se queda alcanzable aunque falten datos, y es `enviar()` quien no deja pasar.
   *
   * <p>Iba deshabilitado, y un `<button disabled>` sale del orden de tabulación: quien navega con
   * teclado ni siquiera llegaba a enfocarlo para enterarse de por qué no pasaba nada, con nueve
   * campos obligatorios y ninguno marcado. Es el mismo razonamiento que el resumen del checkout ya
   * tenía escrito. Lo levantó la auditoría de accesibilidad.
   */
  it('el botón crear es alcanzable con el formulario vacío, y dice qué falta', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    await renderPagina(repositorio);

    const boton = screen.getByRole('button', { name: 'Crear variante' });
    expect(boton.hasAttribute('disabled')).toBe(false);

    fireEvent.click(boton);

    expect(await screen.findByText(/Faltan datos obligatorios/)).toBeTruthy();
    expect(repositorio.llamadasAgregarVariante).toHaveLength(0);
  });

  /**
   * Lo contrario de lo que esta prueba exigía hasta el 19 de septiembre de 2026. Con `ADR-0046` la
   * variante sin medir es un estado legítimo: se vende, pero solo con recogida en el punto, y el
   * panel tiene que dejar cargarla — si no, el catálogo se queda esperando una báscula.
   */
  it('sin el paquete sí se crea la variante, aunque no se pueda cotizar su envío', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    // El espía va como en la prueba de éxito de más abajo, y no es decoración: el router de esta
    // prueba se monta con `provideRouter([])`, así que una navegación de verdad se va contra un
    // router sin rutas y deja una promesa rechazada que Vitest cuenta como error aunque las
    // aserciones pasen. Lo destapó la CI; en local no se vio porque la salida se había filtrado a
    // las líneas de "Tests" y la de "Errors" quedó fuera.
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));
    const enviado = repositorio.llamadasAgregarVariante[0];
    expect(enviado.pesoGramos ?? null).toBeNull();
    expect(enviado.largoCm ?? null).toBeNull();
    // Y vuelve al producto igual que cuando sí trae medidas: sin paquete no es un camino de error.
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos', 'p1', 'editar']);
  });

  /** Tres medidas y un peso vacío no es "a medio medir": es una carga rota, y el panel lo para. */
  it('con el paquete a medias no se crea la variante', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    await renderPagina(repositorio);

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    fireEvent.input(screen.getByLabelText('Largo (cm)'), { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    expect(await screen.findByText(/Faltan datos obligatorios/)).toBeTruthy();
    expect(repositorio.llamadasAgregarVariante).toHaveLength(0);
  });

  it('con una dimensión en cero tampoco se crea', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    await renderPagina(repositorio);

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    llenarPaquete();
    fireEvent.input(screen.getByLabelText('Largo (cm)'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    expect(await screen.findByText(/Faltan datos obligatorios/)).toBeTruthy();
    expect(repositorio.llamadasAgregarVariante).toHaveLength(0);
  });

  /**
   * El color se elige de la paleta, en orden alfabético, y el HEX sale de ella: el mismo nombre y
   * la misma muestra que pone la revisión de un borrador.
   */
  it('un atributo de color se elige de la paleta, hasta tres y en orden', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate').mockResolvedValue(true);

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    llenarPaquete();
    fireEvent.click(screen.getByRole('button', { name: 'Agregar atributo' }));
    await screen.findByRole('option', { name: 'Color' });
    fireEvent.change(screen.getByLabelText('Atributo'), { target: { value: 'a1' } });

    fireEvent.click(await screen.findByRole('button', { name: /^Color/ }));
    expect(screen.queryByLabelText('Valor')).toBeNull();
    const nombres = (await screen.findAllByRole('checkbox')).map((c) =>
      (c as HTMLInputElement).labels?.[0]?.textContent?.trim(),
    );
    expect(nombres).toEqual(['Café', 'Negro', 'Vino']);

    fireEvent.click(screen.getByLabelText('Negro'));
    fireEvent.click(screen.getByLabelText('Vino'));
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));
    expect(repositorio.llamadasAgregarVariante[0].atributos).toEqual([
      { atributoId: 'a1', valor: 'Negro / Vino', colorHex: '#111111' },
    ]);
  });

  /**
   * La talla se elige de la escala de la categoría —heredada de la rama—, con las agrupadas en su
   * sitio, y llega escrita como la escala: «S-M», igual que la escribe la revisión de un borrador.
   */
  it('una talla se elige de la escala de la categoría, con las agrupadas', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate').mockResolvedValue(true);

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Agregar atributo' }));
    await screen.findByRole('option', { name: 'Talla' });
    fireEvent.change(screen.getByLabelText('Atributo'), { target: { value: 'a2' } });

    const talla = (await screen.findByLabelText('Talla', {
      selector: 'select',
    })) as HTMLSelectElement;
    const opciones = [...talla.options].map((opcion) => opcion.textContent?.trim());
    expect(opciones).toEqual(['Selecciona una opción', ...ESCALA_ROPA, 'Otra talla…']);
    expect(screen.queryByLabelText('Valor')).toBeNull();

    fireEvent.change(talla, { target: { value: 'S-M' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));
    expect(repositorio.llamadasAgregarVariante[0].atributos).toEqual([
      { atributoId: 'a2', valor: 'S-M', colorHex: null },
    ]);
  });

  it('«Otra talla» abre el campo para escribir la que no está en la escala', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    vi.spyOn(fixture.debugElement.injector.get(Router), 'navigate').mockResolvedValue(true);

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Agregar atributo' }));
    await screen.findByRole('option', { name: 'Talla' });
    fireEvent.change(screen.getByLabelText('Atributo'), { target: { value: 'a2' } });
    const talla = await screen.findByLabelText('Talla', { selector: 'select' });
    await screen.findByRole('option', { name: 'Otra talla…' });

    fireEvent.change(talla, { target: { value: '__otra__' } });
    fireEvent.input(await screen.findByLabelText('Valor'), { target: { value: '14' } });
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));
    expect(repositorio.llamadasAgregarVariante[0].atributos).toEqual([
      { atributoId: 'a2', valor: '14', colorHex: null },
    ]);
  });

  it('en una categoría sin escala, la talla se escribe como antes', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(false, 'bolsos-dama'));

    fireEvent.click(screen.getByRole('button', { name: 'Agregar atributo' }));
    await screen.findByRole('option', { name: 'Talla' });
    fireEvent.change(screen.getByLabelText('Atributo'), { target: { value: 'a2' } });

    expect(await screen.findByLabelText('Valor')).toBeTruthy();
    expect(screen.queryByLabelText('Talla', { selector: 'select' })).toBeNull();
  });

  it('al enviar exitosamente, agrega la variante y navega de vuelta a editar producto', async () => {
    const repositorio = new RepositorioProductosAdminFalso();
    const { fixture } = await renderPagina(repositorio, 'p1');
    const router = fixture.debugElement.injector.get(Router);
    const navegar = vi.spyOn(router, 'navigate');

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-CAM-AZ-M' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '89900' } });
    llenarPaquete();
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));
    await vi.waitFor(() => expect(repositorio.llamadasAgregarVariante).toHaveLength(1));

    expect(repositorio.llamadasAgregarVariante).toEqual([
      {
        productoId: 'p1',
        sku: 'TS-CAM-AZ-M',
        precio: 89900,
        tasaIva: 0,
        codigoBarras: null,
        existenciaInicial: 0,
        pesoGramos: 180,
        largoCm: 30,
        anchoCm: 25,
        altoCm: 4,
        atributos: [],
      },
    ]);
    expect(navegar).toHaveBeenCalledWith(['/es', 'admin', 'productos', 'p1', 'editar']);
  });

  it('avisa cuando el peso pasa del tope más bajo de las transportadoras', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    // 18 kg donde iban 1,8: el error de unidad que se paga en cada flete.
    fireEvent.input(screen.getByLabelText('Peso (gramos)'), { target: { value: '18000' } });

    expect(await screen.findByText(/pasa de 8 kg/)).toBeTruthy();
  });

  it('con un peso normal no avisa nada', async () => {
    await renderPagina(new RepositorioProductosAdminFalso());

    fireEvent.input(screen.getByLabelText('Peso (gramos)'), { target: { value: '1800' } });

    expect(screen.queryByText(/pasa de 8 kg/)).toBeNull();
  });

  it('con un error del servidor, muestra el mensaje genérico', async () => {
    await renderPagina(new RepositorioProductosAdminFalso(true));

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'TS-1' } });
    fireEvent.input(screen.getByLabelText('Precio'), { target: { value: '1000' } });
    llenarPaquete();
    fireEvent.click(screen.getByRole('button', { name: 'Crear variante' }));

    // `findByText`, no `await esperar(50)`: la espera fija pasaba en aislamiento
    // y fallaba en la suite completa, porque 50 ms no alcanzan cuando la
    // máquina está cargada. `findByText` sondea hasta que el mensaje aparece.
    expect(
      await screen.findByText('No se pudo agregar la variante. Intenta de nuevo.'),
    ).toBeTruthy();
  });
});
