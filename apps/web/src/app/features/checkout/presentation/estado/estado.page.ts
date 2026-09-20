import { NgOptimizedImage } from '@angular/common';
import { iconoEnvio, iconoUbicacion } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
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
  imports: [NgOptimizedImage, TranslocoPipe, TsBoton, TsEsqueleto, TsIcono, TsPrecio],
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

  protected readonly criteriosSeguimiento = computed<CriteriosSeguimiento | null>(() => {
    if (this.checkout.pedido() !== null) {
      return null;
    }
    const parametros = this.route.snapshot.queryParamMap;
    const pedidoId = parametros.get('pedidoId');
    const correo = parametros.get('correo');
    return pedidoId && correo ? { pedidoId, correo } : null;
  });

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

  protected readonly puedeReintentar = computed(() => {
    const pedido = this.pedido();
    return pedido !== null && puedeReintentarPago(pedido.estado);
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
      const actualizado = await this.checkout.reintentarPago(pedido.id);
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
