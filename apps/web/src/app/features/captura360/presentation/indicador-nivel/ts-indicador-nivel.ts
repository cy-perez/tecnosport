import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EstadoDeNivel, Nivel } from '../../domain/nivel-360';

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
  styleUrl: './ts-indicador-nivel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsIndicadorNivel {
  readonly nivel = input.required<Nivel>();

  /** La primera toma no se compara con nada: es la que fija la referencia del resto del set. */
  readonly fijandoReferencia = input(false);

  protected readonly estado = computed<EstadoDeNivel>(() => this.nivel().estado);

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
