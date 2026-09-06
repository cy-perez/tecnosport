import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarListarProductosAdmin } from '../../application/listar-productos-admin.consulta';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import { EstadoProducto, FiltroProductosAdmin, ProductoAdmin } from '../../domain/producto-admin.model';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoProducto, string> = {
  BORRADOR: 'admin.productos.estados.borrador',
  PUBLICADO: 'admin.productos.estados.publicado',
};

/**
 * Primer caso de uso de Track B: solo listar, sin filtros ni acciones todavía — cada uno de
 * crear/editar, variantes+existencias e imágenes es su propio caso de uso en una sesión futura.
 */
@Component({
  selector: 'app-lista-productos-admin',
  imports: [RouterLink, TranslocoPipe, TsEsqueleto, TsMigas, TsPaginador],
  templateUrl: './lista-productos-admin.page.html',
  styleUrl: './lista-productos-admin.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaProductosAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.productos.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });
  protected readonly filtro = computed<FiltroProductosAdmin>(() => filtroDesdeQueryParams(this.queryParams()));

  protected readonly consulta = usarListarProductosAdmin(this.filtro);

  protected readonly productos = computed<readonly ProductoAdmin[]>(() => this.consulta.data()?.items ?? []);

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

  protected irAPagina(pagina: number): void {
    this.navegarA({ ...this.filtro(), pagina });
  }

  private navegarA(filtro: FiltroProductosAdmin): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: queryParamsDesdeFiltro(filtro) });
  }
}
