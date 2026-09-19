import { isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { esFalloDelServidor } from '../../../../core/http/respuesta-http';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';

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
 *
 * <p>**El reenvío vive aquí y no en una pantalla propia** porque aquí es donde alguien descubre que
 * su enlace no sirve. Decirle "pide uno nuevo" sin darle dónde pedirlo era mandarlo a buscar un
 * canal que no existe, que es exactamente la deuda que `RegistrarUsuario` llevaba anotada en su
 * `catch` y que `adr/0045` cierra.
 *
 * <p>**El resultado del reenvío es siempre el mismo**, exista la cuenta, no exista, o exista ya
 * verificada: los tres responden 204 a propósito. Esta pantalla no oculta nada por su cuenta —
 * refleja lo que el backend ya decidió, igual que `RecuperarClavePage`.
 */
@Component({
  selector: 'app-verificar-correo',
  imports: [TranslocoPipe, ReactiveFormsModule, TsBoton, TsCampo],
  templateUrl: './verificar-correo.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificarCorreoPage {
  private readonly route = inject(ActivatedRoute);
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  protected readonly transloco = inject(TranslocoService);

  protected readonly estado = signal<EstadoVerificacion>('cargando');

  protected readonly errorReenvio = signal<string | null>(null);
  protected readonly reenviando = signal(false);
  protected readonly reenviado = signal(false);

  protected readonly form = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  private readonly estadoFormulario = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });
  protected readonly formularioInvalido = computed(() => this.estadoFormulario() === 'INVALID');

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

  /**
   * El corte va aquí y no en un `[deshabilitado]` del botón: un `<button disabled>` sale del orden
   * de tabulación, así que quien navega con teclado ni siquiera llega a enfocarlo para enterarse de
   * por qué no pasa nada. Mismo criterio que el alta de variante del panel y el resumen del
   * checkout.
   */
  protected async reenviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorReenvio.set(null);
    this.reenviando.set(true);

    try {
      await this.repositorio.reenviarVerificacion(this.form.controls.correo.value);
      this.reenviado.set(true);
    } catch {
      this.errorReenvio.set(this.transloco.translate('cuenta.verificarCorreo.reenvio_error'));
    } finally {
      this.reenviando.set(false);
    }
  }
}
