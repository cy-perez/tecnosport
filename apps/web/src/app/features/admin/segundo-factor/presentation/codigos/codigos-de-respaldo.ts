import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { PORTAPAPELES } from '../../../../../core/portapapeles/portapapeles';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { codigosComoTexto } from '../../domain/segundo-factor.model';

/**
 * Los diez códigos de respaldo, que el servidor entrega **en claro una sola vez** (ADR-0096): al
 * activar el factor y al regenerarlos. Se ofrecen para copiar y para guardar como archivo de
 * texto.
 *
 * La descarga es un `<a download>` con la lista en una URL `data:`, sin `Blob` ni
 * `URL.createObjectURL`: no toca ninguna API del navegador, así que no pide guardia de plataforma
 * y funciona igual en el primer pintado del servidor. Angular no sanea el esquema `data:` en un
 * `href` —solo `javascript:`—.
 */
@Component({
  selector: 'app-codigos-de-respaldo',
  imports: [TranslocoPipe, TsBoton],
  templateUrl: './codigos-de-respaldo.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodigosDeRespaldo {
  private readonly portapapeles = inject(PORTAPAPELES);
  private readonly traducir = usarTraductor();

  readonly codigos = input.required<readonly string[]>();

  /** Lo que dice la región viva al copiar. Vive siempre en el DOM; cambia su contenido. */
  protected readonly aviso = signal<string | null>(null);

  protected readonly enlaceDescarga = computed(
    () => 'data:text/plain;charset=utf-8,' + encodeURIComponent(codigosComoTexto(this.codigos())),
  );

  protected copiar(): void {
    this.portapapeles.copiar(codigosComoTexto(this.codigos())).then(
      () => this.aviso.set(this.traducir()('admin.segundoFactor.codigos.copiados')),
      // Sin portapapeles —sin HTTPS, o sin el foco en la página— quedan la descarga y la lista en
      // pantalla: se dice, en vez de callar.
      () => this.aviso.set(this.traducir()('admin.segundoFactor.codigos.error_copiar')),
    );
  }
}
