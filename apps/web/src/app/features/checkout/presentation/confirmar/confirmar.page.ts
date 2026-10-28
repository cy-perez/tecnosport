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
import {
  usarCotizacionEnvio,
  usarOlvidarCotizacionEnvio,
} from '../../application/cotizacion-envio.consulta';
import {
  comandoMetodosDePago,
  usarMetodosDePagoDisponibles,
} from '../../application/metodos-de-pago-disponibles.consulta';
import { usarModalidadesDeEntrega } from '../../application/modalidades-entrega.consulta';
import { TsSalidaSinEnvio } from '../salida-sin-envio/ts-salida-sin-envio';
import { CotizarEnvioComando, OpcionEnvio, opcionDeTransportadora } from '../../domain/envio.model';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from '../../domain/pedido.comandos';
import { MetodoDePagoConPrecio, MetodoPago, Pedido } from '../../domain/pedido.model';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { esMetodoPagoSistecredito, esMetodoPagoWompi } from '../../domain/reglas-pedido';
import { fleteDelMedio } from '../../domain/flete-del-medio';
import { urlWebCheckoutWompi } from '../../domain/wompi';
import { ALMACEN_CORREO_DE_PEDIDO } from '../../domain/almacen-correo-de-pedido.puerto';
import { ALMACEN_CARRITO_POR_PAGAR } from '../../domain/almacen-carrito-por-pagar.puerto';
import { AvisoFueraDelPedido } from '../aviso-fuera-del-pedido/aviso-fuera-del-pedido';

const CLAVE_ETIQUETA: Record<MetodoPago, string> = {
  WOMPI: 'checkout.metodoPago.wompi',
  SISTECREDITO: 'checkout.metodoPago.sistecredito',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual',
  CONTRAENTREGA: 'checkout.metodoPago.contraentrega',
  PAGO_EN_PUNTO: 'checkout.metodoPago.pago_en_punto',
};

/**
 * Los estados de Sistecredito de los que un pedido ya no sale (guia `G-ALI-08`). Todos significan
 * lo mismo para quien compra: ese credito no se abrio y reintentar el mismo no lleva a ninguna
 * parte. La lista es la misma que `SistecreditoClient` trata como terminal.
 */
/** El motivo que clasifica el servidor, con su texto. Sin vocabulario de la pasarela aquí. */
const CLAVE_MOTIVO_SISTECREDITO: Record<string, string> = {
  SOLICITUD_EN_CURSO: 'checkout.confirmar.sistecredito_801',
  MONTO_INSUFICIENTE: 'checkout.confirmar.sistecredito_802',
  CREDITO_NEGADO: 'checkout.confirmar.sistecredito_rechazado',
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
  imports: [
    AvisoFueraDelPedido,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsEsqueleto,
    TsIcono,
    TsPrecio,
    TsSalidaSinEnvio,
  ],
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
  private readonly correosDePedido = inject(ALMACEN_CORREO_DE_PEDIDO);
  private readonly carritosPorPagar = inject(ALMACEN_CARRITO_POR_PAGAR);

  protected readonly error = signal<string | null>(null);

  /**
   * Un crédito rechazado no se arregla reintentando: la pantalla ofrece la única salida que hay,
   * que es pagar con otro medio. Sin esto, el texto genérico invitaba a volver a pulsar
   * "Confirmar pedido" y ese botón ya no puede funcionar — cuando la notificación de la pasarela
   * llega, el pedido queda en `PAGO_FALLIDO` y el backend exige `PAGO_PENDIENTE` para abrir otro
   * intento.
   */
  protected readonly rechazoDeCredito = signal(false);

  /**
   * La misma cotización que ya calculó el resumen. Comparte clave de consulta con aquella —ciudad
   * y bultos— así que TanStack la sirve de su caché y no se gasta una llamada más al proveedor.
   *
   * <p>Hace falta aquí y no basta con haberla mostrado en el resumen: el artículo 50 de la Ley
   * 1480 de 2011 pide el desglose **antes de finalizar la transacción**, y finalizar es este
   * botón, no el de hace dos pantallas.
   */
  protected readonly cotizacion = usarCotizacionEnvio(() => this.criteriosCotizacion());
  private readonly olvidarCotizacion = usarOlvidarCotizacionEnvio();

  protected readonly criteriosCotizacion = computed<CotizarEnvioComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    if (!datos || datos.tipoEntrega !== 'ENVIO_A_DOMICILIO' || !datos.direccion) {
      return null;
    }
    const lineas = this.carrito.lineasPorPagar().map((linea) => ({
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

  /**
   * El envío que se va a cobrar: el de la transportadora que el comprador eligió en el resumen
   * (ADR-0073), o el más económico si no eligió ninguna. `null` si la elegida ya no aparece en la
   * cotización —el pedido respondería 409— y entonces no hay total que mostrar.
   */
  protected readonly envioElegido = computed<OpcionEnvio | null>(() => {
    const cotizada = this.tarifa();
    if (!cotizada) {
      return null;
    }
    const elegida = this.checkout.datosEntrega()?.transportadora;
    if (!elegida) {
      return {
        transportadora: cotizada.transportadora,
        costoEnvio: cotizada.costoEnvio,
        moneda: cotizada.moneda,
        diasEstimados: cotizada.diasEstimados,
      };
    }
    return opcionDeTransportadora(cotizada.opciones, elegida) ?? null;
  });

  /** Hay cotización, pero la transportadora elegida ya no está en ella. */
  protected readonly transportadoraYaNoCotiza = computed(
    () => this.tarifa() !== null && this.envioElegido() === null,
  );

  /**
   * El flete que se cobra con el medio elegido: el que manda el servidor para ese medio —con
   * contraentrega, el que lleva el recaudo— y, mientras el contrato no lo traiga, el de la
   * cotización (`fleteDelMedio`). Sin tarifa sigue en cero, y `totalConocido` no deja pintar total.
   */
  protected readonly costoEnvio = computed(() =>
    this.muestraEnvio() && this.envioElegido() === null
      ? 0
      : fleteDelMedio(this.eleccionVigente(), this.envioElegido()?.costoEnvio ?? 0),
  );

  /**
   * Lo que se cobra: la mercancía **con el medio elegido**, del servidor, más el flete (ADR-0095).
   * El servidor lo vuelve a calcular al crear el pedido con la misma regla; esto es la vista previa
   * y sale de su respuesta, no de sumar precios aquí.
   */
  protected readonly total = computed(() => this.subtotalCobrado() + this.costoEnvio());

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

  private readonly modalidades = usarModalidadesDeEntrega(() =>
    this.carrito.lineasPorPagar().map((linea) => linea.varianteId),
  );

  /** Mismo criterio que en el resumen: mientras no se sabe, como si estuviera apagada. */
  protected readonly retiroDisponible = computed(
    () => this.modalidades.data()?.retiroEnPunto === true,
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
  protected readonly totalConocido = computed(
    () => this.eleccionVigente() !== null && this.envioConocido(),
  );

  /** El flete conocido, o que no hace falta: lo único que `totalConocido` miraba antes. */
  private readonly envioConocido = computed(
    () => !this.muestraEnvio() || this.envioElegido() != null,
  );

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
  protected readonly bloqueadoPorCobertura = computed(() => !this.envioConocido());

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
    if (this.transportadoraYaNoCotiza()) {
      return ['checkout.confirmar.transportadora_no_disponible'];
    }
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
    const sinMedidas = this.articulosSinMedidas();
    if (sinMedidas.length > 0) {
      return [
        sinMedidas.length === 1
          ? 'checkout.confirmar.articulo_sin_medidas'
          : 'checkout.confirmar.articulos_sin_medidas',
        { articulos: this.nombresSinMedidas() },
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

  /**
   * La misma consulta que la página de métodos de pago, con el carrito y la entrega **de ahora**
   * (ADR-0095). El precio del medio se tomaba de una copia guardada al elegirlo, que nada invalidaba
   * si después cambiaba el carrito, un precio o la tarifa, y el pedido se cobra con el carrito
   * vivo: la pantalla donde se finaliza la transacción enseñaba un total que no era el que se iba a
   * cobrar. `siempreFresco` porque la caché de la página anterior puede tener quince segundos.
   */
  private readonly criteriosMetodos = computed<MetodosDePagoDisponiblesComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    const lineas = this.carrito.lineasPorPagar();
    return datos && lineas.length > 0 ? comandoMetodosDePago(datos, lineas) : null;
  });

  protected readonly metodos = usarMetodosDePagoDisponibles(() => this.criteriosMetodos(), {
    siempreFresco: true,
  });

  /**
   * El medio elegido en la respuesta fresca, tomado por su nombre. `null` mientras la consulta no
   * ha vuelto —o vuelve a pedir— y también si ya no lo trae: en ninguno de los tres casos hay un
   * precio que enseñar ni un pedido que mandar.
   */
  protected readonly eleccionVigente = computed<MetodoDePagoConPrecio | null>(() => {
    if (!this.metodos.isSuccess() || this.metodos.isFetching()) {
      return null;
    }
    const metodo = this.checkout.metodoPago();
    return this.metodos.data()?.find((opcion) => opcion.metodo === metodo) ?? null;
  });

  /** Mientras se piden los precios no se confirma: el total todavía no es el que se cobraría. */
  protected readonly preciosCargando = computed(
    () =>
      this.criteriosMetodos() !== null && (this.metodos.isPending() || this.metodos.isFetching()),
  );

  protected readonly preciosConError = computed(
    () => !this.preciosCargando() && this.metodos.isError(),
  );

  /** Al publicado: es el precio anunciado, y el descuento va en su propia fila. */
  protected readonly subtotal = computed(() => {
    const eleccion = this.eleccionVigente();
    return eleccion ? eleccion.subtotal.valor + eleccion.descuento.valor : 0;
  });

  /** El descuento del medio elegido (ADR-0095); cero con Wompi y Sistecrédito. */
  protected readonly descuento = computed(() => this.eleccionVigente()?.descuento.valor ?? 0);

  /** La mercancía con el medio elegido: el subtotal que dio el servidor para ese medio. */
  private readonly subtotalCobrado = computed(() => this.eleccionVigente()?.subtotal.valor ?? 0);

  protected readonly etiquetaMetodoPago = computed(() => {
    const metodo = this.checkout.metodoPago();
    return metodo ? this.traducir()(CLAVE_ETIQUETA[metodo]) : '';
  });

  protected readonly enviando = computed(
    () => this.checkout.creando() || this.checkout.iniciandoPago() || this.esperandoCotizacion(),
  );

  /**
   * La compra quedó hecha y ya se navegó a su pantalla final. Desde ahí lo pagado sale del carrito
   * y el store suelta los datos de entrega y el medio (`CheckoutStore.terminarCompra`), y la guarda de
   * abajo los vería faltar y mandaría al resumen, compitiendo con la navegación que ya va en vuelo.
   */
  private readonly terminado = signal(false);

  constructor() {
    effect(() => {
      if (this.terminado()) {
        return;
      }
      const datosCarrito = this.carrito.consulta.data();
      const sinDatosEntrega = this.checkout.datosEntrega() === null;
      const sinMetodoPago = this.checkout.metodoPago() === null;
      // Vacío o sin nada marcado: el resumen sabe decir cada caso.
      const carritoVacio = !!datosCarrito && this.carrito.lineasPorPagar().length === 0;

      if (carritoVacio || sinDatosEntrega) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      } else if (sinMetodoPago) {
        void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
      }
    });

    // La consulta fresca volvió y ya no trae el medio elegido —la contraentrega dejó de cubrir, el
    // monto ya no alcanza para Sistecrédito—: se descarta, y el efecto de arriba devuelve a elegir
    // otro, donde la página dice por qué.
    effect(() => {
      if (
        this.checkout.metodoPago() !== null &&
        this.metodos.isSuccess() &&
        !this.metodos.isFetching() &&
        this.eleccionVigente() === null
      ) {
        this.checkout.descartarMetodoNoDisponible();
      }
    });
  }

  protected async confirmar(): Promise<void> {
    // Guarda de reentrada: con el botón en `ocupado` —que no se deshabilita, para no tirar el foco
    // a `<body>`— un segundo clic llegaría hasta aquí. La llave de idempotencia evita el pedido
    // doble en el servidor, pero no hace falta mandarlo.
    if (this.enviando()) {
      return;
    }
    const datos = this.checkout.datosEntrega();
    const metodoPago = this.checkout.metodoPago();
    if (!datos || !metodoPago || this.carrito.lineasPorPagar().length === 0) {
      return;
    }
    // Sin el precio fresco del medio no se confirma: mientras carga, el botón ya va ocupado, pero
    // un clic que llegue antes de pintarse, o con la consulta en error, no puede mandar un pedido
    // cuyo total nadie vio.
    if (this.eleccionVigente() === null) {
      if (this.preciosConError()) {
        this.error.set(this.transloco.translate('checkout.confirmar.precios_error'));
      }
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
    // puede hacer. La otra salida —recoger, o escribirnos por WhatsApp— no se repite aquí: ya la
    // dice la fila del costo de envío, con el enlace, y dos alertas seguidas con la misma frase
    // solo cansan a quien las escucha.
    if (this.bloqueadoPorCobertura()) {
      const [clave, parametros] = this.claveYParametrosDelBloqueo();
      this.error.set(this.transloco.translate(clave, parametros));
      return;
    }

    // Lo marcado se lee aquí, después de las esperas, y no al principio: durante ellas la
    // cotización del carrito puede volver con una línea que ya no se vende, y el pedido tiene que
    // ser el de lo que la pantalla muestra ahora, el mismo con que se cotizó el envío.
    const lineasPorPagar = this.carrito.lineasPorPagar();
    if (lineasPorPagar.length === 0) {
      return;
    }
    const comando: CrearPedidoComando = {
      correo: datos.correo,
      nombreComprador: datos.nombreComprador,
      contacto: datos.contacto,
      lineas: lineasPorPagar.map((linea) => ({
        varianteId: linea.varianteId,
        cantidad: linea.cantidad,
        enlaceCompra: linea.enlaceCompra,
      })),
      tipoEntrega: datos.tipoEntrega,
      direccion: datos.direccion,
      metodoPago,
      autorizaDatos: datos.autorizaDatos,
      transportadora: datos.transportadora,
    };

    try {
      // Un reintento tras un fallo a mitad de camino (p. ej. creando el intento de pago) no vuelve
      // a crear el pedido: ya existe, `POST /pedidos` no es el paso a repetir. **Pero solo si es el
      // mismo pedido**: el carrito no pasa por `CheckoutStore`, así que quien cambió una cantidad
      // después del fallo y volvió con «Atrás» trae otro comando, y reutilizar el pedido viejo le
      // cobraría el total viejo y después le borraría el carrito nuevo. La huella decide.
      const pedido =
        this.checkout.pedidoReutilizable(comando) ?? (await this.checkout.crearPedido(comando));
      const continuo = await this.continuarSegunMetodoPago(pedido);
      if (!continuo) {
        // No se pudo seguir y ya se navegó a donde falta algo. Limpiar el carrito aquí dejaría al
        // comprador con un pedido creado, sus líneas fuera de toda pantalla y la guarda del
        // principio de este método impidiéndole volver a intentarlo.
        return;
      }
      // Aquí y no antes, y **solo sin pasarela**. Las líneas ya son del pedido, así que dejarlas en
      // el carrito invita a comprarlas dos veces; pero quitarlas apenas se crea el pedido rompería
      // el reintento de arriba, porque la guarda del principio de este método exige algo por pagar.
      //
      // Con Wompi o Sistecrédito el comprador sale del sitio con el pago todavía por hacer: vaciar
      // el carrito al salir dejaba con las manos vacías a quien cancelaba en la pasarela o a quien
      // le negaban el crédito. Lo pagado lo quita la pantalla de estado cuando el pedido vuelve
      // pagado (`AlmacenCarritoPorPagar`).
      if (this.terminado()) {
        // Lo que llevó este pedido; lo que se dejó sin marcar se queda en el carrito.
        void this.carrito.quitarPagadas(comando.lineas);
        this.checkout.terminarCompra();
      }
    } catch (error) {
      this.error.set(this.mensajeDeError(error));
    }
  }

  /**
   * `false` cuando no se pudo seguir y ya se navegó a otra pantalla. Quien llama **no puede
   * limpiar el carrito** en ese caso: el pedido ya existe, y sin líneas en el carrito la guarda
   * de `confirmar()` deja al comprador sin forma de volver a intentarlo.
   */
  /**
   * El backend manda `codigoSistecredito` y `estadoSistecredito` en el cuerpo del error justamente
   * para esto: el `801` ("esa persona ya tiene una solicitud en curso") y el `802` ("el monto no
   * alcanza") piden cosas distintas del comprador, y un mensaje genérico no le dice si vale la
   * pena reintentar.
   *
   * <p>El texto del proveedor no llega y no debe llegar: el del 801 habla del estado crediticio
   * de una persona. Lo que llega es el código, y el texto lo pone Transloco.
   *
   * <p><b>Se decide además por el estado, y no solo por el código</b>, porque los códigos de
   * rechazo no son dos: la primera prueba con rechazo simulado contra dev, el 23 de septiembre de
   * 2026, devolvió `codeResponse = 4`, que caía en el genérico —"revisa tus datos e intenta de
   * nuevo"—. Ese consejo es falso dos veces: revisar los datos no cambia una decisión de crédito,
   * y volver a pulsar tampoco sirve.
   *
   * <p>Y hasta ese día ni siquiera el `801` llegaba: `ErrorHttp` solo guardaba `codigo`, así que
   * esta rama era código muerto. Ver `core/http/respuesta-http.ts`.
   */
  private mensajeDeError(error: unknown): string {
    // Quien eligió recoger antes de que la recogida se apagara, con la pestaña abierta: el servidor
    // ya no la acepta, y "revisa tus datos" no le diría qué cambiar.
    if (error instanceof ErrorHttp && error.codigo === 'RETIRO_EN_PUNTO_NO_DISPONIBLE') {
      this.rechazoDeCredito.set(false);
      return this.transloco.translate('checkout.confirmar.retiro_no_disponible');
    }
    // La elegida dejó de cotizar entre esta pantalla y el servidor (ADR-0073): se elige otra.
    if (error instanceof ErrorHttp && error.codigo === 'TRANSPORTADORA_NO_DISPONIBLE') {
      this.rechazoDeCredito.set(false);
      void this.olvidarCotizacion();
      return this.transloco.translate('checkout.confirmar.transportadora_no_disponible');
    }
    // El color de tecnología se confirma antes de comprarse (ADR-0086): el enlace venció entre el
    // carrito y este botón, o la línea nunca lo tuvo. Lo que sirve es volver a escribir.
    if (
      error instanceof ErrorHttp &&
      (error.codigo === 'ENLACE_DE_COMPRA_NO_VIGENTE' ||
        error.codigo === 'DISPONIBILIDAD_POR_CONFIRMAR')
    ) {
      this.rechazoDeCredito.set(false);
      return this.transloco.translate('checkout.confirmar.disponibilidad_por_confirmar');
    }
    // El pedido se creó, pero su reserva venció antes del intento de pago (30 minutos) y la unidad
    // pudo venderse. Ese pedido ya no se cobra: se olvida, y el siguiente clic crea uno nuevo que
    // vuelve a reservar. El carrito sigue lleno porque `confirmar()` no llegó a limpiarlo.
    if (error instanceof ErrorHttp && error.codigo === 'RESERVA_DEL_PEDIDO_VENCIDA') {
      this.rechazoDeCredito.set(false);
      this.checkout.olvidarPedido();
      return this.transloco.translate('checkout.confirmar.reserva_vencida');
    }
    const datos = error instanceof ErrorHttp ? error.datos : {};
    // El servidor ya clasificó el rechazo (`motivoSistecredito`); aquí solo se elige el texto. La
    // tabla de códigos y estados de la pasarela vivía en este componente, distinta de la del
    // backend: un `REJECTED` en mayúsculas lo dejaba sondeando con el consejo falso de revisar datos.
    const clave = CLAVE_MOTIVO_SISTECREDITO[datos['motivoSistecredito'] ?? ''];
    if (clave) {
      this.rechazoDeCredito.set(true);
      return this.transloco.translate(clave);
    }
    this.rechazoDeCredito.set(false);
    return this.transloco.translate('checkout.confirmar.error');
  }

  /** A donde se manda a quien tiene que elegir otro medio, por el mismo camino que ya se usa
   * cuando el documento se perdio en un refresh: una sola forma de llegar ahi. */
  protected elegirOtroMetodo(): void {
    void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
  }

  /**
   * Para que la pantalla de estado quite de este carrito, y solo de este, lo que llevó el pedido
   * cuando vuelva pagado.
   */
  private anotarCarritoPorPagar(pedidoId: string): void {
    const carritoId = this.carrito.carritoId();
    if (carritoId) {
      this.carritosPorPagar.anotar(pedidoId, carritoId);
    }
  }

  private async continuarSegunMetodoPago(pedido: Pedido): Promise<boolean> {
    // Antes de cualquier salida: la pasarela, la transferencia o el estado lo leen de aquí.
    this.correosDePedido.recordar(pedido.id, pedido.correo);
    if (esMetodoPagoSistecredito(pedido.metodoPago)) {
      const documento = this.checkout.documentoComprador();
      if (!documento) {
        // Se perdió en un refresh (vive solo en memoria, a propósito). Volver a pedirlo es lo
        // único honesto: sin documento la pasarela no puede encontrar al cliente, y el pedido ya
        // existe, así que reintentar desde ahí no lo duplica.
        void this.router.navigate(['../metodo-pago'], { relativeTo: this.route });
        return false;
      }
      const intento = await this.checkout.crearIntentoSistecredito(
        pedido.id,
        documento,
        this.transloco.activeLang(),
      );
      this.anotarCarritoPorPagar(pedido.id);
      // La URL la arma la pasarela y es de un solo uso: no se compone nada aquí, solo se va. Y a
      // dónde vuelve el comprador NO se decide en esta llamada —va en `urlResponse`, que el
      // backend fija desde su configuración—, a diferencia de Wompi.
      window.location.href = intento.urlRedireccion;
      return true;
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
      // El correo NO va en la URL: la recuerda el navegador (`AlmacenCorreoDePedido`) y la
      // pantalla de retorno la lee de ahí. En la URL quedaba en los registros y en Wompi.
      const parametrosRetorno = new URLSearchParams({
        referencia: intento.referencia,
        pedidoId: pedido.id,
      });
      const urlRetorno = `${window.location.origin}/${idioma}/checkout/retorno-wompi?${parametrosRetorno.toString()}`;
      this.anotarCarritoPorPagar(pedido.id);
      window.location.href = urlWebCheckoutWompi(intento, urlRetorno);
      return true;
    }

    // Sin pasarela, la compra termina aquí: lo que sigue es su pantalla final.
    this.terminado.set(true);
    if (pedido.metodoPago === 'TRANSFERENCIA_MANUAL') {
      // Con parámetros, igual que el retorno de Wompi: un refresh en la
      // pantalla de transferencia (el comprador copiando el número de
      // cuenta con calma) no debería perder los datos de la cuenta.
      void this.router.navigate(['../transferencia'], {
        relativeTo: this.route,
        queryParams: { pedidoId: pedido.id },
      });
      return true;
    }

    // Con el pedido en la URL por lo mismo que la transferencia: un refresh no lo pierde.
    void this.router.navigate(['../estado'], {
      relativeTo: this.route,
      queryParams: { pedidoId: pedido.id },
    });
    return true;
  }
}
