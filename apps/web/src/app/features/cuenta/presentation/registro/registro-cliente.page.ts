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
import { mensajeDeError } from '../../../../core/errores/mensaje-de-error';
import { correoValido } from '../../../../shared/formularios/validadores';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../shared/ui/checkbox/ts-checkbox';
import { iconoClave, iconoCorreo } from '../../../../shared/ui/icono/iconos';
import { RouterLink } from '@angular/router';
import { DemasiadosIntentosError } from '../../../../core/autenticacion/sesion.errores';
import { CorreoYaRegistradoError } from '../../domain/cuenta.errores';
import { REPOSITORIO_CUENTA } from '../../domain/repositorio-cuenta.puerto';
import { usarFocoEnPrimerInvalido } from '../../../../shared/foco/foco';

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
  /** Al fallar el envío, el foco va al primer campo con error. */
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  private readonly repositorio = inject(REPOSITORIO_CUENTA);
  private readonly transloco = inject(TranslocoService);

  /** Mismo patrón que el pie: las rutas viven bajo /:lang. */
  protected readonly idioma = this.transloco.activeLang;

  /** Las anclas de los campos. No son el nombre accesible —`ts-icono` pinta `aria-hidden`—: son
   * lo unico que queda en pantalla diciendo que se escribe cuando el placeholder desaparece. */
  protected readonly iconoCorreo = iconoCorreo;
  protected readonly iconoClave = iconoClave;

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly registrado = signal(false);

  protected readonly form = new FormGroup(
    {
      // `correoValido` y no `Validators.email`: aquel acepta `juan@correo` —sin punto— y el
      // servidor no (`CorreoElectronico.java`), así que esa franja de correos pasaba el formulario
      // y volvía como un 422 genérico dos segundos después.
      correo: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, correoValido],
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

  /**
   * Vacío e inválido son dos cosas y hacen falta dos mensajes. Esto solo miraba `required`, así que
   * un correo con forma inválida dejaba el formulario en `INVALID` —`enviar()` marcaba todo como
   * tocado y volvía— **sin pintar nada**: se pulsaba "Crear cuenta" y no pasaba absolutamente nada,
   * ni mensaje, ni avance. La clave del texto ya existía en los JSON desde el principio y no la
   * usaba nadie.
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
    return control.hasError('required')
      ? this.transloco.translate('cuenta.registro.errores.correo_requerido')
      : this.transloco.translate('cuenta.registro.errores.correo_invalido');
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
    // Guarda de reentrada: el botón va con `[ocupado]` y ya no se deshabilita, porque
    // deshabilitado bajo el dedo mandaba el foco a `<body>` y, si el envío fallaba, quien
    // navega con teclado volvía al principio del documento sin oír el error.
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.enfocarPrimerInvalido();
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
      } else if (error instanceof DemasiadosIntentosError) {
        // El registro lleva techo por IP y por correo (`ConfiguracionLimiteIntentos`), y sin esta
        // rama el 429 se leía igual que una caída: "no se pudo crear la cuenta, intenta de nuevo",
        // que manda a reintentar justo lo único que garantiza otro 429. Las otras cuatro pantallas
        // de cuenta ya lo distinguían; esta no.
        this.error.set(this.transloco.translate('cuenta.registro.error_demasiados_intentos'));
      } else {
        // Por `mensajeDeError` y no por el genérico a secas: el backend manda un `codigo` en el
        // `ProblemDetail` y aquí se tiraba a la basura. Un 422 por el formato del correo o por la
        // autorización que no llegó se leía igual que una caída del servidor —"No se pudo crear la
        // cuenta. Intenta de nuevo."—, que no dice qué corregir y manda a repetir lo que va a
        // fallar igual. Un código sin traducir sigue cayendo al genérico, que es lo correcto.
        this.error.set(mensajeDeError(error, this.transloco, 'cuenta.registro.error_generico'));
      }
    } finally {
      this.enviando.set(false);
    }
  }
}
