import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CorreoSinVerificarError } from '../../../../core/autenticacion/sesion.errores';
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
