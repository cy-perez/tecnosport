import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { usarCotizacionEnvio } from '../../application/cotizacion-envio.consulta';
import {
  comandoMetodosDePago,
  usarPrecargarMetodosDePago,
} from '../../application/metodos-de-pago-disponibles.consulta';
import { CotizarEnvioComando, opcionDeTransportadora } from '../../domain/envio.model';
import { requiereDireccion } from '../../domain/reglas-pedido';
import { TsSelectorTransportadora } from '../selector-transportadora/ts-selector-transportadora';

/**
 * El paso entre el resumen y el método de pago, solo para envíos a domicilio (ADR-0073): con qué
 * transportadora va el pedido. Hasta el 8 de octubre de 2026 era un popover sobre el «Continuar» del
 * resumen; se pidió como página, igual que la de métodos de pago.
 *
 * <p>**Las opciones salen de la misma consulta que usó el resumen**, con los mismos criterios —ciudad
 * y líneas—, así que llegando desde allí la respuesta ya está en la caché y no hay otra llamada al
 * proveedor. Pasado el minuto de `staleTime`, se vuelve a cotizar: lo que se elige aquí es lo que el
 * servidor va a volver a comprobar al crear el pedido, y una lista vieja solo aplaza el 409.
 *
 * <p>Sin datos de entrega, con recogida en el punto o con el carrito vacío no hay nada que elegir, y
 * se vuelve al resumen —el mismo criterio que `MetodoPagoPage`—.
 */
@Component({
  selector: 'app-transportadora',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsSelectorTransportadora],
  templateUrl: './transportadora.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransportadoraPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);
  private readonly precargarMetodosDePago = usarPrecargarMetodosDePago();

  private readonly criterios = computed<CotizarEnvioComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    const lineas = (this.carrito.consulta.data()?.lineas ?? []).map((linea) => ({
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
    }));
    if (!datos?.direccion || lineas.length === 0) {
      return null;
    }
    return { lineas, direccion: datos.direccion };
  });

  protected readonly consulta = usarCotizacionEnvio(() => this.criterios());

  protected readonly opciones = computed(() => {
    const resultado = this.consulta.data();
    return resultado?.tipo === 'TARIFA' ? resultado.cotizacion.opciones : [];
  });

  protected readonly seleccionada = computed(
    () => this.checkout.datosEntrega()?.transportadora ?? null,
  );

  /** Se pulsó «Continuar» sin elegir: el botón no se deshabilita, así que lo dice. */
  protected readonly faltaTransportadora = signal(false);

  constructor() {
    effect(() => {
      const datos = this.checkout.datosEntrega();
      const datosCarrito = this.carrito.consulta.data();
      const carritoVacio = !!datosCarrito && datosCarrito.lineas.length === 0;
      if (!datos || !requiereDireccion(datos.tipoEntrega) || carritoVacio) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      }
    });
  }

  /**
   * Elegir ya pide los métodos de pago de la página siguiente: el servidor los decide cotizando con
   * recaudo, y así esos segundos corren mientras el comprador pulsa «Continuar» (ADR-0021, punto 5).
   * Quien cambia de opción deja una petición por cada una, y no se cancelan: cada una es la pregunta
   * correcta para su transportadora.
   */
  protected elegir(transportadora: string): void {
    this.checkout.elegirTransportadora(transportadora);
    this.faltaTransportadora.set(false);
    const datos = this.checkout.datosEntrega();
    const lineas = this.carrito.consulta.data()?.lineas ?? [];
    if (datos && lineas.length > 0) {
      this.precargarMetodosDePago(comandoMetodosDePago(datos, lineas));
    }
  }

  /**
   * La elegida tiene que estar entre las que cotizan **ahora**: si se volvió a cotizar y ya no
   * aparece, seguir con ella sería el 409 de la confirmación tres pantallas más tarde.
   */
  protected continuar(): void {
    const elegida = this.seleccionada();
    if (!elegida || !opcionDeTransportadora(this.opciones(), elegida)) {
      this.faltaTransportadora.set(true);
      return;
    }
    void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
  }
}
