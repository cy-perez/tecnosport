import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { AccionDeMenu, TsMenuAcciones } from '../../../../../shared/ui/menu/ts-menu-acciones';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarEliminarProducto } from '../../application/eliminar-producto.mutacion';
import { usarListarProductosAdmin } from '../../application/listar-productos-admin.consulta';
import {
  usarDespublicarProducto,
  usarPublicarProducto,
} from '../../application/publicar-producto.mutacion';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import {
  EstadoProducto,
  FiltroProductosAdmin,
  ProductoAdmin,
} from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * La insignia de estado. Contorno y no relleno: el relleno de color pide un `sobre-` propio por
 * cada estado y el sistema solo tiene los de primario, acento, marca y deshabilitado —inventar
 * dos sería inventar color, que es lo que la regla dura #2 prohíbe—. Con el contorno, el par que
 * hay que verificar es `--color-exito` sobre `--color-superficie`, que ya existe en esta misma
 * pantalla (el aviso de "quedó publicado").
 */
const CLASES_INSIGNIA =
  'inline-flex items-center rounded-completo border px-12 py-4 text-xs font-medio';

const CLASES_INSIGNIA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'border-ts-borde text-ts-texto-suave',
  PUBLICADO: 'border-ts-exito text-ts-exito',
};

/** Lo que el menú de una fila ofrece, y lo que cada opción arrastra al confirmarse. */
type AccionDeFila = 'publicar' | 'retirar' | 'eliminar';

/**
 * Los tres textos de cada confirmación: la pregunta, lo que implica y el botón que la acepta.
 *
 * <p>Como tabla y no como tres `?:` en la plantilla: con dos acciones ya era un condicional
 * anidado por cada línea de la caja, y con tres son nueve sitios donde emparejar mal la pregunta
 * de una con el botón de otra. Las de publicar y retirar conservan sus claves originales —el texto
 * no cambió— y por eso viven bajo `publicar.` aunque una de ellas retire.
 */
const TEXTOS_DE_CONFIRMACION: Record<
  AccionDeFila,
  { pregunta: string; implica: string; accion: string; hecho: string }
> = {
  publicar: {
    pregunta: 'admin.productos.publicar.confirmar',
    implica: 'admin.productos.publicar.loQueImplica',
    accion: 'admin.productos.publicar.confirmarAccion',
    hecho: 'admin.productos.publicar.hecho',
  },
  retirar: {
    pregunta: 'admin.productos.publicar.confirmarRetirar',
    implica: 'admin.productos.publicar.loQueImplicaRetirar',
    accion: 'admin.productos.publicar.confirmarRetirarAccion',
    hecho: 'admin.productos.publicar.retirado',
  },
  eliminar: {
    pregunta: 'admin.productos.eliminar.confirmar',
    implica: 'admin.productos.eliminar.loQueImplica',
    accion: 'admin.productos.eliminar.accion',
    hecho: 'admin.productos.eliminar.hecho',
  },
};

/** La clave genérica del error de cada acción, cuando el código del backend no tiene traducción. */
const CLAVE_ERROR: Record<AccionDeFila, string> = {
  publicar: 'admin.productos.publicar.error',
  retirar: 'admin.productos.publicar.errorRetirar',
  eliminar: 'admin.productos.eliminar.error',
};

/**
 * La lista del catálogo desde el panel: el único sitio donde un producto pasa de BORRADOR a
 * PUBLICADO, y el único donde se borra.
 *
 * <p><b>Las tres acciones preguntan antes</b>, y cada una por su motivo. Publicar deja el producto
 * a la vista de quien compra. Retirar lo saca de la vitrina: desaparece de la rejilla, su enlace
 * pasa a responder 404 y sale del sitemap en la siguiente generación — los pedidos ya creados, en
 * cambio, siguen su curso intactos. Eliminar no tiene vuelta, y por eso su botón de confirmación
 * es el único en variante `peligro`.
 *
 * <p>La confirmación va dentro de la fila y no en un diálogo: es una pregunta de una línea, y
 * abrir un modal con trampa de foco para eso es más ceremonia que la decisión. Y es **una sola**
 * fila de confirmación para las tres acciones, porque no pueden estar abiertas a la vez.
 *
 * <p><b>Las acciones viven en un menú de tres puntos</b> desde el 25 de septiembre de 2026, no
 * sueltas en la fila. Eran dos enlaces y un botón, y con "Eliminar" habrían sido cuatro controles
 * por fila compitiendo con el dato: en una tabla de veinte productos, ochenta paradas de tabulación
 * antes del paginador. El menú deja una por fila. Lo que el panel **no** hace es esconder ahí la
 * acción principal: crear un producto sigue siendo un botón a la vista, arriba de la tabla.
 *
 * <p>El servidor decide si un borrado se puede hacer —publicado no, con ventas tampoco— y el panel
 * no se adelanta: no ve los pedidos, y un producto se puede vender entre que se pinta la lista y se
 * pulsa el botón. El 409 llega con su código y `mensajeDeError` lo convierte en la instrucción que
 * toca ("retíralo de la vitrina primero", "tiene ventas").
 */
@Component({
  selector: 'app-lista-productos-admin',
  imports: [TranslocoPipe, TsBoton, TsEsqueleto, TsMenuAcciones, TsMigas, TsPaginador],
  templateUrl: './lista-productos-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaProductosAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.productos.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  protected readonly filtro = computed<FiltroProductosAdmin>(() =>
    filtroDesdeQueryParams(this.queryParams()),
  );

  protected readonly consulta = usarListarProductosAdmin(this.filtro);

  protected readonly productos = computed<readonly ProductoAdmin[]>(
    () => this.consulta.data()?.items ?? [],
  );

  protected readonly totalProductos = computed(() => this.consulta.data()?.totalProductos ?? 0);

  /**
   * Vacía de verdad, no una página fuera de rango: `Page.getTotalPages()` da 0
   * solo cuando no hay ningún producto, y 1 cuando sí los hay pero la página
   * pedida se pasó del final. Sin la distinción, `?pagina=5` con cuatro
   * productos afirmaba que no había ninguno — visto en el navegador.
   */
  protected readonly sinProductos = computed(
    () => this.productos().length === 0 && (this.consulta.data()?.totalPaginas ?? 0) === 0,
  );

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  protected clasesEstado(estado: EstadoProducto): string {
    return CLASES_INSIGNIA + ' ' + CLASES_INSIGNIA_ESTADO[estado];
  }

  /**
   * Las cuatro opciones de una fila, en el orden en que se usan.
   *
   * <p>Editar y capturar llevan `enlace` y por eso el menú las pinta como `<a>`: son destinos, y
   * abrirlos en otra pestaña es algo que quien carga catálogo hace todo el día. Las otras dos
   * emiten y abren la confirmación.
   *
   * <p>La segunda cambia de identidad según el estado: publicar un borrador, retirar un publicado.
   * Son dos acciones distintas y no una alternancia, porque lo que arrastran no es simétrico.
   */
  protected accionesDe(producto: ProductoAdmin): readonly AccionDeMenu[] {
    const publicado = producto.estado === 'PUBLICADO';
    return [
      {
        id: 'editar',
        etiqueta: this.traducir()('admin.productos.editarEnlace'),
        enlace: [producto.id, 'editar'],
      },
      {
        id: publicado ? 'retirar' : 'publicar',
        etiqueta: this.traducir()(
          publicado ? 'admin.productos.publicar.accionRetirar' : 'admin.productos.publicar.accion',
        ),
      },
      {
        id: 'captura',
        etiqueta: this.traducir()('admin.productos.capturar360'),
        enlace: [producto.id, 'captura-360'],
      },
      {
        id: 'eliminar',
        etiqueta: this.traducir()('admin.productos.acciones.eliminar'),
        destructiva: true,
      },
    ];
  }

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoLista = viewChild<ElementRef<HTMLElement>>('avisoLista');
  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);

  /**
   * El disparador del menú de una fila. Se busca por su marca en el DOM y no con `viewChildren`,
   * porque el botón vive dentro de `ts-menu-acciones` y lo que hay que enfocar es el `<button>`
   * real, no el host del componente.
   */
  private botonDeAcciones(productoId: string): HTMLElement | null {
    return (
      this.raiz.nativeElement.querySelector<HTMLElement>(
        `[data-acciones="${productoId}"] button`,
      ) ?? null
    );
  }

  /** La fila con una confirmación abierta, y cuál. `null` = ninguna. */
  protected readonly confirmando = signal<{ id: string; accion: AccionDeFila } | null>(null);
  protected readonly error = signal<string | null>(null);
  /** Lo último que se hizo, para decirlo cuando su fila ya cambió de estado o desapareció. */
  protected readonly aviso = signal<{ clave: string; nombre: string } | null>(null);

  protected readonly clavePregunta = computed(
    () => TEXTOS_DE_CONFIRMACION[this.confirmando()?.accion ?? 'publicar'].pregunta,
  );
  protected readonly claveLoQueImplica = computed(
    () => TEXTOS_DE_CONFIRMACION[this.confirmando()?.accion ?? 'publicar'].implica,
  );
  protected readonly claveAccion = computed(
    () => TEXTOS_DE_CONFIRMACION[this.confirmando()?.accion ?? 'publicar'].accion,
  );

  private readonly mutacionPublicar = usarPublicarProducto();
  private readonly mutacionRetirar = usarDespublicarProducto();
  private readonly mutacionEliminar = usarEliminarProducto();
  protected readonly ocupado = computed(
    () =>
      this.mutacionPublicar.isPending() ||
      this.mutacionRetirar.isPending() ||
      this.mutacionEliminar.isPending(),
  );

  /** La caja de confirmación, que lleva `tabindex="-1"` para poder recibir el foco. */
  private cajaDe(productoId: string): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(`[data-caja="${productoId}"]`);
  }

  /**
   * Al abrir, el foco entra en la caja. **Hace falta, o el `(keydown.escape)` de la caja no recibe
   * nunca la tecla**: el foco se queda donde estaba —el botón del menú, en la fila anterior— así
   * que Escape no cancelaría nada.
   *
   * <p>Las opciones con `enlace` no llegan aquí: el menú las pinta como `<a>` y navegan solas.
   */
  protected preguntar(producto: ProductoAdmin, accion: string): void {
    if (accion !== 'publicar' && accion !== 'retirar' && accion !== 'eliminar') {
      return;
    }
    this.error.set(null);
    this.aviso.set(null);
    this.confirmando.set({ id: producto.id, accion });
    this.enfocarDespuesDePintar(() => this.cajaDe(producto.id));
  }

  /**
   * Cancelar destruye la fila de confirmación con el botón "Cancelar" dentro, así que el foco hay
   * que devolverlo a mano: al botón del menú que abrió la pregunta, que es de donde venía.
   */
  protected cancelar(productoId: string): void {
    this.confirmando.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeAcciones(productoId));
  }

  /**
   * Ejecuta lo que la caja abierta estaba preguntando.
   *
   * <p>Guarda de reentrada en vez de `[cargando]` en el botón: deshabilitar el botón que se acaba
   * de pulsar le quita el foco y el navegador lo manda a `<body>`. El doble envío lo evita esto, y
   * el botón dice que está ocupado con `aria-busy` sin salirse del camino.
   */
  protected confirmar(producto: ProductoAdmin): void {
    const pendiente = this.confirmando();
    if (pendiente === null || pendiente.id !== producto.id || this.ocupado()) {
      return;
    }
    this.error.set(null);

    const acciones: Record<AccionDeFila, () => void> = {
      publicar: () =>
        this.mutacionPublicar.mutate(producto.id, this.manejadores(producto, 'publicar')),
      retirar: () =>
        this.mutacionRetirar.mutate(producto.id, this.manejadores(producto, 'retirar')),
      eliminar: () =>
        this.mutacionEliminar.mutate(producto.id, this.manejadores(producto, 'eliminar')),
    };
    acciones[pendiente.accion]();
  }

  /**
   * Lo que pasa después, igual para las tres: se cierra la caja, se dice qué ocurrió y el foco va
   * al aviso — que es lo único que queda explicando lo que acaba de pasar, porque la caja ya no
   * existe y, si se eliminó, tampoco la fila.
   *
   * <p>El error sale de `mensajeDeError` y no de la clave genérica a secas: los dos rechazos del
   * borrado —publicado, con ventas— llegan con su código y cada uno dice qué hacer. Sin esto, "no
   * pudimos eliminar el producto" deja a quien opera sin saber cuál de los dos le tocó.
   */
  private manejadores(producto: ProductoAdmin, accion: AccionDeFila) {
    return {
      onSuccess: () => {
        this.confirmando.set(null);
        this.aviso.set({ clave: TEXTOS_DE_CONFIRMACION[accion].hecho, nombre: producto.nombre });
        this.enfocarDespuesDePintar(() => this.avisoLista()?.nativeElement);
      },
      onError: (error: unknown) =>
        this.error.set(mensajeDeError(error, this.transloco, CLAVE_ERROR[accion])),
    };
  }

  protected irAPagina(pagina: number): void {
    this.navegarA({ ...this.filtro(), pagina });
  }

  private navegarA(filtro: FiltroProductosAdmin): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: queryParamsDesdeFiltro(filtro),
    });
  }
}
