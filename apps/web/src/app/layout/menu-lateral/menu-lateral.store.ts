import { DOCUMENT, Injectable, afterNextRender, computed, inject, signal } from '@angular/core';

/**
 * El estado del menú lateral, compartido.
 *
 * Es `providedIn: 'root'` y no una señal del componente porque lo miran **tres** elementos que no
 * tienen relación padre-hijo: el propio menú, el encabezado —que corre el logo para que el panel no
 * lo tape— y `app-root`, que reserva el hueco de la izquierda. Es la excepción que
 * `apps/web/CLAUDE.md` describe para `CarritoStore`, y por el mismo motivo: con una fábrica, cada
 * uno tendría su propia señal aislada y el logo no se enteraría de que el menú se abrió.
 *
 * <b>Dos estados y no uno.</b> "Desplegado" es el gesto —el puntero está encima, o el foco está
 * dentro— y se apaga solo. "Fijado" es una decisión de quien mira, y no se apaga hasta que la
 * deshaga. La diferencia importa porque cambian cosas distintas: desplegar pinta el panel
 * <b>encima</b> del contenido, y fijar le hace sitio de verdad. Si fijar solo dejara el panel
 * abierto encima, taparía justo lo que se está mirando y no serviría para nada.
 */
@Injectable({ providedIn: 'root' })
export class MenuLateralStore {
  private readonly documento = inject(DOCUMENT);

  /**
   * ¿Quien mira pidió menos movimiento? Las dos vías del proyecto: la preferencia del sistema
   * operativo y el atributo `data-movimiento` de `<html>`. El atributo hoy **no lo escribe nadie**: la casilla del pie que lo ponía se quitó el 25 de
   * septiembre de 2026. La lectura se queda porque es lo que haría falta el día que el control
   * vuelva; `prefers-reduced-motion` sigue funcionando igual. Quien la pide recibe
   * el cambio de ancho de golpe, no una versión acelerada del mismo barrido — y aquí pesa más que
   * en el panel móvil porque la distancia es de 216 px.
   *
   * Vive en el store y no en el componente porque la miran los dos elementos que animan: el panel y
   * el relleno de `app-root`. Y es una señal rellenada en `afterNextRender` y **no** un `computed`:
   * un `computed` que lee el DOM no lee ninguna señal, así que se evalúa una sola vez y no se
   * recalcula nunca (`apps/web/CLAUDE.md`). En el servidor no hay `matchMedia`, así que arranca en
   * `false`: el valor inicial tiene que ser el que no rompe nada si nadie lo corrige.
   */
  readonly sinMovimiento = signal(false);

  constructor() {
    afterNextRender(() => {
      const documento = this.documento;
      this.sinMovimiento.set(
        documento.documentElement.getAttribute('data-movimiento') === 'reducido' ||
          (documento.defaultView?.matchMedia('(prefers-reduced-motion: reduce)').matches ?? false),
      );
    });
  }

  /** El gesto: el puntero encima o el foco dentro. Se apaga al retirarse. */
  private readonly desplegadoPorElGesto = signal(false);

  /**
   * La decisión: el panel se queda abierto aunque el puntero se vaya.
   *
   * No se persiste, y es una decisión consciente: sobrevive a toda la navegación de la sesión
   * —esto es un singleton de raíz— y se olvida al recargar. Guardarlo en `localStorage` exigiría un
   * puerto, un proveedor y una guarda de plataforma para el servidor, y el valor que devuelve es
   * recordar un clic. Si molesta, se añade con el patrón de `ALMACEN_CARRITO_ID`.
   */
  readonly fijado = signal(false);

  /** ¿El panel está abierto, por el gesto o por decisión? */
  readonly abierto = computed(() => this.fijado() || this.desplegadoPorElGesto());

  /** ¿Está abierto **encima** del contenido? Fijado no: ahí el contenido se corrió. */
  readonly superpuesto = computed(() => this.desplegadoPorElGesto() && !this.fijado());

  abrirPorElGesto(): void {
    this.desplegadoPorElGesto.set(true);
  }

  /**
   * Cierra el gesto. Con el panel fijado no cierra nada —`abierto` sigue siendo cierto—, pero se
   * apaga igual: si no, al desfijar quedaría abierto sin que el puntero esté encima.
   */
  cerrarPorElGesto(): void {
    this.desplegadoPorElGesto.set(false);
  }

  alternarFijado(): void {
    this.fijado.update((fijado) => !fijado);
  }
}
