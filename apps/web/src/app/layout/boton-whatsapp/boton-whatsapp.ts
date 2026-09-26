import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TsIconoMarca } from '../../shared/ui/icono/ts-icono-marca';
import { marcaWhatsapp } from '../../shared/ui/icono/marcas.generado';

/**
 * El botón flotante de WhatsApp: la atención a un toque, desde cualquier pantalla.
 *
 * <p>Se pidió el 26 de septiembre de 2026 con wargosports.com de referencia, donde vive abajo a la
 * derecha como un círculo oscuro con el glifo en blanco. Aquí es lo mismo con los tokens propios,
 * más dos cosas que la referencia no tiene: una etiqueta que se abre al acercarse —el glifo solo no
 * dice a dónde lleva a quien no lo reconoce, que es el mismo argumento por el que los enlaces de
 * redes del pie llevan su nombre al lado— y el halo que late, que es el «efecto llamativo» que se
 * pidió.
 *
 * <p><b>El número no se escribe aquí.</b> Sale de `pie.whatsapp_numero`, que es la única copia que
 * `npm run datos-negocio` cruza contra el resto del sitio y contra los textos legales. Ya pasó una
 * vez que el celular estuviera mal en el pie y en tres párrafos legales durante una fase entera.
 *
 * <h2>El mensaje viene escrito</h2>
 *
 * <p>El enlace lleva `?text=`, así que la conversación empieza con una frase ya puesta en vez de
 * con una casilla vacía. No es adorno: quien escribe desde el botón flotante suele estar mirando
 * algo concreto y no sabe por dónde empezar, y del otro lado un «Hola» suelto no dice de qué
 * producto se habla. El texto va por Transloco como todo lo visible, y `encodeURIComponent` porque
 * lleva tildes y signos que en una URL no pueden ir crudos.
 *
 * <h2>Dónde se monta</h2>
 *
 * <p>En `app.html`, **después** del pie y fuera de su `@defer`. Después porque así es el último
 * elemento del orden de tabulación y no el segundo: un `fixed` no tiene sitio en el flujo, pero sí
 * en el orden del documento, y un botón de chat antes del contenido se lleva el primer Tab de todas
 * las páginas. Fuera del `@defer` porque el pie se hidrata al entrar en pantalla y esto tiene que
 * responder desde el principio.
 */
@Component({
  selector: 'app-boton-whatsapp',
  imports: [TranslocoPipe, TsIconoMarca],
  templateUrl: './boton-whatsapp.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BotonWhatsapp {
  private readonly transloco = inject(TranslocoService);

  protected readonly marcaWhatsapp = marcaWhatsapp;

  /**
   * `https://wa.me/<número>?text=<mensaje>`.
   *
   * <p>Un `computed` y no un campo: el mensaje se traduce, así que cambia al cambiar de idioma, y
   * `activeLang` es una señal. Un campo calculado en el constructor se quedaría con el idioma con
   * el que arrancó la aplicación — el mismo defecto que `apps/web/CLAUDE.md` describe para
   * `translate()` dentro de un inicializador.
   */
  protected readonly enlace = computed(() => {
    // Se lee para que el `computed` dependa de él: `translate()` no lee ninguna señal por su cuenta.
    this.transloco.activeLang();
    const numero = this.transloco.translate('pie.whatsapp_numero');
    const mensaje = this.transloco.translate('whatsapp.mensaje');
    return `https://wa.me/${numero}?text=${encodeURIComponent(mensaje)}`;
  });
}
