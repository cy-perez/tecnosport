import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsBoton } from '../../../../../shared/ts-boton/ts-boton';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { usarListarProductosAdmin } from '../../application/listar-productos-admin.consulta';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import { EstadoProducto, FiltroProductosAdmin } from '../../domain/producto-admin.model';

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
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto],
  templateUrl: './lista-productos-admin.page.html',
  styleUrl: './lista-productos-admin.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaProductosAdminPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  private readonly queryParams = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });
  protected readonly filtro = computed<FiltroProductosAdmin>(() => filtroDesdeQueryParams(this.queryParams()));

  protected readonly consulta = usarListarProductosAdmin(this.filtro);

  protected etiquetaEstado(estado: EstadoProducto): string {
    return this.transloco.translate(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  protected paginaAnterior(): void {
    this.navegarA({ ...this.filtro(), pagina: this.filtro().pagina - 1 });
  }

  protected paginaSiguiente(): void {
    this.navegarA({ ...this.filtro(), pagina: this.filtro().pagina + 1 });
  }

  private navegarA(filtro: FiltroProductosAdmin): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: queryParamsDesdeFiltro(filtro) });
  }
}
