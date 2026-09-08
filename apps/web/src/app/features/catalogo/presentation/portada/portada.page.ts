import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { organizacionJsonLd, sitioWebJsonLd } from '../../../../core/seo/datos-estructurados';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { usarDatosEstructurados } from '../../../../core/seo/usar-metadatos';
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

  private readonly traducir = usarTraductor();

  /**
   * `Organization` y `WebSite`, solo aquí y no en cada pantalla: repetirlos en todas no añade nada
   * y multiplica los sitios donde un dato del negocio puede quedar desactualizado.
   *
   * Los datos salen de las claves del pie, que son las que el sitio ya publica por obligación legal
   * (Ley 1480: nombre, NIT, dirección, teléfono y correo visibles). Una segunda copia aquí sería
   * exactamente la divergencia que este proyecto lleva toda la fase evitando.
   */
  private readonly datosEstructurados = computed<object[]>(() => {
    const traducir = this.traducir();
    const origen = origenPublico();
    return [
      organizacionJsonLd(origen, {
        nombre: traducir('pie.nombre_comercial'),
        nit: traducir('pie.nit'),
        direccion: traducir('pie.direccion'),
        telefono: traducir('pie.telefono_e164'),
        correo: traducir('pie.correo'),
      }),
      sitioWebJsonLd(origen, this.idioma(), traducir('app.titulo')),
    ];
  });

  constructor() {
    usarDatosEstructurados(() => this.datosEstructurados());
  }

  protected claveDeLinea(linea: string): string {
    return `catalogo.filtros.linea.${linea.toLowerCase()}`;
  }
}
