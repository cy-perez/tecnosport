import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { cn } from '../ui/cn';
import { usarTraductor } from '../../core/i18n/traductor';
import { ServicioTema } from '../../core/tema/tema.servicio';
import { TsIcono } from '../ui/icono/ts-icono';
import { iconoTemaClaro, iconoTemaOscuro } from '../ui/icono/iconos';

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
      'flex size-tactil cursor-pointer items-center justify-center rounded-completo ' +
        'border border-ts-borde bg-ts-superficie p-0 text-ts-texto transition-colors ' +
        'hover:bg-ts-superficie-alt',
      this.sobreMarca() ? 'anillo-foco-sobre-marca' : 'anillo-foco',
    ),
  );

  protected alternar(): void {
    this.tema.alternar();
  }
}
