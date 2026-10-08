import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Lo que se le ofrece a quien se quedó sin envío a domicilio —sin cobertura, un artículo que no se
 * puede asegurar o que no se ha medido, una cotización rechazada o caída—, como frase aparte de la
 * que explica el motivo. Antes cada motivo terminaba en "recoge en nuestro punto"; desde que la
 * recogida se puede apagar (8 de octubre de 2026) la salida depende de la bandera, y escribirla una
 * vez aquí evita que un motivo siga prometiendo una recogida que ya no existe.
 *
 * Con la recogida apagada la salida es WhatsApp: el pedido no se puede tomar en el sitio, y lo que
 * sí se puede es hablar con alguien que busque cómo entregarlo.
 */
@Component({
  selector: 'ts-salida-sin-envio',
  imports: [TranslocoPipe],
  template: `
    @if (retiroDisponible()) {
      {{ 'checkout.salida.retiro' | transloco }}
    } @else {
      {{ 'checkout.salida.whatsapp_antes' | transloco }}
      <a
        class="anillo-foco font-medio text-ts-primario underline"
        [href]="'https://wa.me/' + ('pie.whatsapp_numero' | transloco)"
        >{{ 'checkout.salida.whatsapp_enlace' | transloco }}</a
      >{{ 'checkout.salida.whatsapp_despues' | transloco }}
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsSalidaSinEnvio {
  readonly retiroDisponible = input.required<boolean>();
}
