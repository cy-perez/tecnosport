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
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    RouterLink,
  ],
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

  /**
   * Los mensajes de campo vacio, que es lo que sustituye al boton deshabilitado (`apps/web/CLAUDE.md`:
   * "No se deshabilita un boton para decir que faltan datos"). La confirmacion ya tenia el suyo
   * para el caso de que las dos claves no coincidan; lo que faltaba era decir que el campo esta
   * vacio, que es el caso normal de quien pulsa "Enviar" sin escribir nada.
   */
  private readonly tickClave = toSignal(this.form.controls.clave.events, {
    initialValue: null,
  });
  protected readonly errorClave = computed(() => {
    this.tickClave();
    const control = this.form.controls.clave;
    return control.touched && control.hasError('required')
      ? this.transloco.translate('cuenta.registro.errores.clave_requerida')
      : null;
  });

  private readonly tickConfirmarClave = toSignal(this.form.controls.confirmarClave.events, {
    initialValue: null,
  });
  protected readonly errorConfirmarClave = computed(() => {
    this.tickConfirmarClave();
    const control = this.form.controls.confirmarClave;
    return control.touched && control.hasError('required')
      ? this.transloco.translate('cuenta.registro.errores.confirmar_requerida')
      : null;
  });

  private readonly tickCorreo = toSignal(this.form.controls.correo.events, {
    initialValue: null,
  });
  protected readonly errorCorreo = computed(() => {
    this.tickCorreo();
    const control = this.form.controls.correo;
    return control.touched && control.hasError('required')
      ? this.transloco.translate('cuenta.registro.errores.correo_requerido')
      : null;
  });

  /**
   * La autorizacion de datos era un boton deshabilitado, y eso la dejaba sin explicacion: quien no
   * marcaba la casilla veia un boton que no hacia nada. Es ademas el mismo defecto que ya se
   * corrigio una vez en el checkout —"Sin marcar la casilla, «Continuar» no hacia nada y no decia
   * por que"—, repetido aqui.
   */
  private readonly tickAutorizacion = toSignal(this.form.controls.autorizaDatos.events, {
    initialValue: null,
  });
  protected readonly errorAutorizacion = computed(() => {
    this.tickAutorizacion();
    const control = this.form.controls.autorizaDatos;
    return control.touched && control.invalid
      ? this.transloco.translate('cuenta.registro.errores.autorizacion_requerida')
      : null;
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
