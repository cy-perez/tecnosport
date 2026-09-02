import { ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsTarjetaProducto } from '../../../../shared/ts-tarjeta-producto/ts-tarjeta-producto';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';

@Component({
  selector: 'app-rejilla',
  imports: [TranslocoPipe, TsTarjetaProducto, TsEsqueleto, TsBoton],
  templateUrl: './rejilla.page.html',
  styleUrl: './rejilla.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RejillaPage {
  protected readonly consulta = usarBusquedaProductos(() => ({}));

  protected readonly productos = computed(() => this.consulta.data()?.pages.flatMap((pagina) => pagina.items) ?? []);

  protected readonly marcadoresDeCarga = [1, 2, 3, 4];
}
