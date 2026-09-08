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
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../shared/ui/checkbox/ts-checkbox';
import { RouterLink } from '@angular/router';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';

function clavesCoincidenValidador(control: AbstractControl): ValidationErrors | null {
  const clave = control.get('clave')?.value;
  const confirmarClave = control.get('confirmarClave')?.value;
  return clave === confirmarClave ? null : { clavesNoCoinciden: true };
}

@Component({
  selector: 'app-registro-cliente',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsCheckbox, RouterLink],
  templateUrl: './registro-cliente.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroClientePage {
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  private readonly transloco = inject(TranslocoService);

  /** Mismo patrón que el pie: las rutas viven bajo /:lang. */
  protected readonly idioma = this.transloco.activeLang;

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly registrado = signal(false);

  protected readonly form = new FormGroup(
    {
      correo: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.email],
      }),
      clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      confirmarClave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      // requiredTrue, y arranca en false: la casilla nunca puede venir premarcada — sin acción del
      // titular no hay autorización válida.
      autorizaDatos: new FormControl(false, {
        nonNullable: true,
        validators: [Validators.requiredTrue],
      }),
    },
    { validators: clavesCoincidenValidador },
  );

  // valueChanges, no statusChanges: el formulario puede quedarse INVALID de punta a punta mientras
  // clave/confirmarClave dejan de coincidir y vuelven a coincidir — status nunca transiciona, así
  // que statusChanges no emitiría de nuevo y el error de confirmación quedaría pegado al valor
  // inicial. valueChanges sí emite en cada tecla, y los validadores ya corrieron para cuando emite.
  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly formularioInvalido = computed(() => {
    this.valorFormulario();
    return this.form.invalid;
  });
  protected readonly clavesNoCoinciden = computed(() => {
    this.valorFormulario();
    return !!this.form.errors?.['clavesNoCoinciden'];
  });

  protected async enviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      await this.repositorio.registrar(
        this.form.controls.correo.value,
        this.form.controls.clave.value,
        this.form.controls.autorizaDatos.value,
      );
      this.registrado.set(true);
    } catch (error) {
      if (error instanceof CorreoYaRegistradoError) {
        this.error.set(this.transloco.translate('cuenta.registro.error_correo_registrado'));
      } else {
        this.error.set(this.transloco.translate('cuenta.registro.error_generico'));
      }
    } finally {
      this.enviando.set(false);
    }
  }
}
