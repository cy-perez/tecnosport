import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from '../../../../../core/autenticacion/sesion.errores';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { fechaLarga } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import {
  usarDesactivar,
  usarEstadoSegundoFactor,
  usarRegenerarCodigos,
} from '../../application/segundo-factor.consultas';
import { normalizarCodigo } from '../../domain/codigo-segundo-factor';
import { CodigosDeRespaldo } from '../codigos/codigos-de-respaldo';
import { codigoDeLaAplicacionODeRespaldo } from '../codigo.validador';

/** La clave actual y un código: lo que piden regenerar y desactivar. */
function formularioDeConfirmacion() {
  return new FormGroup({
    claveActual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, codigoDeLaAplicacionODeRespaldo],
    }),
  });
}

type FormularioDeConfirmacion = ReturnType<typeof formularioDeConfirmacion>;

/**
 * La seguridad de la cuenta del panel (ADR-0096): si el segundo factor está activo, cuántos
 * códigos de respaldo quedan, regenerarlos y desactivar el factor. Las dos escrituras piden la
 * clave y un código —de la aplicación o de respaldo—, revocan las demás sesiones y devuelven una
 * nueva, que la mutación adopta.
 *
 * Desactivar es también el camino para cambiar de teléfono: se desactiva y se vuelve a activar. Con
 * el factor obligatorio el servidor lo rechaza, y la pantalla ni lo ofrece.
 */
@Component({
  selector: 'app-seguridad-admin',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    CodigosDeRespaldo,
  ],
  templateUrl: './seguridad-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeguridadAdminPage {
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly consulta = usarEstadoSegundoFactor();
  private readonly regenerar = usarRegenerarCodigos();
  private readonly desactivacion = usarDesactivar();

  protected readonly estado = computed(() => this.consulta.data() ?? null);
  protected readonly activadoEn = computed(() => {
    const iso = this.estado()?.activadoEn;
    // `traducir()` se lee para que la fecha cambie de idioma con el sitio.
    this.traducir();
    return iso ? fechaLarga(iso, this.transloco.activeLang()) : '';
  });

  /** Los códigos recién regenerados: se muestran una vez, hasta salir de la pantalla. */
  protected readonly codigosNuevos = signal<readonly string[]>([]);

  protected readonly regenerando = signal(false);
  protected readonly errorRegenerar = signal<string | null>(null);
  protected readonly formRegenerar = formularioDeConfirmacion();

  protected readonly desactivando = signal(false);
  protected readonly errorDesactivar = signal<string | null>(null);
  protected readonly desactivado = signal(false);
  protected readonly formDesactivar = formularioDeConfirmacion();

  private readonly tickRegenerar = toSignal(this.formRegenerar.events, { initialValue: null });
  protected readonly erroresRegenerar = computed(() => {
    this.tickRegenerar();
    return this.erroresDe(this.formRegenerar);
  });

  private readonly tickDesactivar = toSignal(this.formDesactivar.events, { initialValue: null });
  protected readonly erroresDesactivar = computed(() => {
    this.tickDesactivar();
    return this.erroresDe(this.formDesactivar);
  });

  protected idioma(): string {
    return this.transloco.activeLang();
  }

  protected async regenerarCodigos(): Promise<void> {
    if (this.regenerando()) {
      return;
    }
    if (this.formRegenerar.invalid) {
      this.formRegenerar.markAllAsTouched();
      return;
    }
    this.errorRegenerar.set(null);
    this.regenerando.set(true);
    try {
      const resultado = await this.regenerar.mutateAsync(this.valoresDe(this.formRegenerar));
      this.codigosNuevos.set(resultado.codigos);
      this.formRegenerar.reset();
    } catch (error) {
      this.errorRegenerar.set(this.mensaje(error));
      this.formRegenerar.controls.codigo.reset();
    } finally {
      this.regenerando.set(false);
    }
  }

  protected async desactivar(): Promise<void> {
    if (this.desactivando()) {
      return;
    }
    if (this.formDesactivar.invalid) {
      this.formDesactivar.markAllAsTouched();
      return;
    }
    this.errorDesactivar.set(null);
    this.desactivando.set(true);
    try {
      await this.desactivacion.mutateAsync(this.valoresDe(this.formDesactivar));
      this.codigosNuevos.set([]);
      this.desactivado.set(true);
      this.formDesactivar.reset();
    } catch (error) {
      this.errorDesactivar.set(this.mensaje(error));
      this.formDesactivar.controls.codigo.reset();
    } finally {
      this.desactivando.set(false);
    }
  }

  private valoresDe(form: FormularioDeConfirmacion): { claveActual: string; codigo: string } {
    return {
      claveActual: form.controls.claveActual.value,
      codigo: normalizarCodigo(form.controls.codigo.value),
    };
  }

  private erroresDe(form: FormularioDeConfirmacion): {
    clave: string | null;
    codigo: string | null;
  } {
    const clave = form.controls.claveActual;
    const codigo = form.controls.codigo;
    return {
      clave:
        clave.touched && clave.invalid
          ? this.traducir()('admin.segundoFactor.errores.clave_requerida')
          : null,
      codigo:
        !codigo.touched || codigo.valid
          ? null
          : codigo.hasError('required')
            ? this.traducir()('admin.segundoFactor.errores.codigo_requerido')
            : this.traducir()('admin.segundoFactor.errores.codigo_formato'),
    };
  }

  private mensaje(error: unknown): string {
    if (error instanceof ClaveActualIncorrectaError) {
      return this.transloco.translate('admin.segundoFactor.error_clave_actual');
    }
    if (error instanceof CodigoSegundoFactorInvalidoError) {
      return this.transloco.translate('admin.segundoFactor.error_codigo');
    }
    if (error instanceof DemasiadosIntentosError) {
      return this.transloco.translate('admin.segundoFactor.error_demasiados_intentos');
    }
    if (error instanceof SesionExpiradaError) {
      return this.transloco.translate('admin.segundoFactor.error_sesion_expirada');
    }
    return mensajeDeError(error, this.transloco, 'admin.segundoFactor.error');
  }
}
