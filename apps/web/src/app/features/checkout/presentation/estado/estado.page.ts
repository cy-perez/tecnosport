import { NgOptimizedImage } from '@angular/common';
import { iconoEnvio, iconoUbicacion } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { correoValido } from '../../../../shared/formularios/validadores';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarIdiomaActivo, usarTraductor } from '../../../../core/i18n/traductor';
import { formatearPrecio } from '../../../../shared/ts-precio/formato-precio';
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
import { usarSolicitarEnlaceDeSeguimiento } from '../../application/solicitar-enlace-de-seguimiento.mutacion';
import { EnvioPublico, EstadoPedido, Pedido, RetractoPublico } from '../../domain/pedido.model';
import {
  esMetodoPagoSistecredito,
  esMetodoPagoWompi,
  puedeReintentarPago,
} from '../../domain/reglas-pedido';
import { urlWebCheckoutWompi } from '../../domain/wompi';
import { fechaLarga } from '../../../../core/i18n/fecha-colombia';
import { ALMACEN_CORREO_DE_PEDIDO } from '../../domain/almacen-correo-de-pedido.puerto';
import { correoDelPedido } from '../../application/correo-del-pedido';

const CLAVE_ETIQUETA_ESTADO: Record<EstadoPedido, string> = {
  PAGO_PENDIENTE: 'checkout.estado.estados.pago_pendiente',
  PAGADO: 'checkout.estado.estados.pagado',
  PAGO_FALLIDO: 'checkout.estado.estados.pago_fallido',
  CONFIRMADO_CONTRAENTREGA: 'checkout.estado.estados.confirmado_contraentrega',
  EN_PREPARACION: 'checkout.estado.estados.en_preparacion',
  DESPACHADO: 'checkout.estado.estados.despachado',
  LISTO_PARA_RECOGER: 'checkout.estado.estados.listo_para_recoger',
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

  private readonly correosDePedido = inject(ALMACEN_CORREO_DE_PEDIDO);

  /**
   * El formulario ya no consulta el pedido: pide que el enlace llegue al correo (ADR-0088). La
   * respuesta es la misma acierte o no, así que lo que se le dice a la persona también.
   */
  protected readonly solicitud = usarSolicitarEnlaceDeSeguimiento();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    numeroPedido: ['', [Validators.required]],
    correo: ['', [Validators.required, correoValido]],
  });

  /**
   * De dónde sale el pedido, por orden: el store —lo acaba de crear esta misma visita— y los
   * parámetros del enlace del correo. Sin ninguno de los dos, el formulario.
   */
  protected readonly criteriosSeguimiento = computed<CriteriosSeguimiento | null>(() => {
    if (this.checkout.pedido() !== null) {
      return null;
    }
    const pedidoId = this.route.snapshot.queryParamMap.get('pedidoId');
    // El correo ya no viene en la consulta: del fragmento del enlace de los correos, o de lo que
    // el navegador recordó antes de ir a la pasarela. Sin ninguno, cae al formulario.
    const correo = pedidoId
      ? correoDelPedido(pedidoId, this.route.snapshot.fragment, this.correosDePedido)
      : null;
    return pedidoId && correo ? { pedidoId, correo } : null;
  });

  /**
   * El formulario se queda puesto siempre que no haya pedido que mostrar: antes, durante y después
   * de pedir el enlace.
   *
   * <p>Llegó a desmontarse al pulsar el botón —lo sustituía un esqueleto, el botón que la persona
   * acababa de pulsar dejaba de existir y el foco se iba a `<body>`—, y lo levantó la auditoría de
   * accesibilidad. El esqueleto solo cubre el camino del enlace del correo, donde no hay formulario
   * que conservar; en el del formulario, el estado lo cuenta el botón con su `aria-busy` y su
   * anillo, y el resultado, las dos regiones vivas.
   */
  protected readonly pidiendoDatos = computed(
    () => this.criteriosSeguimiento() === null && this.pedido() === null,
  );

  /**
   * Lo que se le dice a quien pidió el enlace y no se le pudo mandar: el límite de intentos y la
   * red son dos cosas distintas, y "intenta de nuevo en un momento" a quien chocó con el límite lo
   * haría chocar otra vez. Lo que ya no existe es "no encontramos tu pedido": el servidor no lo
   * dice, a propósito (ADR-0088).
   */
  protected readonly mensajeDeConsulta = computed(() => {
    if (this.error() !== null) {
      return this.error();
    }
    const fallo = this.solicitud.error();
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
    return control.hasError('correoInvalido')
      ? traducir('checkout.estado.consulta.correo_invalido')
      : null;
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

  protected solicitarEnlace(): void {
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
    // Guarda de reentrada, porque el botón va con `[ocupado]` y no con `[cargando]`: no se
    // deshabilita, así que un segundo Enter mientras vuela la primera llegaría hasta aquí.
    if (this.solicitud.isPending()) {
      return;
    }
    const { numeroPedido, correo } = this.form.getRawValue();
    // Una mutación y no una consulta: reenviar lo mismo vuelve a pedir el enlace, que es lo que
    // espera quien no lo vio llegar. El tope de tres por hora lo pone el servidor.
    this.solicitud.mutate({ numeroPedido: numeroPedido.trim(), correo: correo.trim() });
  }

  protected readonly consulta = usarSeguimientoPedido(() => this.criteriosSeguimiento());

  protected readonly pedido = computed<Pedido | null>(
    () => this.checkout.pedido() ?? this.consulta.data() ?? null,
  );

  private readonly idioma = usarIdiomaActivo();

  /** El descuento del medio de pago (ADR-0095), formateado para ir dentro de la frase. */
  protected readonly descuento = computed(() => {
    const pedido = this.pedido();
    return pedido && pedido.descuento.valor > 0
      ? formatearPrecio(pedido.descuento.valor, pedido.descuento.moneda, this.idioma())
      : '';
  });

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
    if (!pedido) {
      return '';
    }
    // El pago en el punto comparte estado con la contraentrega (ADR-0094); el nombre del estado
    // no, porque al comprador le diría que paga a una transportadora.
    if (pedido.estado === 'CONFIRMADO_CONTRAENTREGA' && pedido.metodoPago === 'PAGO_EN_PUNTO') {
      return this.traducir()('checkout.estado.estados.confirmado_pago_en_punto');
    }
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[pedido.estado]);
  });

  /**
   * El botón de reintentar necesita el `id` del pedido, que es la credencial de `/pagos/intentos` y
   * de `/reintentar-pago`. Hubo un camino que entraba por el número y no lo traía; desde ADR-0088
   * el pedido solo se ve por el enlace del correo, que sí lo trae. La comprobación del `id` vacío
   * se queda porque `aSeguimiento` mapea un `id` ausente a cadena vacía, y sin él no hay a qué
   * reintentar.
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
