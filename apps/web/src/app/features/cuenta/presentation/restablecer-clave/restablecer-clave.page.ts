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
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../shared/ts-campo/ts-campo';

function clavesCoincidenValidador(control: AbstractControl): ValidationErrors | null {
  const clave = control.get('claveNueva')?.value;
  const confirmarClave = control.get('confirmarClave')?.value;
  return clave === confirmarClave ? null : { clavesNoCoinciden: true };
}

/**
 * A diferencia de `VerificarCorreoPage`, aquí no hay ninguna llamada automática al cargar: el
 * usuario escribe la clave nueva y la envía con un gesto, así que no hace falta `afterNextRender`
 * ni ninguna guardia de SSR — el token solo se usa dentro de `enviar()`.
 */
@Component({
  selector: 'app-restablecer-clave',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './restablecer-clave.page.html',
  styleUrl: './restablecer-clave.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestablecerClavePage {
  private readonly route = inject(ActivatedRoute);
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  protected readonly transloco = inject(TranslocoService);

  protected readonly token = this.route.snapshot.queryParamMap.get('token');

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly restablecida = signal(false);

  protected readonly form = new FormGroup(
    {
      claveNueva: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      confirmarClave: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: clavesCoincidenValidador },
  );

  // valueChanges, no statusChanges: mismo motivo que RegistroClientePage.
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
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      await this.repositorio.restablecerClave(this.token, this.form.controls.claveNueva.value);
      this.restablecida.set(true);
    } catch {
      this.error.set(this.transloco.translate('cuenta.restablecerClave.error_token_invalido'));
    } finally {
      this.enviando.set(false);
    }
  }
}
