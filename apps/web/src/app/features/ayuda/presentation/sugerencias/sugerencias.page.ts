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
import { correoValido } from '../../../../shared/formularios/validadores';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DemasiadosIntentosError } from '../../../../core/autenticacion/sesion.errores';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsAreaTexto } from '../../../../shared/ui/area-texto/ts-area-texto';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../shared/ui/checkbox/ts-checkbox';
import { iconoCorreo } from '../../../../shared/ui/icono/iconos';
import {
  MAXIMO_CARACTERES_SUGERENCIA,
  REPOSITORIO_SUGERENCIAS,
} from '../../domain/repositorio-sugerencias.puerto';
import { usarFocoEnPrimerInvalido } from '../../../../shared/foco/foco';

/**
 * La autorización de datos hace falta <b>solo si se dejó el correo</b>.
 *
 * <p>Es un validador de grupo y no uno de campo porque depende de dos controles: un
 * `requiredTrue` suelto en la casilla exigiría el sí para mandar una sugerencia anónima, o sea
 * pedir permiso para tratar un dato que nadie dio, que es justo lo que el buzón evita.
 */
function autorizacionSoloConCorreo(control: AbstractControl): ValidationErrors | null {
  const correo = String(control.get('correo')?.value ?? '').trim();
  const autoriza = control.get('autorizaDatos')?.value === true;
  return correo === '' || autoriza ? null : { autorizacionRequerida: true };
}

/**
 * El buzón de sugerencias: lo que la gente nos quiere decir y que no cabe en ningún otro formulario
 * del sitio.
 *
 * <h2>Lo que esta pantalla dice en voz alta</h2>
 *
 * <p>Que <b>esto no es una PQR</b>. Una garantía, un retracto, un reclamo o un derecho de datos
 * personales tienen un plazo legal corriendo desde que llegan, y llegan por el canal que los
 * términos anuncian — el correo y el WhatsApp de la página de contacto, que es donde se radican con
 * número y con reloj. Un buzón que se traga un reclamo es peor que no tener buzón, así que el aviso
 * va arriba del formulario y con su enlace, no en letra pequeña debajo.
 *
 * <h2>El correo es opcional, y eso cambia el formulario</h2>
 *
 * <p>Sin correo no hay dato personal que tratar, así que no se pide la autorización de la Ley 1581
 * ni se guarda constancia de nada. En cuanto alguien escribe su correo, la casilla pasa a ser
 * obligatoria — lo decide {@link autorizacionSoloConCorreo}, un validador de grupo, porque depende
 * de los dos controles a la vez.
 *
 * <p>La casilla <b>nunca viene premarcada</b>: sin una acción del titular no hay autorización
 * válida. Y el botón no se deshabilita para decir que falta algo (`apps/web/CLAUDE.md`): se valida
 * al pulsar, con `markAllAsTouched()` y un `[error]` enganchado en cada control — las dos mitades
 * van juntas o no va ninguna.
 */
@Component({
  selector: 'app-sugerencias',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoDirective,
    TranslocoPipe,
    TsAreaTexto,
    TsBoton,
    TsCampo,
    TsCheckbox,
  ],
  templateUrl: './sugerencias.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SugerenciasPage {
  /** Al fallar el envío, el foco va al primer campo con error. */
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  private readonly repositorio = inject(REPOSITORIO_SUGERENCIAS);
  private readonly transloco = inject(TranslocoService);

  /**
   * Para leer una clave dentro de un `computed`. `transloco.translate()` a secas no lee ninguna
   * señal, así que el `computed` se evalúa una vez y no se recalcula: si el scope `ayuda` todavía
   * no cargó, la clave cruda se queda puesta para siempre. Aquí el `resolve` de la ruta lo precarga
   * —así que en la práctica llega a tiempo— y aun así se usa el traductor: la precarga garantiza
   * que no se vea mal ni un instante, el traductor garantiza que se corrija si alguna vez no llega.
   * El botón flotante de WhatsApp, que no tiene ruta que lo precargue, se publicó con este mismo
   * defecto y salía enlazando a `wa.me/pie.whatsapp_numero`.
   */
  private readonly traducir = usarTraductor();

  protected readonly idioma = this.transloco.activeLang;
  protected readonly iconoCorreo = iconoCorreo;
  protected readonly maximoCaracteres = MAXIMO_CARACTERES_SUGERENCIA;

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  /** Si la que acaba de irse llevaba correo: el acuse dice cosas distintas. */
  protected readonly enviadoConCorreo = signal(false);

  protected readonly form = new FormGroup(
    {
      mensaje: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(MAXIMO_CARACTERES_SUGERENCIA)],
      }),
      // Sin `required`: el correo es opcional a propósito. El formato sí se exige, para no mandar
      // al servidor algo que va a rechazar — y con la misma expresión que usa él
      // (`CorreoElectronico.java`), que es lo que `Validators.email` no hacía.
      correo: new FormControl('', { nonNullable: true, validators: [correoValido] }),
      autorizaDatos: new FormControl(false, { nonNullable: true }),
    },
    { validators: autorizacionSoloConCorreo },
  );

  /**
   * `valueChanges` y no `statusChanges`, por lo mismo que en el registro: el formulario puede
   * quedarse INVALID de punta a punta mientras el correo se escribe y la casilla sigue sin marcar,
   * y en ese camino `status` nunca transiciona — así que `statusChanges` no volvería a emitir y el
   * mensaje de la autorización se quedaría pegado al valor inicial.
   */
  private readonly valorFormulario = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  private readonly tickMensaje = toSignal(this.form.controls.mensaje.events, {
    initialValue: null,
  });
  protected readonly errorMensaje = computed(() => {
    this.tickMensaje();
    const control = this.form.controls.mensaje;
    if (!control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return this.traducir()('ayuda.sugerencias.errores.mensaje_requerido');
    }
    return null;
  });

  private readonly tickCorreo = toSignal(this.form.controls.correo.events, {
    initialValue: null,
  });
  protected readonly errorCorreo = computed(() => {
    this.tickCorreo();
    const control = this.form.controls.correo;
    return control.touched && control.hasError('correoInvalido')
      ? this.traducir()('ayuda.sugerencias.errores.correo_invalido')
      : null;
  });

  /**
   * Las **dos** señales, y hacen falta las dos. El error depende de que la casilla esté tocada y de
   * que el grupo siga inválido: `valueChanges` cubre lo segundo, pero `markAllAsTouched()` —que es
   * lo que corre al pulsar "Enviar" con el formulario a medias— **no cambia ningún valor**, así que
   * no emite y el `computed` no se recalcularía. El mensaje no aparecía y el botón parecía no hacer
   * nada, que es justo el defecto que este proyecto ya corrigió dos veces. Los eventos del control
   * sí traen el cambio de "tocado".
   */
  private readonly tickAutorizacion = toSignal(this.form.controls.autorizaDatos.events, {
    initialValue: null,
  });
  protected readonly errorAutorizacion = computed(() => {
    this.tickAutorizacion();
    this.valorFormulario();
    const casilla = this.form.controls.autorizaDatos;
    return casilla.touched && this.form.hasError('autorizacionRequerida')
      ? this.traducir()('ayuda.sugerencias.errores.autorizacion_requerida')
      : null;
  });

  /** Solo se pide cuando hay correo escrito, y la pantalla lo enseña solo entonces. */
  protected readonly pideAutorizacion = computed(() => {
    this.valorFormulario();
    return this.form.controls.correo.value.trim() !== '';
  });

  /**
   * El texto del contador. `shared/ui/` no traduce nada, así que el componente recibe la función ya
   * hecha; y sale del traductor reactivo para que se rehaga cuando el scope cargue y cuando alguien
   * cambie de idioma.
   */
  protected readonly textoContador = computed(() => {
    const traducir = this.traducir();
    return (restantes: number) => traducir('ayuda.sugerencias.restantes', { restantes });
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

    const correo = this.form.controls.correo.value.trim();
    try {
      await this.repositorio.enviar({
        mensaje: this.form.controls.mensaje.value,
        correo: correo === '' ? null : correo,
        autorizaDatos: this.form.controls.autorizaDatos.value,
      });
      this.enviadoConCorreo.set(correo !== '');
      this.enviado.set(true);
    } catch (error) {
      this.error.set(
        this.transloco.translate(
          error instanceof DemasiadosIntentosError
            ? 'ayuda.sugerencias.error_limite'
            : 'ayuda.sugerencias.error_generico',
        ),
      );
    } finally {
      this.enviando.set(false);
    }
  }

  /** Vuelve al formulario en blanco, para quien tiene dos cosas que decir. */
  protected escribirOtra(): void {
    this.form.reset();
    this.enviado.set(false);
    this.error.set(null);
  }
}
