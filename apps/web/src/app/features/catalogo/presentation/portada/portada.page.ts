import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsTarjetaProducto } from '../tarjeta-producto/ts-tarjeta-producto';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';
import { FILTRO_NOVEDADES, LINEAS } from '../../domain/filtro-productos.model';

@Component({
  selector: 'app-portada',
  imports: [TranslocoPipe, RouterLink, TsTarjetaProducto, TsEsqueleto],
  templateUrl: './portada.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortadaPage {
  private readonly transloco = inject(TranslocoService);

  protected readonly lineas = LINEAS;
  protected readonly marcadoresDeCarga = [1, 2, 3, 4];

  protected readonly consulta = usarBusquedaProductos(() => FILTRO_NOVEDADES);

  protected readonly novedades = computed(
    () => this.consulta.data()?.pages.flatMap((pagina) => pagina.items) ?? [],
  );

  protected readonly idioma = this.transloco.activeLang;

  protected claveDeLinea(linea: string): string {
    return `catalogo.filtros.linea.${linea.toLowerCase()}`;
  }
}
