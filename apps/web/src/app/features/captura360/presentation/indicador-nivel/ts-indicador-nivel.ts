import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EstadoDeNivel, Nivel } from '../../domain/nivel-360';

// Sobre la vista de cámara hace falta un fondo propio: el contraste del vídeo
// no se controla (docs/10-captura-360.md).
const BASE = 'm-0 flex items-center gap-8 px-12 py-8 font-texto font-medio';
const CON_SENSOR = `${BASE} bg-ts-primario text-ts-sobre-primario`;
const SIN_SENSOR = `${BASE} bg-ts-superficie-alt text-ts-texto`;

// `text-base` y no la escala de espacio: el SCSS usaba `var(--esp-16)` para un
// tamaño de fuente. Son los mismos 16 px, pero ahora sale del token
// tipográfico, que es el que corresponde.
const GLIFO = 'text-base leading-none';

const COLOR_POR_ESTADO: Partial<Record<EstadoDeNivel, string>> = {
  EN_RANGO: 'text-ts-exito',
  CERCA: 'text-ts-acento',
  FUERA_DE_RANGO: 'text-ts-error',
};

/**
 * El estado del nivel, en tres niveles más su ausencia (`docs/10-captura-360.md`).
 *
 * **Nunca solo por color**: cada estado lleva su texto y su glifo, porque el color solo no es
 * accesible y porque sobre una vista de cámara ni siquiera es fiable. Y el mensaje dice qué hacer
 * —hacia dónde inclinar, en grados— en vez de limitarse a decir que algo está mal.
 */
@Component({
  selector: 'ts-indicador-nivel',
  imports: [TranslocoPipe],
  templateUrl: './ts-indicador-nivel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsIndicadorNivel {
  readonly nivel = input.required<Nivel>();

  /** La primera toma no se compara con nada: es la que fija la referencia del resto del set. */
  readonly fijandoReferencia = input(false);

  protected readonly estado = computed<EstadoDeNivel>(() => this.nivel().estado);

  protected readonly clases = computed(() =>
    this.estado() === 'SIN_SENSOR' ? SIN_SENSOR : CON_SENSOR,
  );

  /**
   * El color acompaña al texto y al glifo, nunca los reemplaza: los tres
   * estados se distinguen sin ver un solo color.
   */
  protected readonly clasesGlifo = computed(() => {
    const color = COLOR_POR_ESTADO[this.estado()];
    return color ? `${GLIFO} ${color}` : GLIFO;
  });

  protected readonly glifo = computed(() => {
    switch (this.estado()) {
      case 'EN_RANGO':
        return '●';
      case 'CERCA':
        return '◐';
      case 'FUERA_DE_RANGO':
        return '○';
      default:
        return '—';
    }
  });

  /** Cuántos grados y hacia dónde, sobre el eje que más lejos está. */
  protected readonly correccion = computed(() => {
    const nivel = this.nivel();
    const desviacion =
      nivel.ejeDominante === 'GAMMA' ? nivel.desviacionGamma : nivel.desviacionBeta;
    return {
      grados: Math.abs(Math.round(desviacion)),
      eje: nivel.ejeDominante,
      signo: Math.sign(desviacion),
    };
  });

  protected readonly claveDeMensaje = computed(() => {
    if (this.estado() === 'SIN_SENSOR') {
      return 'captura360.nivel.sin_sensor';
    }
    if (this.fijandoReferencia()) {
      return 'captura360.nivel.fijando_referencia';
    }
    if (this.estado() === 'EN_RANGO') {
      return 'captura360.nivel.en_rango';
    }

    const { eje, signo } = this.correccion();
    if (eje === 'GAMMA') {
      return signo > 0 ? 'captura360.nivel.gira_derecha' : 'captura360.nivel.gira_izquierda';
    }
    return signo > 0 ? 'captura360.nivel.inclina_atras' : 'captura360.nivel.inclina_adelante';
  });
}
