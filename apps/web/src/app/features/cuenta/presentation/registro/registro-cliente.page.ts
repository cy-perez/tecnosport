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
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../shared/ts-campo/ts-campo';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';

function clavesCoincidenValidador(control: AbstractControl): ValidationErrors | null {
  const clave = control.get('clave')?.value;
  const confirmarClave = control.get('confirmarClave')?.value;
  return clave === confirmarClave ? null : { clavesNoCoinciden: true };
}

@Component({
  selector: 'app-registro-cliente',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './registro-cliente.page.html',
  styleUrl: './registro-cliente.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroClientePage {
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  private readonly transloco = inject(TranslocoService);

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly registrado = signal(false);

  protected readonly form = new FormGroup(
    {
      correo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
      clave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      confirmarClave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: clavesCoincidenValidador },
  );

  // valueChanges, no statusChanges: el formulario puede quedarse INVALID de punta a punta mientras
  // clave/confirmarClave dejan de coincidir y vuelven a coincidir — status nunca transiciona, así
  // que statusChanges no emitiría de nuevo y el error de confirmación quedaría pegado al valor
  // inicial. valueChanges sí emite en cada tecla, y los validadores ya corrieron para cuando emite.
  private readonly valorFormulario = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
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
      await this.repositorio.registrar(this.form.controls.correo.value, this.form.controls.clave.value);
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
