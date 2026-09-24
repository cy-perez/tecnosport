import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { organizacionJsonLd, sitioWebJsonLd } from '../../../../core/seo/datos-estructurados';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { usarDatosEstructurados } from '../../../../core/seo/usar-metadatos';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsTarjetaProducto } from '../tarjeta-producto/ts-tarjeta-producto';
import { TsHero } from './hero/ts-hero';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';
import { FILTRO_NOVEDADES, LINEAS } from '../../domain/filtro-productos.model';

@Component({
  selector: 'app-portada',
  imports: [TranslocoPipe, RouterLink, TsHero, TsTarjetaProducto, TsEsqueleto],
  templateUrl: './portada.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortadaPage {
  private readonly transloco = inject(TranslocoService);

  /**
   * Las cuatro líneas del modelo, siempre.
   *
   * <b>Esto filtraba hasta el 24 de septiembre de 2026</b>, y el motivo era bueno: la rejilla lleva
   * a `/productos?linea=...`, y en un catálogo de pura tecnología las otras baldosas eran enlaces a
   * una rejilla vacía en la primera pantalla del sitio. El filtro se deducía de las categorías, que
   * el servidor devolvía ya recortadas a las que tenían productos publicados.
   *
   * Ese recorte desapareció con el árbol —lo razona `ListarCategorias`—, así que este cálculo ya no
   * filtraba nada y solo dejaba la portada a merced de qué hubiera cargado. Se quita, y con él el
   * `usarCategorias()` que solo servía para esto: <b>las baldosas son las líneas del negocio</b>,
   * que es un dato del modelo y no de la existencia. Una portada que esconde "Calzado deportivo"
   * porque hoy no hay tenis cargados dice que el negocio no vende tenis.
   */
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
        redes: [traducir('pie.facebook_url'), traducir('pie.instagram_url')],
      }),
      sitioWebJsonLd(origen, this.idioma(), traducir('app.titulo')),
    ];
  });

  constructor() {
    usarDatosEstructurados(() => this.datosEstructurados());
  }

  protected claveDeLinea(linea: string): string {
    return `lineas.${linea.toLowerCase()}`;
  }
}
