import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import {
  ClaveActualIncorrectaError,
  DemasiadosIntentosError,
} from '../../../core/autenticacion/sesion.errores';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../shared/ui/campo/ts-campo';
import { TsPaginaFormulario } from '../../../shared/ui/pagina-formulario/ts-pagina-formulario';

function clavesCoincidenValidador(control: AbstractControl): ValidationErrors | null {
  const clave = control.get('claveNueva')?.value;
  const confirmarClave = control.get('confirmarClave')?.value;
  return clave === confirmarClave ? null : { clavesNoCoinciden: true };
}

/**
 * Cambiar la clave del panel sin pasar por el correo. Antes de esta pantalla el único camino era
 * `/auth/recuperacion`, que depende de que el buzón reciba: con una sola cuenta de administrador,
 * perder la clave se resolvía borrando filas en la base de datos.
 *
 * El servidor revoca todas las sesiones y devuelve una nueva, así que al terminar se sigue dentro
 * — no hay que volver a entrar, y cualquier otro dispositivo queda fuera.
 */
@Component({
  selector: 'app-cambiar-clave-admin',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './cambiar-clave-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CambiarClaveAdminPage {
  private readonly sesionStore = inject(SesionStore);
  protected readonly transloco = inject(TranslocoService);

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly cambiada = signal(false);

  protected readonly form = new FormGroup(
    {
      claveActual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      claveNueva: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      confirmarClave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: clavesCoincidenValidador },
  );

  // valueChanges y no statusChanges, igual que las demás pantallas con formulario: el estado
  // cambia antes de que el valor se haya propagado y el mensaje se queda un ciclo atrás.
  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly clavesNoCoinciden = computed(() => {
    this.valorFormulario();
    return !!this.form.errors?.['clavesNoCoinciden'];
  });

  protected async enviar(): Promise<void> {
    // El botón se queda vivo aunque falten datos (apps/web/CLAUDE.md): deshabilitarlo lo saca del
    // orden de tabulación y nadie se entera de por qué no pasa nada. Se valida al pulsar.
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(this.transloco.translate('admin.clave.error_formulario'));
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      await this.sesionStore.cambiarClave(
        this.form.controls.claveActual.value,
        this.form.controls.claveNueva.value,
      );
      this.cambiada.set(true);
      this.form.reset();
    } catch (error) {
      this.error.set(this.transloco.translate(this.claveDelError(error)));
    } finally {
      this.enviando.set(false);
    }
  }

  private claveDelError(error: unknown): string {
    if (error instanceof ClaveActualIncorrectaError) {
      return 'admin.clave.error_clave_actual';
    }
    if (error instanceof DemasiadosIntentosError) {
      return 'admin.clave.error_demasiados_intentos';
    }
    return 'comun.error_servidor';
  }
}
