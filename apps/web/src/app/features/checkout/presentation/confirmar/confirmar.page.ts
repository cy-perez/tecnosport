import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { iconoEnvio, iconoUbicacion } from '../../../../shared/ui/icono/iconos';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../../shared/ts-precio/ts-precio';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { CheckoutStore } from '../../application/checkout.store';
import { usarCotizacionEnvio } from '../../application/cotizacion-envio.consulta';
import { CotizarEnvioComando } from '../../domain/envio.model';
import { CrearPedidoComando } from '../../domain/pedido.comandos';
import { MetodoPago, Pedido } from '../../domain/pedido.model';
import { esMetodoPagoSistecredito, esMetodoPagoWompi } from '../../domain/reglas-pedido';
import { urlWebCheckoutWompi } from '../../domain/wompi';

const CLAVE_ETIQUETA: Record<MetodoPago, string> = {
  TARJETA: 'checkout.metodoPago.tarjeta',
  PSE: 'checkout.metodoPago.pse',
  NEQUI: 'checkout.metodoPago.nequi',
  BANCOLOMBIA: 'checkout.metodoPago.bancolombia',
  ADDI: 'checkout.metodoPago.addi',
  SISTECREDITO: 'checkout.metodoPago.sistecredito',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual',
  CONTRAENTREGA: 'checkout.metodoPago.contraentrega',
};

/**
 * Tercer y último paso antes de que exista el pedido (Fase 3, paso 4c de
 * `docs/09-plan-de-arranque.md`). Sin dirección o sin método de pago
 * guardados, no hay nada que confirmar — vuelve al paso que falta.
 *
 * Después de crear el pedido:
 * - Wompi (`esMetodoPagoWompi`): pide el intento de pago y **redirige el
 *   navegador** al Web Checkout hospedado — el cliente sale del sitio unos
 *   segundos, a propósito (`docs/09-plan-de-arranque.md`).
 * - Transferencia manual: el pedido ya trae `datosTransferencia`, no hace
 *   falta pedir nada más — a la pantalla de transferencia (paso 4e).
 * - Contraentrega: no se cobra nada, el pedido ya quedó confirmado — a la
 *   pantalla de estado (paso 4f).
 */
@Component({
  selector: 'app-confirmar',
  imports: [RouterLink, TranslocoPipe, TsBoton, TsEsqueleto, TsIcono, TsPrecio],
  templateUrl: './confirmar.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmarPage {
  protected readonly iconoEnvio = iconoEnvio;
  protected readonly iconoUbicacion = iconoUbicacion;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly error = signal<string | null>(null);

  /**
   * La misma cotización que ya calculó el resumen. Comparte clave de consulta con aquella —ciudad
   * y bultos— así que TanStack la sirve de su caché y no se gasta una llamada más al proveedor.
   *
   * <p>Hace falta aquí y no basta con haberla mostrado en el resumen: el artículo 50 de la Ley
   * 1480 de 2011 pide el desglose **antes de finalizar la transacción**, y finalizar es este
   * botón, no el de hace dos pantallas.
   */
  protected readonly cotizacion = usarCotizacionEnvio(() => this.criteriosCotizacion());

  protected readonly criteriosCotizacion = computed<CotizarEnvioComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    if (!datos || datos.tipoEntrega !== 'ENVIO_A_DOMICILIO' || !datos.direccion) {
      return null;
    }
    const lineas = (this.carrito.consulta.data()?.lineas ?? []).map((linea) => ({
      varianteId: linea.varianteId,
      cantidad: linea.cantidad,
    }));
    return lineas.length === 0 ? null : { lineas, direccion: datos.direccion };
  });

  /** La tarifa, cuando la hubo: las otras dos respuestas no son tarifas y no se disfrazan de una. */
  protected readonly tarifa = computed(() => {
    const resultado = this.cotizacion.data();
    return resultado?.tipo === 'TARIFA' ? resultado.cotizacion : null;
  });

  protected readonly costoEnvio = computed(() => this.tarifa()?.costoEnvio ?? 0);

  protected readonly total = computed(() => this.subtotal() + this.costoEnvio());

  /** Retiro en punto: no hay flete que mostrar, y el total es el subtotal. */
  protected readonly muestraEnvio = computed(() => this.criteriosCotizacion() !== null);

  /**
   * Los dos motivos por los que puede no haber tarifa, separados igual que en el
   * resumen. Esta pantalla los mezclaba en un solo `@else` y le decía "no
   * tenemos transporte hasta esta dirección" a quien en realidad se había topado
   * con una caída nuestra — mandándolo a corregir una dirección que estaba bien.
   */
  protected readonly sinCobertura = computed(
    () =>
      this.muestraEnvio() &&
      this.cotizacion.isSuccess() &&
      this.cotizacion.data()?.tipo === 'SIN_COBERTURA',
  );

  /**
   * El tercer motivo, y el único que no se arregla haciendo nada: algo del carrito vale más de lo
   * que la transportadora asegura (`ADR-0036`). La salida es la recogida en el punto, y para eso
   * hay que volver — por eso se nombra el artículo, o el comprador no sabe cuál quitar.
   */
  protected readonly articulosNoAsegurables = computed(() => {
    const resultado = this.cotizacion.data();
    return resultado?.tipo === 'ARTICULO_NO_ASEGURABLE' ? resultado.articulos : [];
  });

  /**
   * El cuarto motivo, y el único temporal: el artículo todavía no se ha medido (`ADR-0046`). La
   * salida que se le ofrece a quien compra es la misma —recoger o quitarlo— pero el texto va
   * aparte, porque el de los no asegurables explica un porqué que aquí sería falso.
   */
  protected readonly articulosSinMedidas = computed(() => {
    const resultado = this.cotizacion.data();
    return resultado?.tipo === 'ARTICULO_SIN_MEDIDAS' ? resultado.articulos : [];
  });

  protected readonly nombresSinMedidas = computed(() =>
    this.articulosSinMedidas()
      .map((articulo) => articulo.nombre)
      .join(', '),
  );

  protected readonly nombresNoAsegurables = computed(() =>
    this.articulosNoAsegurables()
      .map((articulo) => articulo.nombre)
      .join(', '),
  );

  protected readonly errorCotizacion = computed(
    () => this.muestraEnvio() && this.cotizacion.isError(),
  );

  /**
   * A domicilio y sin tarifa, el subtotal no es el total. Mostrarlo como "Total a
   * pagar" en la pantalla donde se finaliza la transacción es justo lo que el
   * artículo 50 de la Ley 1480 de 2011 no permite — y esta pantalla lo hacía,
   * porque `costoEnvio` cae a cero cuando no hay cotización.
   *
   * Se mira la **tarifa** y no el dato de la consulta: desde `ADR-0036` hay dos
   * respuestas que llegan en `success` y no traen tarifa, y las dos dejarían
   * pasar el total falso si aquí se preguntara solo si hay datos.
   */
  protected readonly totalConocido = computed(() => !this.muestraEnvio() || this.tarifa() != null);

  /**
   * Sin tarifa no hay pedido: el servidor responde 409 y hace bien. El resumen ya
   * bloquea dos pantallas antes, pero se bloquea también aquí porque a esta se
   * puede llegar sin pasar por aquella —con la URL, o volviendo atrás después de
   * cambiar algo— y porque la tarifa puede haber caducado en el camino.
   *
   * <p>Exige la tarifa en vez de reconocer su ausencia, por el mismo motivo que en
   * el resumen: mirar los estados terminales de la consulta deja una rendija entre
   * que los criterios quedan listos y que TanStack arranca la petición.
   */
  protected readonly bloqueadoPorCobertura = computed(() => !this.totalConocido());

  /**
   * La cuarta razón para quedarse sin domicilio: la plataforma rechazó los datos del envío. Aquí
   * importa más que en el resumen, porque el texto de esta pantalla es el que dice qué hacer — y
   * "vuelve a intentarlo en unos minutos" sería falso: el reintento trae el mismo rechazo.
   */
  protected readonly cotizacionRechazada = computed(
    () =>
      this.muestraEnvio() &&
      this.cotizacion.isSuccess() &&
      this.cotizacion.data()?.tipo === 'COTIZACION_RECHAZADA',
  );

  /**
   * Los cuatro motivos por los que no se puede mandar el pedido, cada uno con su texto. El del
   * artículo no asegurable además lo nombra: sin el nombre, "quita lo que no se puede enviar" es
   * una adivinanza, y el comprador está mirando una lista de productos.
   */
  private claveYParametrosDelBloqueo(): [string, Record<string, unknown>?] {
    if (this.sinCobertura()) {
      return ['checkout.confirmar.sin_cobertura'];
    }
    const noAsegurables = this.articulosNoAsegurables();
    if (noAsegurables.length > 0) {
      return [
        noAsegurables.length === 1
          ? 'checkout.confirmar.articulo_no_asegurable'
          : 'checkout.confirmar.articulos_no_asegurables',
        { articulos: this.nombresNoAsegurables() },
      ];
    }
    if (this.cotizacionRechazada()) {
      return ['checkout.confirmar.envio_rechazado'];
    }
    return ['checkout.confirmar.envio_no_calculado'];
  }

  /** Si la consulta ya llegó a un desenlace; no `isFetching`, por la rendija de arriba. */
  protected readonly cotizacionResuelta = computed(
    () => !this.muestraEnvio() || this.cotizacion.isSuccess() || this.cotizacion.isError(),
  );

  private readonly cotizacionResuelta$ = toObservable(this.cotizacionResuelta);

  /** Solo mientras `confirmar()` espera la cotización, para que el botón lo diga. */
  protected readonly esperandoCotizacion = signal(false);

  protected readonly subtotal = computed(() => {
    const datosCarrito = this.carrito.consulta.data();
    if (!datosCarrito) {
      return 0;
    }
    return datosCarrito.lineas.reduce((suma, linea) => {
      const snapshot = this.carrito.snapshotDeLinea(linea.varianteId);
      return suma + (snapshot ? snapshot.precioValor * linea.cantidad : 0);
    }, 0);
  });

  protected readonly etiquetaMetodoPago = computed(() => {
    const metodo = this.checkout.metodoPago();
    return metodo ? this.traducir()(CLAVE_ETIQUETA[metodo]) : '';
  });

  protected readonly enviando = computed(
    () => this.checkout.creando() || this.checkout.iniciandoPago() || this.esperandoCotizacion(),
  );

  constructor() {
    effect(() => {
      const datosCarrito = this.carrito.consulta.data();
      const sinDatosEntrega = this.checkout.datosEntrega() === null;
      const sinMetodoPago = this.checkout.metodoPago() === null;
      const carritoVacio = !!datosCarrito && datosCarrito.lineas.length === 0;

      if (carritoVacio || sinDatosEntrega) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      } else if (sinMetodoPago) {
        void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
      }
    });
  }

  protected async confirmar(): Promise<void> {
    const datos = this.checkout.datosEntrega();
    const metodoPago = this.checkout.metodoPago();
    const datosCarrito = this.carrito.consulta.data();
    if (!datos || !metodoPago || !datosCarrito || datosCarrito.lineas.length === 0) {
      return;
    }
    this.error.set(null);

    // Igual que en el resumen: primero esperar la cotización si va en vuelo. Decidir con
    // `isSuccess()` mientras la consulta todavía no ha vuelto es mirar un `false` que solo
    // significa "aún no sé".
    if (!this.cotizacionResuelta()) {
      this.esperandoCotizacion.set(true);
      try {
        await firstValueFrom(this.cotizacionResuelta$.pipe(filter((resuelta) => resuelta)));
      } finally {
        this.esperandoCotizacion.set(false);
      }
    }

    // Sin tarifa no se manda el pedido. Antes se mandaba, el servidor respondía 409 —con razón— y
    // aquí se traducía a "revisa tus datos e intenta de nuevo": un mensaje que culpa al comprador
    // de algo que no es suyo y que reintentar no arregla. El texto ahora dice qué pasó y qué
    // puede hacer, que es volver y elegir la recogida en el punto.
    if (this.bloqueadoPorCobertura()) {
      const [clave, parametros] = this.claveYParametrosDelBloqueo();
      this.error.set(this.transloco.translate(clave, parametros));
      return;
    }

    const comando: CrearPedidoComando = {
      correo: datos.correo,
      contacto: datos.contacto,
      lineas: datosCarrito.lineas.map((linea) => ({
        varianteId: linea.varianteId,
        cantidad: linea.cantidad,
      })),
      tipoEntrega: datos.tipoEntrega,
      direccion: datos.direccion,
      metodoPago,
      autorizaDatos: datos.autorizaDatos,
    };

    try {
      // Un reintento tras un fallo a mitad de camino (p. ej. creando el
      // intento de pago) no vuelve a crear el pedido: ya existe, `POST
      // /pedidos` no es el paso a repetir. `CheckoutStore.guardarDatosEntrega`
      // y `elegirMetodoPago` limpian `pedido` cada vez que cambia algo que lo
      // volvería inválido, así que si sigue ahí es el mismo intento.
      const pedido = this.checkout.pedido() ?? (await this.checkout.crearPedido(comando));
      await this.continuarSegunMetodoPago(pedido);
      // Aquí y no antes. Las líneas ya son del pedido, así que dejar el carrito lleno invita a
      // comprarlas dos veces; pero limpiarlo apenas se crea el pedido rompería el reintento de
      // arriba, porque la guarda del principio de este método exige un carrito con líneas. Si
      // `continuarSegunMetodoPago` falla —el intento de pago de Wompi, por ejemplo— no se llega
      // hasta acá y el carrito queda intacto para volver a intentarlo.
      this.carrito.limpiar();
    } catch {
      this.error.set(this.transloco.translate('checkout.confirmar.error'));
    }
  }

  private async continuarSegunMetodoPago(pedido: Pedido): Promise<void> {
    if (esMetodoPagoSistecredito(pedido.metodoPago)) {
      const documento = this.checkout.documentoComprador();
      if (!documento) {
        // Se perdió en un refresh (vive solo en memoria, a propósito). Volver a pedirlo es lo
        // único honesto: sin documento la pasarela no puede encontrar al cliente, y el pedido ya
        // existe, así que reintentar desde ahí no lo duplica.
        void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
        return;
      }
      const intento = await this.checkout.crearIntentoSistecredito(
        pedido.id,
        documento,
        this.transloco.activeLang(),
      );
      // La URL la arma la pasarela y es de un solo uso: no se compone nada aquí, solo se va. Y a
      // dónde vuelve el comprador NO se decide en esta llamada —va en `urlResponse`, que el
      // backend fija desde su configuración—, a diferencia de Wompi.
      window.location.href = intento.urlRedireccion;
      return;
    }

    if (esMetodoPagoWompi(pedido.metodoPago)) {
      const intento = await this.checkout.crearIntentoPago(pedido.id);
      const idioma = this.transloco.activeLang();
      // referencia, pedidoId y correo van en la propia URL de retorno (no en
      // el estado de la app): Wompi solo le agrega `?id=...` a lo que se le
      // pasó, y la SPA se recarga entera al volver de un dominio externo —
      // `CheckoutStore` no sobrevive ese viaje. `id` lo agrega Wompi mismo;
      // `correo` hace falta para `GET /pedidos/{id}/seguimiento` en la
      // pantalla de estado (docs/03-api.md: "con token del correo").
      const parametrosRetorno = new URLSearchParams({
        referencia: intento.referencia,
        pedidoId: pedido.id,
        correo: pedido.correo,
      });
      const urlRetorno = `${window.location.origin}/${idioma}/checkout/retorno-wompi?${parametrosRetorno.toString()}`;
      window.location.href = urlWebCheckoutWompi(intento, urlRetorno);
      return;
    }

    if (pedido.metodoPago === 'TRANSFERENCIA_MANUAL') {
      // Con parámetros, igual que el retorno de Wompi: un refresh en la
      // pantalla de transferencia (el comprador copiando el número de
      // cuenta con calma) no debería perder los datos de la cuenta.
      void this.router.navigate(['../transferencia'], {
        relativeTo: this.route,
        queryParams: { pedidoId: pedido.id, correo: pedido.correo },
      });
      return;
    }

    void this.router.navigate(['../estado'], { relativeTo: this.route });
  }
}
