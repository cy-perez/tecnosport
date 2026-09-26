import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { cn } from '../ui/cn';
import { usarTraductor } from '../../core/i18n/traductor';
import { ServicioTema } from '../../core/tema/tema.servicio';
import { TsIcono } from '../ui/icono/ts-icono';
import { iconoTemaClaro, iconoTemaOscuro } from '../ui/icono/iconos';

/**
 * Lo común a las dos formas. El radio va en las dos: en la plana no hay borde que redondear, pero
 * es el que le da su forma al anillo de foco.
 */
const BASE =
  'flex size-tactil cursor-pointer items-center justify-center rounded-completo p-0 transition-colors';

/** El chip del encabezado, la forma que empareja este botón con `ts-alternador-idioma`. */
const CHIP = 'border border-ts-borde bg-ts-superficie text-ts-texto hover:bg-ts-superficie-alt';

/**
 * El icono desnudo de la franja del pie. El hover es un cambio de color, como en la referencia,
 * pero al revés: allá la base está apagada y el hover la sube a color pleno; aquí la base ya es
 * `sobre-marca` a plena fuerza —igual que el copyright y los legales de su fila— y no hay un token
 * apagado sobre marca que inventar, así que el hover baja al 70 %.
 */
const PLANO = 'border-0 bg-transparent text-inherit hover:text-ts-sobre-marca/70';

/**
 * El botón que alterna claro y oscuro. La cookie y la escritura de `data-tema`
 * son de `ServicioTema` (`core/tema/`): un componente de `shared/` no decide la
 * política de persistencia del sitio.
 *
 * Fue un `<select>` de tres opciones —claro, oscuro y seguir al sistema— y
 * ahora es un botón de dos estados.
 *
 * **No tiene estado.** Ni señal, ni `afterNextRender`, ni valor inicial que
 * corregir después de hidratar. Qué icono se ve lo decide el CSS, colgado del
 * mismo `data-tema` que ya está puesto en `<html>` — el patrón que el
 * encabezado ya usa para el logo positivo y el negativo. Resuelve un problema
 * concreto: el servidor **no puede saber** el tema mientras renderiza, porque
 * `conTemaAplicado` inyecta `data-tema` por reemplazo de cadena sobre el HTML
 * ya construido, así que un icono atado a una señal se pintaría en claro y
 * saltaría al de oscuro al hidratar. Y cuál es el tema actual al pulsar lo
 * responde el servicio leyendo el documento, que es donde de verdad vive.
 */
@Component({
  selector: 'ts-alternador-tema',
  imports: [TsIcono],
  templateUrl: './ts-alternador-tema.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `inline-flex` para que el elemento propio no herede el `display: inline`,
  // que le sumaría el interlineado a un botón de 44 px si algún día cuelga de
  // un padre que no sea flex. Mismo host que `ts-alternador-idioma`.
  host: { class: 'inline-flex' },
})
export class TsAlternadorTema {
  /**
   * Conmuta el anillo de foco al par `sobre-marca`, para cuando el botón se pinta sobre una franja
   * de `--color-marca`.
   *
   * <p>No es un capricho de tema: en tema claro `--color-foco` y `--color-marca` son el mismo
   * grafito (`#1B1F26`), así que con `outline-offset: 2px` el anillo se dibuja **fuera** del
   * círculo, sobre la franja, y es literalmente invisible. Es el mismo problema que ya obligó a
   * crear `anillo-foco-sobre-marca` para los enlaces del pie y el input `sobreMarca` de
   * `ts-checkbox`.
   *
   * <p>Apareció el 25 de septiembre de 2026, cuando el alternador entró a la franja final del pie:
   * hasta entonces solo vivía en el encabezado, sobre `--color-superficie`, donde el anillo normal
   * es el correcto. Lo levantó la auditoría de accesibilidad.
   */
  readonly sobreMarca = input(false);

  /**
   * Quita el borde, el fondo claro y el hover de fondo, y deja el icono desnudo heredando el color
   * de la superficie que lo contiene. Es la forma que el pie de referencia de Preline —"Footer with
   * Newsletter Signup and Link Columns"— le da al alternador: un icono suelto en la franja final,
   * no un chip.
   *
   * <p><b>La caja de 44 px no se va con la bandera.</b> El icono baja a 16 px, como el `size-4` de
   * la referencia y como los demás iconos del pie, pero el botón sigue midiendo `size-tactil`: un
   * objetivo de 16 px incumple WCAG 2.5.8, y la excepción "inline" —la que el pie invoca para los
   * enlaces que van dentro de una frase, cuyo tamaño lo fija el interlineado— no cubre un botón
   * suelto. Cambia lo que se ve, no lo que se pulsa. Es el mismo patrón que la flecha del carrusel
   * del hero, que ya era un icono desnudo sobre `--color-marca`.
   *
   * <p>El encabezado no la pasa: allí el chip con borde es lo que empareja este botón con
   * `ts-alternador-idioma`, que va a su lado.
   */
  readonly plano = input(false);

  private readonly tema = inject(ServicioTema);

  private readonly traducir = usarTraductor();

  /**
   * `usarTraductor()` y no el pipe `| transloco`, que es lo que usa el resto
   * del encabezado. No es que el pipe falle —en el navegador traduce bien—,
   * sino que en este entorno de pruebas no reacciona a un cambio de idioma, ni
   * forzando la detección de cambios ni esperando por el resultado: el nombre
   * del botón se queda en el idioma anterior y no hay forma de fijarlo con una
   * prueba. Con una señal sí, y `usarTraductor` es la del proyecto para leer
   * una clave suelta: cuelga de `translationLoadSuccess` **y** de `langChanged`,
   * así que cubre tanto el scope que llega tarde como el cambio de idioma.
   */
  protected readonly etiqueta = computed(() => this.traducir()('tema.alternar'));

  protected readonly iconoTemaClaro = iconoTemaClaro;
  protected readonly iconoTemaOscuro = iconoTemaOscuro;

  /**
   * El anillo es <b>uno u otro</b>, nunca los dos.
   *
   * <p>Esto empezó siendo `cn(BASE_CON_ANILLO_NORMAL, sobreMarca() && 'anillo-foco-sobre-marca')`,
   * dando por hecho que `tailwind-merge` descartaría el primero. No lo hace: `anillo-foco` y
   * `anillo-foco-sobre-marca` son utilidades propias del proyecto y `cn` no las conoce como grupo
   * en conflicto, así que el botón salía con las dos y cuál ganaba lo decidía el orden en la hoja
   * generada, no el orden aquí. Medido en el navegador, no supuesto: el `class` tenía las dos.
   *
   * <p>Con el ternario no hay nada que resolver. Registrar el grupo en `cn.ts` sería el arreglo
   * sistémico —y es lo que `apps/web/CLAUDE.md` pide al añadir un token con nombre no numérico—
   * pero para dos clases mutuamente excluyentes en un solo componente, elegir es más claro que
   * enseñarle a una librería a descartar.
   */
  protected readonly clases = computed(() =>
    cn(
      BASE,
      this.plano() ? PLANO : CHIP,
      this.sobreMarca() ? 'anillo-foco-sobre-marca' : 'anillo-foco',
    ),
  );

  /**
   * 16 px en la forma plana; los 24 px por omisión de `ts-icono` en el chip del encabezado.
   *
   * <p>Va aquí y no como un segundo input de tamaño porque no es una medida que se elija: es la
   * que la forma plana necesita para leerse en una franja de texto pequeño.
   */
  protected readonly claseIcono = computed(() => (this.plano() ? 'size-16' : ''));

  protected alternar(): void {
    this.tema.alternar();
  }
}
