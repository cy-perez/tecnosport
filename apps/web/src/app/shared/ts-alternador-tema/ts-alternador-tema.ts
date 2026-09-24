import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
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

  protected alternar(): void {
    this.tema.alternar();
  }
}
