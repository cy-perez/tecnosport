import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { usarFocoEnPrimerInvalido } from '../../../../shared/foco/foco';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarIdiomaActivo, usarTraductor } from '../../../../core/i18n/traductor';
import { formatearPrecio } from '../../../../shared/ts-precio/formato-precio';
import {
  OpcionMetodoPago,
  TsSelectorMetodoPago,
} from '../selector-metodo-pago/ts-selector-metodo-pago';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import {
  comandoMetodosDePago,
  usarMetodosDePagoDisponibles,
} from '../../application/metodos-de-pago-disponibles.consulta';
import { CheckoutStore } from '../../application/checkout.store';
import { MetodosDePagoDisponiblesComando } from '../../domain/pedido.comandos';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { MetodoDePagoConPrecio, MetodoPago, TipoDocumento } from '../../domain/pedido.model';

const CLAVE_ETIQUETA: Record<MetodoPago, string> = {
  WOMPI: 'checkout.metodoPago.wompi',
  SISTECREDITO: 'checkout.metodoPago.sistecredito',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual',
  CONTRAENTREGA: 'checkout.metodoPago.contraentrega',
  PAGO_EN_PUNTO: 'checkout.metodoPago.pago_en_punto',
};

/**
 * Lo que el nombre del botón no alcanza a decir, al frente del botón y en letra menor. Solo dos
 * lo necesitan.
 *
 * <p><b>`WOMPI` lo necesita más que ninguno</b>: el enum agrupa ahí tarjeta, PSE y botón
 * Bancolombia (`V66`) porque el Web Checkout hospedado nunca recibió cuál había elegido el
 * comprador. Un botón a secas sería peor que los cuatro que reemplaza —nombraría la pasarela en
 * vez del medio— y por eso la etiqueta dice los medios y el detalle dice dónde se eligen.
 *
 * <p>`TRANSFERENCIA_MANUAL` lo necesita por lo contrario: su nombre es claro pero no dice a dónde
 * se transfiere, y desde el 28 de septiembre de 2026 son tres cuentas y no una.
 */
const CLAVE_DETALLE: Partial<Record<MetodoPago, string>> = {
  WOMPI: 'checkout.metodoPago.wompi_detalle',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual_detalle',
  PAGO_EN_PUNTO: 'checkout.metodoPago.pago_en_punto_detalle',
};

/**
 * El orden en que se pintan los botones, que no es el que devuelve el servidor.
 *
 * <p>La respuesta de `POST /pedidos/metodos-de-pago-con-precio` dice **cuáles** se ofrecen, no en
 * qué orden se enseñan: eso es una decisión de la vitrina, y sin fijarla aquí cualquier cambio en
 * el backend reordenaría los botones de la pantalla sin que nadie lo pidiera. Se pidió esta
 * secuencia el 28 de septiembre de 2026.
 *
 * <p>`CONTRAENTREGA` va al final y no estaba en la petición: solo se ofrece donde el sistema la
 * habilita por ciudad (`docs/11-pagos-y-envios.md`), así que en la mayoría de los pedidos no hay
 * cuarto botón. Cuando lo hay, cierra la lista.
 *
 * <p>Un método que llegue del servidor y no esté aquí **se pinta igual**, detrás de los conocidos:
 * un medio de pago que el backend ofrece y la vitrina esconde es una venta que no ocurre.
 */
const ORDEN_DE_PRESENTACION: readonly MetodoPago[] = [
  'WOMPI',
  'TRANSFERENCIA_MANUAL',
  'SISTECREDITO',
  'CONTRAENTREGA',
  'PAGO_EN_PUNTO',
];

/** Los que acepta la pasarela de Sistecrédito (`adr/0048`). */
const TIPOS_DE_DOCUMENTO: readonly TipoDocumento[] = ['CC', 'TI', 'TIE', 'NIT'];

/** La posición de un método en {@link ORDEN_DE_PRESENTACION}; los desconocidos, al final. */
function posicionDe(metodo: MetodoPago): number {
  const posicion = ORDEN_DE_PRESENTACION.indexOf(metodo);
  return posicion === -1 ? ORDEN_DE_PRESENTACION.length : posicion;
}

/**
 * Segundo paso del checkout (Fase 3, paso 4b de `docs/09-plan-de-arranque.md`).
 * Sin dirección/correo guardados de la página de resumen (`CheckoutStore.datosEntrega`)
 * no hay nada que consultar — se vuelve ahí. `POST
 * /pedidos/metodos-de-pago-con-precio` es la misma consulta que el
 * servidor vuelve a aplicar al crear el pedido (`MetodosDePagoDisponibles`
 * en el backend): lo que se ve aquí no es una promesa, es lo que de verdad
 * va a aceptar el siguiente paso.
 */
@Component({
  selector: 'app-metodo-pago',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsSelect,
    TsSelectControl,
    TsSelectorMetodoPago,
  ],
  templateUrl: './metodo-pago.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetodoPagoPage {
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly traducir = usarTraductor();
  private readonly idioma = usarIdiomaActivo();
  protected readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly comando = computed<MetodosDePagoDisponiblesComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    const datosCarrito = this.carrito.consulta.data();
    if (!datos || !datosCarrito || datosCarrito.lineas.length === 0) {
      return null;
    }
    return comandoMetodosDePago(datos, datosCarrito.lineas);
  });

  protected readonly consulta = usarMetodosDePagoDisponibles(() => this.comando());

  /**
   * Cada medio con su detalle y, si pagar con él ahorra algo, "Ahorras $ X" (ADR-0095). Un ahorro
   * y no un total por medio: el precio anunciado es uno solo, y lo que cambia con el medio es el
   * descuento. La cifra es la del servidor, no una resta de precios en el navegador.
   */
  protected readonly opciones = computed<OpcionMetodoPago[]>(() =>
    [...(this.consulta.data() ?? [])]
      .sort((uno, otro) => posicionDe(uno.metodo) - posicionDe(otro.metodo))
      .map((opcion) => {
        const claveDetalle = CLAVE_DETALLE[opcion.metodo];
        const partes = [
          claveDetalle ? this.traducir()(claveDetalle) : null,
          opcion.descuento.valor > 0
            ? this.traducir()('checkout.metodoPago.ahorro', {
                ahorro: formatearPrecio(
                  opcion.descuento.valor,
                  opcion.descuento.moneda,
                  this.idioma(),
                ),
              })
            : null,
        ].filter((parte): parte is string => parte !== null);
        return {
          valor: opcion.metodo,
          etiqueta: this.traducir()(CLAVE_ETIQUETA[opcion.metodo]),
          detalle: partes.length > 0 ? partes.join(' ') : undefined,
        };
      }),
  );

  /**
   * El aviso de contraentrega se muestra cuando la opción se ofrece, no cuando
   * se elige: el deber de informar es previo a la decisión, no posterior. Dice
   * las dos cosas que sorprenden al recibir el paquete — que se cobra el total
   * con el envío incluido, y que la transportadora solo recibe efectivo
   * (docs/12-legales-de-envio.md, sección 3).
   */
  protected readonly ofreceContraentrega = computed(() =>
    this.opciones().some((opcion) => opcion.valor === 'CONTRAENTREGA'),
  );

  /**
   * Lo mismo para pagar al recoger (ADR-0094), y por la misma razón: antes de elegir. Lo que
   * sorprende es que hay que confirmar el pedido y que, listo, se guarda 48 horas.
   */
  protected readonly ofrecePagoEnPunto = computed(() =>
    this.opciones().some((opcion) => opcion.valor === 'PAGO_EN_PUNTO'),
  );

  /**
   * El documento se pide aquí y no en la pantalla de confirmar porque es parte de elegir este
   * método: quien elige Sistecrédito tiene que saber, antes de seguir, que le van a pedir su
   * cédula y por qué. En la pantalla siguiente ya habría aceptado sin verlo.
   */
  protected readonly pideDocumento = computed(() => this.checkout.metodoPago() === 'SISTECREDITO');

  protected readonly opcionesTipoDocumento = computed(() =>
    TIPOS_DE_DOCUMENTO.map((tipo) => ({
      valor: tipo,
      etiqueta: this.traducir()(`checkout.metodoPago.documento.tipo.${tipo.toLowerCase()}`),
    })),
  );

  private readonly fb = inject(NonNullableFormBuilder);

  /**
   * `CC` por omisión porque es lo que usa casi todo el mundo, y un `select` obligatorio que
   * arranca vacío es un paso extra para todos por el caso raro de nadie.
   */
  protected readonly formularioDocumento = this.fb.group({
    tipoDocumento: this.fb.control<TipoDocumento>('CC', Validators.required),
    documento: this.fb.control('', [
      Validators.required,
      // El largo máximo lo rechaza la pasarela con su propio código; esto solo evita mandar un
      // viaje de red con algo que obviamente no es un documento.
      Validators.pattern(/^[A-Za-z0-9]{4,20}$/),
    ]),
  });

  private readonly estadoDocumento = toSignal(this.formularioDocumento.valueChanges, {
    initialValue: this.formularioDocumento.getRawValue(),
  });

  /**
   * Que alguien ya haya pulsado continuar con el formulario inválido. Una señal propia y no
   * `control.touched`: marcar un control como tocado **no emite `valueChanges`**, así que el
   * `computed` de abajo no se recalcularía y el mensaje no aparecería hasta la siguiente tecla.
   * Es la trampa de reactividad que `apps/web/CLAUDE.md` describe — un `FormGroup` no es reactivo
   * por sí mismo, y leerlo desde un `computed` solo funciona si algo observable cambió.
   */
  private readonly intentoContinuar = signal(false);

  /**
   * El error solo después de intentar continuar: mientras se escribe por primera vez, un campo a
   * medias todavía no es un error.
   *
   * <p>El botón de continuar NO se deshabilita por esto. Un botón apagado no dice qué le falta;
   * pulsarlo enseña el mensaje, que es lo que el comprador necesita para arreglarlo.
   */
  protected readonly errorDocumento = computed(() => {
    // Las dos señales: el intento, y el valor (para que el error desaparezca al corregirlo).
    this.estadoDocumento();
    if (!this.intentoContinuar()) {
      return null;
    }
    return this.formularioDocumento.controls.documento.invalid
      ? this.traducir()('checkout.metodoPago.documento.invalido')
      : null;
  });

  constructor() {
    // Sin datos de entrega, o con el carrito vacío (se vació en otra
    // pestaña, por ejemplo): no hay nada que consultar. De vuelta al
    // resumen, que sabe mostrar cada uno de esos dos casos.
    effect(() => {
      const datosCarrito = this.carrito.consulta.data();
      const sinDatosEntrega = this.checkout.datosEntrega() === null;
      const carritoVacio = !!datosCarrito && datosCarrito.lineas.length === 0;
      if (sinDatosEntrega || carritoVacio) {
        void this.router.navigate(['../resumen'], { relativeTo: this.route });
      }
    });
  }

  /** Se pulsó «Continuar» sin elegir método: el botón ya no se deshabilita, así que lo dice. */
  protected readonly faltaMetodo = signal(false);

  /**
   * Se guarda el medio, no su precio: confirmar vuelve a pedirlo con el carrito de ese momento
   * (ADR-0095). Solo uno de los que se ofrecen.
   */
  protected elegir(metodo: MetodoPago): void {
    const ofrecido = this.consulta
      .data()
      ?.some((opcion: MetodoDePagoConPrecio) => opcion.metodo === metodo);
    if (!ofrecido) {
      return;
    }
    this.checkout.elegirMetodoPago(metodo);
    this.faltaMetodo.set(false);
  }

  protected continuar(): void {
    if (!this.checkout.metodoPago()) {
      this.faltaMetodo.set(true);
      return;
    }
    if (this.pideDocumento()) {
      this.intentoContinuar.set(true);
      this.formularioDocumento.markAllAsTouched();
      if (this.formularioDocumento.invalid) {
        this.enfocarPrimerInvalido();
        return;
      }
      const { tipoDocumento, documento } = this.formularioDocumento.getRawValue();
      this.checkout.anotarDocumentoComprador({ tipoDocumento, documento: documento.trim() });
    }
    void this.router.navigate(['../confirmar'], { relativeTo: this.route });
  }
}
