import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  CorreoSinVerificarError,
  DemasiadosIntentosError,
} from '../../../../core/autenticacion/sesion.errores';
import { esFalloDelServidor } from '../../../../core/http/respuesta-http';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';

/**
 * Solo para `CLIENTE` — el login de `ADMIN` es `features/admin/`. Un correo/clave válidos pero de
 * un `ADMIN` cierran la sesión que acaban de abrir en vez de dejarla colgada, mismo criterio que
 * `IniciarSesionAdminPage` en el otro sentido.
 */
@Component({
  selector: 'app-iniciar-sesion-cliente',
  imports: [TsPaginaFormulario, ReactiveFormsModule, RouterLink, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './iniciar-sesion-cliente.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IniciarSesionClientePage {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly sesionStore = inject(SesionStore);

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected readonly form = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private readonly estadoFormulario = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });
  protected readonly formularioInvalido = computed(() => this.estadoFormulario() === 'INVALID');

  /**
   * El mensaje de cada campo, que es lo que sustituye al boton deshabilitado.
   *
   * `apps/web/CLAUDE.md`: "No se deshabilita un boton para decir que faltan datos. Un `<button
   * disabled>` sale del orden de tabulacion: quien navega con teclado no lo encuentra y nada le
   * explica por que no pasa nada". Estas cinco pantallas lo hacian igual, y ademas sin un solo
   * `[error]` enganchado, asi que el `markAllAsTouched()` que ya llamaba `enviar()` no pintaba
   * nada. Mismo patron que `resumen.page.ts`: el `tick` del control es lo que hace que el
   * `computed` se recalcule cuando cambia su estado.
   */
  private readonly tickCorreo = toSignal(this.form.controls.correo.events, {
    initialValue: null,
  });
  protected readonly errorCorreo = computed(() => {
    this.tickCorreo();
    const control = this.form.controls.correo;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('required')) {
      return this.transloco.translate('cuenta.iniciarSesion.errores.correo_requerido');
    }
    return this.transloco.translate('cuenta.iniciarSesion.errores.correo_invalido');
  });

  private readonly tickClave = toSignal(this.form.controls.clave.events, {
    initialValue: null,
  });
  protected readonly errorClave = computed(() => {
    this.tickClave();
    const control = this.form.controls.clave;
    if (!control.touched || control.valid) {
      return null;
    }
    return this.transloco.translate('cuenta.iniciarSesion.errores.clave_requerida');
  });

  protected async enviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      const sesion = await this.sesionStore.iniciarSesion(
        this.form.controls.correo.value,
        this.form.controls.clave.value,
      );
      if (sesion.rol !== 'CLIENTE') {
        await this.sesionStore.cerrarSesion();
        this.error.set(this.transloco.translate('cuenta.iniciarSesion.no_es_cliente'));
        return;
      }
      const idioma = this.transloco.activeLang();
      void this.router.navigate(['/' + idioma]);
    } catch (error) {
      if (error instanceof CorreoSinVerificarError) {
        this.error.set(this.transloco.translate('cuenta.iniciarSesion.error_sin_verificar'));
      } else if (error instanceof DemasiadosIntentosError) {
        // Mismo motivo que en el login del panel: sin esta rama, el límite de intentos se lee
        // como una clave equivocada.
        this.error.set(this.transloco.translate('cuenta.iniciarSesion.error_demasiados_intentos'));
      } else if (esFalloDelServidor(error)) {
        this.error.set(this.transloco.translate('comun.error_servidor'));
      } else {
        this.error.set(this.transloco.translate('cuenta.iniciarSesion.error'));
      }
    } finally {
      this.enviando.set(false);
    }
  }
}
