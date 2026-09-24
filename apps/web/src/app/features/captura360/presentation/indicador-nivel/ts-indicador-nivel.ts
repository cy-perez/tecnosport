import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AnuncioDeNivel, EstadoDeNivel, Nivel } from '../../domain/nivel-360';

// Sobre la vista de cámara hace falta un fondo propio: el contraste del vídeo
// no se controla (docs/10-captura-360.md).
//
// **`marca` y no `primario`, y es una corrección.** `--color-primario` no es el mismo color en los
// dos temas: grafito en claro, ámbar en oscuro. Este chip vive sobre una vista de cámara, no sobre
// una superficie del tema, así que el tema no debería moverlo — y moviéndolo, los tres colores de
// estado de abajo quedaban pintados sobre un fondo que cambia bajo ellos. `--color-marca` es
// oscuro en los dos temas y `--color-sobre-marca` claro en los dos.
const BASE = 'm-0 flex items-center gap-8 px-12 py-8 font-texto font-medio';
const CON_SENSOR = `${BASE} bg-ts-marca text-ts-sobre-marca`;
const SIN_SENSOR = `${BASE} bg-ts-superficie-alt text-ts-texto`;

// `text-base` y no la escala de espacio: el SCSS usaba `var(--esp-16)` para un
// tamaño de fuente. Son los mismos 16 px, pero ahora sale del token
// tipográfico, que es el que corresponde.
const GLIFO = 'text-base leading-none';

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

  /**
   * Qué dice la región viva, que **no es lo que pinta el texto de arriba**: llega ya asentado, con
   * los grados congelados del momento en que el estado se sostuvo. Por eso habla unas pocas veces
   * por minuto en vez de veinte por segundo. Quién lo asienta es el store, con el reloj que traen
   * las propias lecturas del sensor.
   *
   * `null` mientras no haya nada que decir todavía, y entonces la región vive vacía.
   */
  readonly anuncio = input<AnuncioDeNivel | null>(null);

  /** El anuncio solo cuando de verdad hay algo dicho: sin esto la región pintaría `nivel.null`. */
  protected readonly dicho = computed(() => {
    const anuncio = this.anuncio();
    return anuncio === null || anuncio.clave === null
      ? null
      : { clave: anuncio.clave, grados: anuncio.grados };
  });

  protected readonly estado = computed<EstadoDeNivel>(() => this.nivel().estado);

  protected readonly clases = computed(() =>
    this.estado() === 'SIN_SENSOR' ? SIN_SENSOR : CON_SENSOR,
  );

  /**
   * El glifo hereda el color del chip, y **ya no lleva uno por estado**.
   *
   * Lo llevaba —`exito`, `acento`, `error`— y estaba roto en los dos temas, porque esos tres
   * tokens están calibrados contra `fondo`/`superficie` y aquí se pintaban sobre `primario`: en
   * claro, verde y rojo sobre grafito quedaban por debajo del 3:1 que pide un elemento gráfico; en
   * oscuro, `acento` es ámbar y `primario` también, o sea el glifo de "CERCA" era invisible. No lo
   * veía `npm run contrastes` porque prueba pares declarados —`sobre-primario` sobre `primario`— y
   * no combinaciones reales de plantilla.
   *
   * La información no se pierde: el estado ya se distingue por la forma del glifo (●, ◐, ○) y por
   * el texto de al lado, que además dice hacia dónde inclinar y cuántos grados. El color siempre
   * fue el canal redundante, y este componente lo dice desde su primera versión.
   *
   * Devolverle el color pide un trío de estados *sobre marca* en el kit (regla dura #2: si falta un
   * valor, se añade al `tokens.json` y se regenera), no una clase de escape aquí.
   */
  protected readonly clasesGlifo = computed(() => GLIFO);

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
    const desviacion = nivel.ejeDominante === 'GIRAR' ? nivel.girar : nivel.inclinar;
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
    if (eje === 'GIRAR') {
      return signo > 0 ? 'captura360.nivel.gira_derecha' : 'captura360.nivel.gira_izquierda';
    }
    return signo > 0 ? 'captura360.nivel.inclina_atras' : 'captura360.nivel.inclina_adelante';
  });
}
