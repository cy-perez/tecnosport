import { NgOptimizedImage } from '@angular/common';
import { iconoEnvio, iconoUbicacion } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { usarFoco } from '../../../../shared/foco/foco';
import { esLimiteDeIntentos } from '../../../../core/errores/mensaje-de-error';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { CheckoutStore } from '../../application/checkout.store';
import {
  CriteriosSeguimiento,
  usarSeguimientoPedido,
} from '../../application/seguimiento-pedido.consulta';
import { EnvioPublico, EstadoPedido, Pedido, RetractoPublico } from '../../domain/pedido.model';
import {
  esMetodoPagoSistecredito,
  esMetodoPagoWompi,
  puedeReintentarPago,
} from '../../domain/reglas-pedido';
import { urlWebCheckoutWompi } from '../../domain/wompi';
import { fechaLarga } from '../../../../core/i18n/fecha-colombia';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoPedido, string> = {
  PAGO_PENDIENTE: 'checkout.estado.estados.pago_pendiente',
  PAGADO: 'checkout.estado.estados.pagado',
  PAGO_FALLIDO: 'checkout.estado.estados.pago_fallido',
  CONFIRMADO_CONTRAENTREGA: 'checkout.estado.estados.confirmado_contraentrega',
  EN_PREPARACION: 'checkout.estado.estados.en_preparacion',
  DESPACHADO: 'checkout.estado.estados.despachado',
  ENTREGADO: 'checkout.estado.estados.entregado',
  RECHAZADO_EN_ENTREGA: 'checkout.estado.estados.rechazado_en_entrega',
  DEVUELTO: 'checkout.estado.estados.devuelto',
  RECAUDO_PENDIENTE: 'checkout.estado.estados.recaudo_pendiente',
  RECAUDO_CONCILIADO: 'checkout.estado.estados.recaudo_conciliado',
};

/**
 * Último paso del checkout (Fase 3, paso 4f de `docs/09-plan-de-arranque.md`)
 * — adonde llegan contraentrega (directo desde `ConfirmarPage`, sin salir
 * del sitio) y el retorno de Wompi (`RetornoWompiPage`, con `pedidoId` y
 * `correo` en la URL porque la SPA se recargó entera).
 *
 * `CheckoutStore.pedido` manda si ya está poblado (no hizo falta salir del
 * sitio): se muestra directo, sin llamar al servidor. Si no, se consulta
 * `GET /pedidos/{id}/seguimiento` con lo que venga en la URL — construido
 * expresamente para el paso 4f, ver `docs/03-api.md`.
 */
@Component({
  selector: 'app-estado',
  imports: [
    NgOptimizedImage,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsIcono,
    TsPrecio,
  ],
  templateUrl: './estado.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoPage {
  protected readonly iconoEnvio = iconoEnvio;
  protected readonly iconoUbicacion = iconoUbicacion;

  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly traducir = usarTraductor();
  protected readonly checkout = inject(CheckoutStore);

  /**
   * La fecha, en el idioma de quien lee y en hora de Colombia. No con `DatePipe`: sin `LOCALE_ID`
   * registrado cae a `en-US` —un comprador en castellano leía «September 18, 2026»— y sin zona sale
   * distinta en el servidor y en el navegador. Ver `core/i18n/fecha-colombia.ts`.
   */
  protected fechaLarga(iso: string): string {
    return fechaLarga(iso, this.transloco.activeLang());
  }

  protected readonly error = signal<string | null>(null);

  /**
   * Lo que el formulario pidió, cuando se pidió. Vacío mientras nadie lo haya enviado: sin esto la
   * consulta arrancaría con el formulario en blanco y la pantalla diría "no encontramos tu pedido"
   * antes de que nadie escribiera nada.
   */
  private readonly consultado = signal<{ numeroPedido: string; correo: string } | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    numeroPedido: ['', [Validators.required]],
    correo: ['', [Validators.required, Validators.email]],
  });

  /**
   * De dónde sale el pedido, por orden: el store —lo acaba de crear esta misma visita—, los
   * parámetros del enlace del correo, y lo que se haya consultado en el formulario.
   *
   * <p>El formulario va el último a propósito: quien llega desde el enlace de su correo no tiene
   * que volver a escribir nada.
   */
  protected readonly criteriosSeguimiento = computed<CriteriosSeguimiento | null>(() => {
    if (this.checkout.pedido() !== null) {
      return null;
    }
    const parametros = this.route.snapshot.queryParamMap;
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');
    if (pedidoId && correo) {
      return { tipo: 'ID', pedidoId, correo };
    }
    const delFormulario = this.consultado();
    return delFormulario === null
      ? null
      : {
          tipo: 'NUMERO',
          numeroPedido: delFormulario.numeroPedido,
          correo: delFormulario.correo,
        };
  });

  /**
   * El formulario se queda puesto **mientras la consulta vuela** y también cuando lo consultado no
   * existe o falló.
   *
   * <p>Esto decía `criteriosSeguimiento() === null || noEncontrado()`, y con eso el formulario se
   * desmontaba justo al pulsar "Consultar": lo sustituía un esqueleto, el botón que la persona
   * acababa de pulsar dejaba de existir —el foco se iba a `<body>`—, el `[cargando]` del botón no
   * se llegaba a ver nunca, y con lector de pantalla no había ni un anuncio entre el envío y la
   * respuesta. Lo levantó la auditoría de accesibilidad.
   *
   * <p>Ahora el esqueleto solo cubre el camino del enlace del correo, donde no hay formulario que
   * conservar; en el del formulario, el estado lo cuenta el propio botón con su `aria-busy` y su
   * anillo.
   */
  protected readonly pidiendoDatos = computed(
    () => this.criteriosSeguimiento()?.tipo !== 'ID' && this.pedido() === null,
  );

  /**
   * `null` es "el servidor dijo que no existe", que es distinto de `undefined` —todavía no se ha
   * preguntado—. El adaptador traduce el 404 a `null` precisamente para poder distinguirlos aquí.
   */
  protected readonly noEncontrado = computed(
    () => this.consulta.isSuccess() && this.consulta.data() === null,
  );

  /**
   * Lo que se le dice a quien consultó y no obtuvo su pedido.
   *
   * <p>Tres casos distintos, y antes los tres acababan en el mismo sitio: la pantalla caía al
   * `@else` final y decía <b>"No encontramos este pedido"</b> también cuando la consulta había
   * fallado por red o por límite de intentos. Es la peor frase posible para alguien que acaba de
   * pagar, y el javadoc del adaptador prometía una traducción que no llegaba a ninguna parte — el
   * error de la consulta no estaba enganchado a nada. Lo levantó la revisión.
   */
  protected readonly mensajeDeConsulta = computed(() => {
    if (this.error() !== null) {
      return this.error();
    }
    if (this.noEncontrado()) {
      return this.traducir()('checkout.estado.consulta.no_encontrado');
    }
    const fallo = this.consulta.error();
    if (!fallo) {
      return null;
    }
    const traducir = this.traducir();
    return esLimiteDeIntentos(fallo)
      ? traducir('checkout.estado.consulta.demasiados_intentos')
      : traducir('checkout.estado.consulta.error_consulta');
  });

  /** El error de un campo, solo cuando ya se tocó: `ts-campo` lo pinta y pone `aria-invalid`. */
  protected readonly errorNumero = computed(() => {
    const control = this.form.controls.numeroPedido;
    this.revision();
    return control.touched && control.hasError('required')
      ? this.traducir()('checkout.estado.consulta.numero_requerido')
      : null;
  });

  protected readonly errorCorreo = computed(() => {
    const control = this.form.controls.correo;
    this.revision();
    const traducir = this.traducir();
    if (!control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return traducir('checkout.estado.consulta.correo_requerido');
    }
    return control.hasError('email') ? traducir('checkout.estado.consulta.correo_invalido') : null;
  });

  /**
   * Un contador que se mueve en cada envío, solo para que los dos `computed` de arriba se
   * recalculen: `FormControl.touched` no es una señal, así que sin esto `markAllAsTouched()` no
   * repinta nada. Es la mitad que faltaba de la lección de `apps/web/CLAUDE.md` —"`markAllAsTouched()`
   * sin un `[error]` enganchado no pinta nada; las dos mitades van juntas"— y aquí estaba puesta
   * solo la primera.
   */
  private readonly revision = signal(0);

  private readonly enfocar = usarFoco();

  protected consultarPorNumero(): void {
    this.form.markAllAsTouched();
    this.revision.update((n) => n + 1);
    if (this.form.invalid) {
      // **Sin mensaje general.** Cada campo ya dice lo suyo con su `[error]`, y añadir "escribe el
      // número y el correo" encima lo dice dos veces sin decir cuál de los dos falta. Lo que se
      // hace en su lugar es mover el foco al primer campo con problema: así un lector de pantalla
      // anuncia su etiqueta y su error, que es la información que el párrafo general no daba.
      // En una pestaña oculta `requestAnimationFrame` no corre, así que esto no se puede medir ahí
      // — se comprueba en el navegador, con la ventana delante.
      this.error.set(null);
      this.enfocar(() =>
        document.getElementById(
          this.form.controls.numeroPedido.invalid ? 'seguimiento-numero' : 'seguimiento-correo',
        ),
      );
      return;
    }
    this.error.set(null);
    const { numeroPedido, correo } = this.form.getRawValue();
    const criterios = { numeroPedido: numeroPedido.trim(), correo: correo.trim() };
    // Reenviar lo mismo tiene que volver a preguntar. Con `staleTime` y la misma `queryKey`, fijar
    // el mismo valor no dispara nada: el botón no gira, el mensaje no cambia, y quien no vio el
    // error cree que la página se colgó.
    const mismosDeAntes =
      this.consultado()?.numeroPedido === criterios.numeroPedido &&
      this.consultado()?.correo === criterios.correo;
    if (mismosDeAntes) {
      void this.consulta.refetch();
      return;
    }
    this.consultado.set(criterios);
  }

  /** Si a este pedido se llegó escribiendo el número, y no desde el enlace del correo. */
  protected readonly vinoDelFormulario = computed(
    () => this.criteriosSeguimiento()?.tipo === 'NUMERO',
  );

  /** Vuelve al formulario en blanco, para consultar otro pedido. */
  protected otraConsulta(): void {
    this.consultado.set(null);
    this.form.reset();
    this.error.set(null);
    this.revision.update((n) => n + 1);
  }

  protected readonly consulta = usarSeguimientoPedido(() => this.criteriosSeguimiento());

  protected readonly pedido = computed<Pedido | null>(
    () => this.checkout.pedido() ?? this.consulta.data() ?? null,
  );

  /**
   * Solo del seguimiento, nunca del store: el pedido que `CheckoutStore` guarda es el que acaba de
   * crearse en esta misma visita, y sobre uno recién creado no puede haber ningún retracto. Que
   * este bloque no aparezca ahí no es un olvido.
   */
  protected readonly retractos = computed<readonly RetractoPublico[]>(
    () => this.consulta.data()?.retractos ?? [],
  );

  /**
   * Por el mismo motivo que los retractos: un pedido recién creado en esta visita todavía no se
   * ha despachado, así que el envío solo puede venir del seguimiento. Es el dato que el correo de
   * despacho anuncia y al que su enlace trae.
   */
  protected readonly envio = computed<EnvioPublico | null>(
    () => this.consulta.data()?.envio ?? null,
  );

  protected etiquetaEstadoRetracto(estado: RetractoPublico['estado']): string {
    return this.traducir()('checkout.estado.retracto.estados.' + estado.toLowerCase());
  }

  protected readonly etiquetaEstado = computed(() => {
    const pedido = this.pedido();
    return pedido ? this.traducir()(CLAVE_ETIQUETA_ESTADO[pedido.estado]) : '';
  });

  /**
   * El botón de reintentar necesita el `id` del pedido, y el seguimiento por número <b>no lo
   * devuelve</b>: ese UUID es la credencial de `/pagos/intentos` y de `/reintentar-pago`, y
   * entregarlo por una puerta que se abre adivinando un número secuencial convertiría una fuga de
   * lectura en una de escritura (ver `MapeadorSeguimiento.aRespuestaSinIdInterno`).
   *
   * <p>`aSeguimiento` mapea el `id` ausente a cadena vacía, así que la comprobación es esa. Quien
   * llegó escribiendo el número y tiene un pago fallido reintenta desde el enlace de su correo, que
   * es donde ese botón siempre tuvo sentido.
   */
  protected readonly puedeReintentar = computed(() => {
    const pedido = this.pedido();
    return pedido !== null && pedido.id !== '' && puedeReintentarPago(pedido.estado);
  });

  protected readonly reintentando = signal(false);

  protected async reintentar(): Promise<void> {
    const pedido = this.pedido();
    if (!pedido) {
      return;
    }
    this.error.set(null);
    this.reintentando.set(true);
    try {
      const actualizado = await this.checkout.reintentarPago(pedido.id, pedido.correo);
      // Sistecrédito antes que Wompi, con su propia rama. Este `if` era el único del archivo
      // porque hasta ahora solo un pedido de Wompi podía llegar a PAGO_FALLIDO. Ya no —los
      // estados Rejected/Cancelled/Expired/Abandoned de Sistecrédito también llevan ahí— y sin
      // esta rama el botón dejaba el pedido de vuelta en PAGO_PENDIENTE sin intento vivo, sin
      // redirección y sin un solo mensaje. Es el mismo fork que `ConfirmarPage`: si uno cambia,
      // el otro también.
      if (esMetodoPagoSistecredito(actualizado.metodoPago)) {
        // El documento no sobrevive a la recarga de la SPA (vive solo en memoria, a propósito) y
        // a esta pantalla se llega justo después de volver de un dominio externo. Se vuelve a
        // pedir donde se pide siempre.
        void this.router.navigate(['/', this.transloco.activeLang(), 'checkout', 'metodo-pago']);
        return;
      }
      if (esMetodoPagoWompi(actualizado.metodoPago)) {
        const intento = await this.checkout.crearIntentoPago(actualizado.id);
        const idioma = this.transloco.activeLang();
        const parametrosRetorno = new URLSearchParams({
          referencia: intento.referencia,
          pedidoId: actualizado.id,
          correo: actualizado.correo,
        });
        const urlRetorno = `${window.location.origin}/${idioma}/checkout/retorno-wompi?${parametrosRetorno.toString()}`;
        window.location.href = urlWebCheckoutWompi(intento, urlRetorno);
      }
    } catch {
      this.error.set(this.transloco.translate('checkout.estado.error_reintento'));
    } finally {
      this.reintentando.set(false);
    }
  }
}
