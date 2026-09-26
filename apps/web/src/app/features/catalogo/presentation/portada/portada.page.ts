import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import type { IconNode } from 'lucide';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { organizacionJsonLd, sitioWebJsonLd } from '../../../../core/seo/datos-estructurados';
import { origenPublico } from '../../../../core/seo/origen-publico';
import { usarDatosEstructurados } from '../../../../core/seo/usar-metadatos';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import {
  iconoContraentrega,
  iconoEnvio,
  iconoGarantia,
  iconoMediosDePago,
} from '../../../../shared/ui/icono/iconos';
import { TsTarjetaProducto } from '../tarjeta-producto/ts-tarjeta-producto';
import { TsCarruselHero } from './hero/ts-carrusel-hero';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';
import { FILTRO_NOVEDADES, LINEAS } from '../../domain/filtro-productos.model';

/**
 * Un sello de confianza: su icono y las dos claves de su texto.
 *
 * <p>Vienen de `ts-carrusel-hero`, donde vivieron hasta el 26 de septiembre de 2026 con una sola
 * clave cada uno. Ahora son dos —titular y línea de apoyo— porque esa es la forma de la página
 * de referencia y porque el titular solo no decía lo suficiente: "Envíos a todo el país" no
 * aclara quién paga el envío, y quien lo lee se lo imagina.
 *
 * <p>Van como dato y no escritos cuatro veces en la plantilla por lo de siempre: cuatro copias de
 * la misma lista de clases son cuatro sitios donde se puede desalinear una.
 */
interface SelloDeConfianza {
  readonly titulo: string;
  readonly apoyo: string;
  readonly icono: IconNode;
}

/**
 * Los cuatro sellos: lo que el sitio sí puede prometer.
 *
 * <p>El orden es el del recorrido de compra —cómo llega, cómo se paga, con qué se paga, qué pasa
 * después— y no un ranking. Los cuatro se pintan iguales por la misma razón que los cuatro
 * botones del carrusel (`ADR-0064`): cuatro cosas que valen lo mismo no llevan jerarquía.
 */
const SELLOS_DE_CONFIANZA: readonly SelloDeConfianza[] = [
  {
    titulo: 'portada.confianza.envio.titulo',
    apoyo: 'portada.confianza.envio.apoyo',
    icono: iconoEnvio,
  },
  {
    titulo: 'portada.confianza.contraentrega.titulo',
    apoyo: 'portada.confianza.contraentrega.apoyo',
    icono: iconoContraentrega,
  },
  {
    titulo: 'portada.confianza.pago.titulo',
    apoyo: 'portada.confianza.pago.apoyo',
    icono: iconoMediosDePago,
  },
  {
    titulo: 'portada.confianza.garantia.titulo',
    apoyo: 'portada.confianza.garantia.apoyo',
    icono: iconoGarantia,
  },
];

@Component({
  selector: 'app-portada',
  imports: [TranslocoPipe, RouterLink, TsCarruselHero, TsTarjetaProducto, TsEsqueleto, TsIcono],
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

  protected readonly sellosDeConfianza = SELLOS_DE_CONFIANZA;

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
