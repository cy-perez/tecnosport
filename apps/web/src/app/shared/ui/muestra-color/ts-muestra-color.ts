import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ParteDeMuestra, PatronDeColor } from './muestra-color.model';

/** Un dibujo en el lienzo de 100 × 100 del SVG: unidades del `viewBox`, no píxeles de CSS. */
interface Porcion {
  readonly trazo: string;
  readonly relleno: string;
  readonly patron: Patron | null;
}

interface Patron {
  readonly id: string;
  readonly tipo: PatronDeColor;
  readonly colores: readonly string[];
}

/** Las cuñas salen del centro con este radio: llegan a las esquinas de un cuadrado de 100. */
const RADIO = 75;
const CENTRO = 50;

/**
 * La primera porción empieza abajo y gira por la izquierda: con dos colores, el primero queda a la
 * izquierda y el segundo a la derecha, que es como se lee «Negro / Rojo» (decidido por el negocio
 * el 4 de octubre de 2026). Con tres, tres tercios en el mismo sentido.
 */
const INICIO_EN_GRADOS = 180;

let siguienteId = 0;

function punto(grados: number): string {
  const radianes = (grados * Math.PI) / 180;
  const x = CENTRO + RADIO * Math.sin(radianes);
  const y = CENTRO - RADIO * Math.cos(radianes);
  return `${x.toFixed(3)} ${y.toFixed(3)}`;
}

function cuna(desde: number, hasta: number): string {
  const grande = hasta - desde > 180 ? 1 : 0;
  return `M ${CENTRO} ${CENTRO} L ${punto(desde)} A ${RADIO} ${RADIO} 0 ${grande} 1 ${punto(hasta)} Z`;
}

/** Un cuadrado que cubre todo el lienzo: la porción única no se parte. */
const TODO = 'M -1 -1 H 101 V 101 H -1 Z';

/**
 * El círculo de un color: una a tres porciones, cada una lisa o con su patrón —franjas, puntos,
 * manchas—. Es solo el dibujo: toma la forma de su contenedor —redondo en la tarjeta, cuadrado en
 * la ficha— porque llena la caja y el contenedor recorta con su `overflow-hidden`, y es decorativo
 * (`aria-hidden`): el nombre del color lo dice el botón que lo envuelve.
 *
 * Los colores llegan de la paleta, en la base; aquí solo se pone la forma. Por eso no hay un HEX
 * literal y la regla de los tokens sigue intacta.
 */
@Component({
  selector: 'ts-muestra-color',
  templateUrl: './ts-muestra-color.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block size-full' },
})
export class TsMuestraColor {
  readonly partes = input.required<readonly ParteDeMuestra[]>();

  private readonly prefijo = `ts-muestra-${siguienteId++}`;

  protected readonly porciones = computed<Porcion[]>(() => {
    const partes = this.partes();
    const paso = 360 / Math.max(partes.length, 1);
    return partes.map((parte, indice) => {
      const desde = INICIO_EN_GRADOS + paso * indice;
      const trazo = partes.length === 1 ? TODO : cuna(desde, desde + paso);
      if (parte.patron === null) {
        return { trazo, relleno: parte.colores[0] ?? 'transparent', patron: null };
      }
      const patron = {
        id: `${this.prefijo}-${indice}`,
        tipo: parte.patron,
        colores: parte.colores,
      };
      return { trazo, relleno: `url(#${patron.id})`, patron };
    });
  });

  /** Las franjas del multicolor: una por color, del mismo ancho. */
  protected franjas(colores: readonly string[]): { x: number; ancho: number; color: string }[] {
    const ancho = 100 / colores.length;
    return colores.map((color, indice) => ({ x: indice * ancho, ancho, color }));
  }
}
