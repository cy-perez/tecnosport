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
import { correoValido } from '../../../shared/formularios/validadores';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../core/i18n/traductor';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsPaginaFormulario } from '../../../shared/ui/pagina-formulario/ts-pagina-formulario';
import { TsCampo } from '../../../shared/ui/campo/ts-campo';
import { SesionStore } from '../../../core/autenticacion/sesion.store';
import { esFalloDelServidor } from '../../../core/http/respuesta-http';
import {
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  DesafioSegundoFactorVencidoError,
} from '../../../core/autenticacion/sesion.errores';
import { Sesion } from '../../../core/autenticacion/sesion.model';
import { usarFoco } from '../../../shared/foco/foco';
import { normalizarCodigo } from '../segundo-factor/domain/codigo-segundo-factor';
import { codigoDeLaAplicacionODeRespaldo } from '../segundo-factor/presentation/codigo.validador';

/** Los dos pasos del inicio de sesión: la clave, y el código cuando la cuenta tiene segundo factor. */
type Paso = 'clave' | 'codigo';

/**
 * Solo para `ADMIN` — el login de `CLIENTE` es `features/cuenta/`, todavía
 * sin construir. Un correo/clave válidos pero de un `CLIENTE` cierran la
 * sesión que acaban de abrir en vez de dejarla colgada: esta pantalla no
 * es el lugar para esa cuenta.
 */
@Component({
  selector: 'app-iniciar-sesion-admin',
  imports: [TsPaginaFormulario, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo],
  templateUrl: './iniciar-sesion-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IniciarSesionAdminPage {
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly sesionStore = inject(SesionStore);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly enfocar = usarFoco();

  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  /**
   * En qué paso está la pantalla. El desafío del segundo paso vive solo aquí, en memoria: no va a
   * la URL ni al almacenamiento, y recargar la página vuelve a la clave, que es lo que debe pasar
   * con un trámite de cinco minutos.
   */
  protected readonly paso = signal<Paso>('clave');
  private desafio: string | null = null;

  /**
   * Si quien entra escribe el código de la aplicación o uno de respaldo. El campo acepta los dos en
   * cualquier modo; lo que cambia es la etiqueta, la ayuda y el teclado del teléfono: seis dígitos
   * piden el numérico, y un código de respaldo lleva letras.
   */
  protected readonly conRespaldo = signal(false);

  protected readonly formCodigo = new FormGroup({
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, codigoDeLaAplicacionODeRespaldo],
    }),
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
    if (control.hasError('required')) {
      return this.traducir()('admin.iniciarSesion.segundoFactor.errores.codigo_requerido');
    }
    return this.traducir()('admin.iniciarSesion.segundoFactor.errores.codigo_formato');
  });

  /**
   * El panel cerró la sesión en la pestaña pero el servidor no lo confirmó (`PanelAdminPage`): la
   * cookie de refresco sigue viva. Se dice aquí porque es adonde el panel navega.
   */
  protected readonly cierreSinConfirmar =
    this.ruta.snapshot.queryParamMap.get('cierre') === 'sin-confirmar';

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
      return this.traducir()('admin.iniciarSesion.errores.correo_requerido');
    }
    return this.traducir()('admin.iniciarSesion.errores.correo_invalido');
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
    return this.traducir()('admin.iniciarSesion.errores.clave_requerida');
  });

  /**
   * A dónde ir después de iniciar sesión: lo que puso `adminGuard` al mandar
   * aquí, o el panel si se llegó por la puerta principal.
   *
   * Solo se acepta una ruta **relativa de este sitio**, dentro de `/admin`. Un
   * `destino` es un parámetro de la URL, o sea entrada del usuario: sin este
   * filtro, un enlace preparado con `?destino=https://otro-sitio` convertiría
   * este formulario en un salto a un sitio ajeno, con la credencial recién
   * escrita y la confianza de venir del dominio correcto.
   */
  private destinoTrasIniciar(idioma: string): string {
    const panel = `/${idioma}/admin/panel`;
    const destino = this.ruta.snapshot.queryParamMap.get('destino');
    if (destino === null) {
      return panel;
    }
    const esRutaDeEsteSitio = destino.startsWith('/') && !destino.startsWith('//');
    return esRutaDeEsteSitio && destino.includes('/admin/') ? destino : panel;
  }

  /**
   * El límite de intentos tiene su propio mensaje. Antes caía en "correo o clave incorrectos"
   * —`esFalloDelServidor` solo es cierto para un 5xx, así que cualquier 4xx compartía texto— y
   * eso le dice a quien escribió bien su clave que la escribió mal: se va a recuperarla, y sigue
   * sin entrar, porque la clave nunca fue el problema.
   */
  private claveDelError(error: unknown): string {
    if (error instanceof DemasiadosIntentosError) {
      return 'admin.iniciarSesion.error_demasiados_intentos';
    }
    return esFalloDelServidor(error) ? 'comun.error_servidor' : 'admin.iniciarSesion.error';
  }

  protected async enviar(): Promise<void> {
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      const resultado = await this.sesionStore.iniciarSesion(
        this.form.controls.correo.value,
        this.form.controls.clave.value,
      );
      if (resultado.tipo === 'segundo-factor') {
        this.pedirCodigo(resultado.desafio);
        return;
      }
      await this.entrar(resultado.sesion);
    } catch (error) {
      this.error.set(this.transloco.translate(this.claveDelError(error)));
    } finally {
      this.enviando.set(false);
    }
  }

  /** El segundo paso: el código de la aplicación o uno de respaldo contra el desafío (ADR-0096). */
  protected async verificar(): Promise<void> {
    if (this.enviando()) {
      return;
    }
    if (this.formCodigo.invalid) {
      this.formCodigo.markAllAsTouched();
      return;
    }
    const desafio = this.desafio;
    if (desafio === null) {
      this.volverAClave();
      return;
    }
    this.error.set(null);
    this.enviando.set(true);

    try {
      const sesion = await this.sesionStore.verificarSegundoFactor(
        desafio,
        normalizarCodigo(this.formCodigo.controls.codigo.value),
      );
      await this.entrar(sesion);
    } catch (error) {
      if (error instanceof DesafioSegundoFactorVencidoError) {
        // Ningún código va a servir contra este desafío: vencido, usado o con los intentos
        // agotados. Se vuelve a la clave con el motivo dicho, en vez de dejar a alguien tecleando
        // códigos buenos que el servidor va a seguir rechazando.
        this.volverAClave();
        this.error.set(
          this.transloco.translate('admin.iniciarSesion.segundoFactor.error_desafio_vencido'),
        );
        return;
      }
      this.error.set(this.transloco.translate(this.claveDelErrorDeCodigo(error)));
      this.formCodigo.controls.codigo.reset();
      this.enfocarCodigo();
    } finally {
      this.enviando.set(false);
    }
  }

  protected alternarRespaldo(): void {
    this.conRespaldo.update((valor) => !valor);
    this.formCodigo.reset();
    this.error.set(null);
    this.enfocarCodigo();
  }

  /** "Volver": otra cuenta, o la clave mal escrita. El desafío se suelta. */
  protected volverAClave(): void {
    this.desafio = null;
    this.paso.set('clave');
    this.conRespaldo.set(false);
    this.formCodigo.reset();
    this.form.controls.clave.reset();
    this.error.set(null);
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('#admin-clave'));
  }

  private pedirCodigo(desafio: string): void {
    this.desafio = desafio;
    this.conRespaldo.set(false);
    this.formCodigo.reset();
    this.paso.set('codigo');
    this.enfocarCodigo();
  }

  /**
   * El foco al campo del código al cambiar de paso: el formulario de la clave desaparece con el
   * botón que se acaba de pulsar dentro, y sin esto el foco cae a `<body>` (apps/web/CLAUDE.md).
   */
  private enfocarCodigo(): void {
    this.enfocar(() => this.host.nativeElement.querySelector<HTMLElement>('#admin-codigo'));
  }

  private async entrar(sesion: Sesion): Promise<void> {
    if (sesion.rol !== 'ADMIN') {
      // Si el cierre falla, el mensaje sigue siendo este: decir "correo o clave incorrectos" a
      // quien acaba de entrar bien sería falso. La sesión ya se soltó en la pestaña.
      await this.sesionStore.cerrarSesion().catch(() => undefined);
      this.error.set(this.transloco.translate('admin.iniciarSesion.no_es_admin'));
      return;
    }
    const idioma = this.transloco.activeLang();
    void this.router.navigateByUrl(this.destinoTrasIniciar(idioma));
  }

  /**
   * El código mal escrito se corrige escribiendo otro; el límite de intentos, esperando. Un fallo
   * del servidor no es culpa del código.
   */
  private claveDelErrorDeCodigo(error: unknown): string {
    if (error instanceof DemasiadosIntentosError) {
      return 'admin.iniciarSesion.segundoFactor.error_demasiados_intentos';
    }
    if (error instanceof CodigoSegundoFactorInvalidoError || !esFalloDelServidor(error)) {
      return 'admin.iniciarSesion.segundoFactor.error_codigo';
    }
    return 'comun.error_servidor';
  }
}
