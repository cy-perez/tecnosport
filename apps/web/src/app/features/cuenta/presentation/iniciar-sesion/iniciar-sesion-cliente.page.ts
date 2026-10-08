import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { correoValido } from '../../../../shared/formularios/validadores';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  CorreoSinVerificarError,
  CuentaExistenteRequiereClaveError,
  CuentaGoogleSinRegistroError,
  DemasiadosIntentosError,
} from '../../../../core/autenticacion/sesion.errores';
import { TsEntrarConGoogle } from '../entrar-con-google/ts-entrar-con-google';
import { esFalloDelServidor } from '../../../../core/http/respuesta-http';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { iconoClave, iconoCorreo } from '../../../../shared/ui/icono/iconos';
import { usarFocoEnPrimerInvalido } from '../../../../shared/foco/foco';

/**
 * Solo para `CLIENTE` — el login de `ADMIN` es `features/admin/`. Un correo/clave válidos pero de
 * un `ADMIN` cierran la sesión que acaban de abrir en vez de dejarla colgada, mismo criterio que
 * `IniciarSesionAdminPage` en el otro sentido.
 */
@Component({
  selector: 'app-iniciar-sesion-cliente',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEntrarConGoogle,
  ],
  templateUrl: './iniciar-sesion-cliente.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IniciarSesionClientePage {
  /** Al fallar el envío, el foco va al primer campo con error. */
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  protected readonly sesionStore = inject(SesionStore);

  /** Las anclas de los campos, igual que en crear cuenta: nunca el nombre accesible. */
  protected readonly iconoCorreo = iconoCorreo;
  protected readonly iconoClave = iconoClave;

  protected readonly error = signal<string | null>(null);
  /** Entró con Google sin cuenta: el aviso lleva un enlace a «Crear cuenta», por eso no es `error`. */
  protected readonly sinRegistroConGoogle = signal(false);
  protected readonly enviando = signal(false);

  protected readonly form = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, correoValido],
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
    this.sinRegistroConGoogle.set(false);
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

  /**
   * Entrar con Google (ADR-0074). Desde aquí no se autoriza nada: si la cuenta no existe, el servidor
   * responde que hay que pasar por «Crear cuenta», donde está la casilla de los datos.
   */
  protected async entrarConGoogle(credencial: string): Promise<void> {
    if (this.enviando()) {
      return;
    }
    this.error.set(null);
    this.sinRegistroConGoogle.set(false);
    this.enviando.set(true);
    try {
      await this.sesionStore.iniciarSesionConGoogle(credencial, false);
      void this.router.navigate(['/' + this.transloco.activeLang()]);
    } catch (error) {
      if (error instanceof CuentaGoogleSinRegistroError) {
        this.sinRegistroConGoogle.set(true);
      } else if (error instanceof CuentaExistenteRequiereClaveError) {
        this.error.set(this.transloco.translate('cuenta.google.requiere_clave'));
      } else if (error instanceof DemasiadosIntentosError) {
        // Su propio texto: el del login dice "tu clave no es el problema" a quien no usó clave.
        this.error.set(this.transloco.translate('cuenta.google.demasiados_intentos'));
      } else if (esFalloDelServidor(error)) {
        this.error.set(this.transloco.translate('comun.error_servidor'));
      } else {
        this.error.set(this.transloco.translate('cuenta.google.error'));
      }
    } finally {
      this.enviando.set(false);
    }
  }
}
