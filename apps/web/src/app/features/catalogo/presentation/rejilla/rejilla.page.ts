import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsTarjetaProducto } from '../tarjeta-producto/ts-tarjeta-producto';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';
import { hayFiltrosActivos } from '../../domain/filtro-productos.model';
import { filtroDesdeQueryParams } from '../../domain/query-params-filtro';
import { FiltrosProductos } from '../filtros/filtros-productos';

@Component({
  selector: 'app-rejilla',
  imports: [TranslocoPipe, TsTarjetaProducto, TsEsqueleto, TsBoton, FiltrosProductos],
  templateUrl: './rejilla.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RejillaPage {
  /** Una fila en escritorio: las candidatas reales a LCP. Ver la plantilla. */
  protected readonly TARJETAS_PRIORITARIAS = 4;

  private readonly route = inject(ActivatedRoute);

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });

  private readonly filtro = computed(() => filtroDesdeQueryParams(this.queryParams()));

  protected readonly consulta = usarBusquedaProductos(this.filtro);

  protected readonly hayFiltros = computed(() => hayFiltrosActivos(this.filtro()));

  protected readonly productos = computed(
    () => this.consulta.data()?.pages.flatMap((pagina) => pagina.items) ?? [],
  );

  protected readonly marcadoresDeCarga = [1, 2, 3, 4];
}
