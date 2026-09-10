import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { esFalloDelServidor } from '../../../../core/http/respuesta-http';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';

/**
 * `error` es "el enlace no sirve" y `fallo_servidor` es "no llegamos a preguntarlo". Se separan
 * porque el texto de `error` le dice a quien llega que pida un enlace nuevo, y decirle eso cuando
 * el servidor está caído lo manda a gastar el que ya tenía, que sí era válido.
 */
type EstadoVerificacion = 'cargando' | 'exito' | 'error' | 'fallo_servidor';

/**
 * El token es de un solo uso (docs/08-seguridad-legal.md) — si el servidor lo consumiera durante
 * el renderizado SSR y el navegador lo volviera a enviar al hidratar, la segunda llamada llegaría
 * con el token ya usado y le rompería la verificación a un usuario real. Por eso, a diferencia de
 * otras páginas de este proyecto que sí llaman una API en el constructor sin guardia, esta espera a
 * `afterNextRender` (mismo motivo que `SesionStore`): el servidor siempre sirve "cargando",
 * determinista, y solo el navegador, ya hidratado, dispara la verificación real.
 */
@Component({
  selector: 'app-verificar-correo',
  imports: [TranslocoPipe],
  templateUrl: './verificar-correo.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificarCorreoPage {
  private readonly route = inject(ActivatedRoute);
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  protected readonly estado = signal<EstadoVerificacion>('cargando');

  constructor() {
    if (!this.esNavegador) {
      return;
    }
    afterNextRender(() => {
      void this.verificar(this.route.snapshot.queryParamMap.get('token'));
    });
  }

  private async verificar(token: string | null): Promise<void> {
    if (!token) {
      this.estado.set('error');
      return;
    }
    try {
      await this.repositorio.verificarCorreo(token);
      this.estado.set('exito');
    } catch (error) {
      this.estado.set(esFalloDelServidor(error) ? 'fallo_servidor' : 'error');
    }
  }
}
