import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from '../../../../../core/autenticacion/sesion.errores';
import { SesionStore } from '../../../../../core/autenticacion/sesion.store';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { ErrorHttp } from '../../../../../core/http/respuesta-http';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { PORTAPAPELES } from '../../../../../core/portapapeles/portapapeles';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsCheckbox } from '../../../../../shared/ui/checkbox/ts-checkbox';
import { TsPaginaFormulario } from '../../../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import {
  usarConfirmarActivacion,
  usarIniciarActivacion,
} from '../../application/segundo-factor.consultas';
import { normalizarCodigo } from '../../domain/codigo-segundo-factor';
import { ActivacionIniciada, agruparDeACuatro } from '../../domain/segundo-factor.model';
import { CodigosDeRespaldo } from '../codigos/codigos-de-respaldo';
import { codigoDeLaAplicacion } from '../codigo.validador';

/** Los tres pasos: la clave actual, la clave TOTP con el primer código, y los códigos de respaldo. */
type Paso = 'clave' | 'codigo' | 'codigos';

/**
 * Activar el segundo factor del panel (ADR-0096). Hermana de `iniciar-sesion` y fuera del marco:
 * con la sesión restringida —un administrador sin factor mientras el servidor lo exige— el resto
 * del panel responde 403, así que aquí no hay pestañas a donde ir.
 *
 * Al confirmar, el servidor revoca todas las sesiones y abre una completa; la mutación la adopta
 * antes de volver (`segundo-factor.consultas.ts`). Los códigos de respaldo se muestran una sola
 * vez, y se pide marcar que se guardaron antes de entrar: después no hay forma de volver a verlos,
 * solo de regenerarlos.
 */
@Component({
  selector: 'app-activar-segundo-factor',
  imports: [
    TsPaginaFormulario,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsCheckbox,
    CodigosDeRespaldo,
  ],
  templateUrl: './activar-segundo-factor.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivarSegundoFactorPage {
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  private readonly portapapeles = inject(PORTAPAPELES);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly enfocar = usarFoco();
  private readonly sesionStore = inject(SesionStore);
  private readonly iniciar = usarIniciarActivacion();
  private readonly confirmar = usarConfirmarActivacion();

  /** Con la sesión restringida, la pantalla explica que no es opcional. */
  protected readonly obligatoria = computed(
    () => this.sesionStore.sesion()?.segundoFactorPorActivar === true,
  );

  protected readonly paso = signal<Paso>('clave');
  protected readonly activacion = signal<ActivacionIniciada | null>(null);
  protected readonly codigos = signal<readonly string[]>([]);
  protected readonly secretoAgrupado = computed(() => {
    const activacion = this.activacion();
    return activacion ? agruparDeACuatro(activacion.secreto) : '';
  });

  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly guardados = signal(false);
  protected readonly errorGuardados = signal<string | null>(null);

  protected readonly formClave = new FormGroup({
    claveActual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly formCodigo = new FormGroup({
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, codigoDeLaAplicacion],
    }),
  });

  private readonly tickClave = toSignal(this.formClave.controls.claveActual.events, {
    initialValue: null,
  });
  protected readonly errorClave = computed(() => {
    this.tickClave();
    const control = this.formClave.controls.claveActual;
    return control.touched && control.invalid
      ? this.traducir()('admin.segundoFactor.errores.clave_requerida')
      : null;
  });

  private readonly tickCodigo = toSignal(this.formCodigo.controls.codigo.events, {
    initialValue: null,
  });
  protected readonly errorCodigo = computed(() => {
    this.tickCodigo();
    const control = this.formCodigo.controls.codigo;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required')
      ? this.traducir()('admin.segundoFactor.errores.codigo_requerido')
      : this.traducir()('admin.segundoFactor.errores.codigo_seis_digitos');
  });

  protected async continuar(): Promise<void> {
    if (this.enviando()) {
      return;
    }
    if (this.formClave.invalid) {
      this.formClave.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);
    try {
      const activacion = await this.iniciar.mutateAsync(this.formClave.controls.claveActual.value);
      this.activacion.set(activacion);
      this.formClave.reset();
      this.formCodigo.reset();
      this.paso.set('codigo');
      this.enfocarEn('#activar-titulo-clave');
    } catch (error) {
      this.error.set(this.mensaje(error));
    } finally {
      this.enviando.set(false);
    }
  }

  protected async activar(): Promise<void> {
    if (this.enviando()) {
      return;
    }
    if (this.formCodigo.invalid) {
      this.formCodigo.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);
    try {
      const resultado = await this.confirmar.mutateAsync(
        normalizarCodigo(this.formCodigo.controls.codigo.value),
      );
      this.codigos.set(resultado.codigos);
      this.activacion.set(null);
      this.paso.set('codigos');
      this.enfocarEn('#codigos-de-respaldo-titulo');
    } catch (error) {
      if (error instanceof ErrorHttp && error.codigo === 'ACTIVACION_SEGUNDO_FACTOR_NO_INICIADA') {
        // La activación pendiente venció o se reemplazó: la clave que se ve ya no sirve y hay que
        // pedir otra, que empieza por la clave de la cuenta.
        this.activacion.set(null);
        this.paso.set('clave');
      }
      this.error.set(this.mensaje(error));
      this.formCodigo.controls.codigo.reset();
    } finally {
      this.enviando.set(false);
    }
  }

  protected copiar(texto: string): void {
    this.portapapeles.copiar(texto).then(
      () => this.aviso.set(this.traducir()('admin.segundoFactor.activar.copiado')),
      () => this.aviso.set(this.traducir()('admin.segundoFactor.activar.error_copiar')),
    );
  }

  protected marcarGuardados(marcado: boolean): void {
    this.guardados.set(marcado);
    if (marcado) {
      this.errorGuardados.set(null);
    }
  }

  /** El botón no se deshabilita (apps/web/CLAUDE.md): se pulsa, y si falta la casilla se dice. */
  protected irAlPanel(): void {
    if (!this.guardados()) {
      this.errorGuardados.set(this.traducir()('admin.segundoFactor.codigos.error_sin_guardar'));
      this.enfocarEn('#activar-codigos-guardados');
      return;
    }
    void this.router.navigateByUrl(`/${this.transloco.activeLang()}/admin/panel`);
  }

  /**
   * El paso anterior desaparece con el botón que se acaba de pulsar dentro: el foco va al titular
   * del paso nuevo para que el lector de pantalla empiece por ahí (apps/web/CLAUDE.md).
   */
  private enfocarEn(selector: string): void {
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>(selector));
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
