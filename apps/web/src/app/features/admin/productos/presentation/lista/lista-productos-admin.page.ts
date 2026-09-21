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
import { usarPublicarProducto } from '../../application/publicar-producto.mutacion';
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
 * <p><b>Publicar pide confirmación, y no por costumbre</b>: el dominio no sabe despublicar
 * —`Producto.publicar()` es de una sola vía y no hay endpoint de vuelta—, así que un clic de más
 * deja el producto en la vitrina y sacarlo de ahí exige tocar la base. La confirmación va dentro
 * de la fila y no en un diálogo: es una pregunta de una línea, y abrir un modal con trampa de foco
 * para eso es más ceremonia que la decisión.
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

  private readonly mutacion = usarPublicarProducto();
  protected readonly publicando = computed(() => this.mutacion.isPending());

  protected preguntar(producto: ProductoAdmin): void {
    this.error.set(null);
    this.publicado.set(null);
    this.confirmando.set(producto.id);
  }

  protected cancelar(): void {
    this.confirmando.set(null);
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
