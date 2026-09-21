import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
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
 * La lista del catálogo desde el panel, y el único sitio donde un producto pasa de BORRADOR a
 * PUBLICADO.
 *
 * <p><b>Las dos transiciones preguntan antes</b>, y cada una por su motivo. Publicar deja el
 * producto a la vista de quien compra. Retirar lo saca de la vitrina: desaparece de la rejilla,
 * su enlace pasa a responder 404 y sale del sitemap en la siguiente generación — los pedidos ya
 * creados, en cambio, siguen su curso intactos.
 *
 * <p>La confirmación va dentro de la fila y no en un diálogo: es una pregunta de una línea, y
 * abrir un modal con trampa de foco para eso es más ceremonia que la decisión. Y es **una sola**
 * fila de confirmación para las dos acciones, porque no pueden estar abiertas a la vez: un
 * producto o está publicado o no lo está.
 */
@Component({
  selector: 'app-lista-productos-admin',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsMigas, TsPaginador],
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

  /** El producto con la confirmación de publicar abierta. `null` = ninguna. */
  protected readonly confirmando = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  /** El nombre de lo último publicado, para confirmarlo cuando su fila ya cambió de estado. */
  protected readonly publicado = signal<string | null>(null);
  /** Lo mismo al retirar: la fila cambia de botón y el mensaje tiene que quedar a la vista. */
  protected readonly retirado = signal<string | null>(null);

  private readonly mutacion = usarPublicarProducto();
  private readonly mutacionRetirar = usarDespublicarProducto();
  protected readonly publicando = computed(
    () => this.mutacion.isPending() || this.mutacionRetirar.isPending(),
  );

  protected preguntar(producto: ProductoAdmin): void {
    this.error.set(null);
    this.publicado.set(null);
    this.retirado.set(null);
    this.confirmando.set(producto.id);
  }

  protected cancelar(): void {
    this.confirmando.set(null);
  }

  /**
   * Retirar de la vitrina. Comparte la fila de confirmación con publicar y no tiene la suya
   * porque son la misma pregunta —"¿seguro?"— sobre la misma fila; lo que cambia es el texto, y
   * eso lo decide la plantilla mirando el estado.
   */
  protected despublicar(producto: ProductoAdmin): void {
    this.error.set(null);
    this.mutacionRetirar.mutate(producto.id, {
      onSuccess: () => {
        this.confirmando.set(null);
        this.retirado.set(producto.nombre);
      },
      onError: (error) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.productos.publicar.errorRetirar')),
    });
  }

  protected publicar(producto: ProductoAdmin): void {
    this.error.set(null);
    this.mutacion.mutate(producto.id, {
      onSuccess: () => {
        this.confirmando.set(null);
        this.publicado.set(producto.nombre);
      },
      // El 409 de "no tiene imagen principal" es accionable y se dice: quien publica tiene que
      // saber que le falta la foto, no que "no se pudo".
      onError: (error) =>
        this.error.set(mensajeDeError(error, this.transloco, 'admin.productos.publicar.error')),
    });
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
