import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import {
  OpcionMetodoPago,
  TsSelectorMetodoPago,
} from '../selector-metodo-pago/ts-selector-metodo-pago';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { CarritoStore } from '../../../carrito/application/carrito.store';
import { usarMetodosDePagoDisponibles } from '../../application/metodos-de-pago-disponibles.consulta';
import { CheckoutStore } from '../../application/checkout.store';
import { MetodosDePagoDisponiblesComando } from '../../domain/pedido.comandos';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { MetodoPago, TipoDocumento } from '../../domain/pedido.model';

const CLAVE_ETIQUETA: Record<MetodoPago, string> = {
  TARJETA: 'checkout.metodoPago.tarjeta',
  PSE: 'checkout.metodoPago.pse',
  NEQUI: 'checkout.metodoPago.nequi',
  BANCOLOMBIA: 'checkout.metodoPago.bancolombia',
  SISTECREDITO: 'checkout.metodoPago.sistecredito',
  TRANSFERENCIA_MANUAL: 'checkout.metodoPago.transferencia_manual',
  CONTRAENTREGA: 'checkout.metodoPago.contraentrega',
};

/** Los que acepta la pasarela de Sistecrédito (`adr/0048`). */
const TIPOS_DE_DOCUMENTO: readonly TipoDocumento[] = ['CC', 'TI', 'TIE', 'NIT'];

/**
 * Segundo paso del checkout (Fase 3, paso 4b de `docs/09-plan-de-arranque.md`).
 * Sin dirección/correo guardados de la página de resumen (`CheckoutStore.datosEntrega`)
 * no hay nada que consultar — se vuelve ahí. `POST
 * /pedidos/metodos-de-pago-disponibles` es la misma consulta que el
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly traducir = usarTraductor();
  protected readonly carrito = inject(CarritoStore);
  protected readonly checkout = inject(CheckoutStore);

  protected readonly comando = computed<MetodosDePagoDisponiblesComando | null>(() => {
    const datos = this.checkout.datosEntrega();
    const datosCarrito = this.carrito.consulta.data();
    if (!datos || !datosCarrito || datosCarrito.lineas.length === 0) {
      return null;
    }
    return {
      correo: datos.correo,
      lineas: datosCarrito.lineas.map((linea) => ({
        varianteId: linea.varianteId,
        cantidad: linea.cantidad,
      })),
      tipoEntrega: datos.tipoEntrega,
      direccion: datos.direccion,
    };
  });

  protected readonly consulta = usarMetodosDePagoDisponibles(() => this.comando());

  protected readonly opciones = computed<OpcionMetodoPago[]>(() =>
    (this.consulta.data() ?? []).map((metodo) => ({
      valor: metodo,
      etiqueta: this.traducir()(CLAVE_ETIQUETA[metodo]),
    })),
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

  protected elegir(metodo: MetodoPago): void {
    this.checkout.elegirMetodoPago(metodo);
  }

  protected continuar(): void {
    if (!this.checkout.metodoPago()) {
      return;
    }
    if (this.pideDocumento()) {
      this.intentoContinuar.set(true);
      this.formularioDocumento.markAllAsTouched();
      if (this.formularioDocumento.invalid) {
        return;
      }
      const { tipoDocumento, documento } = this.formularioDocumento.getRawValue();
      this.checkout.anotarDocumentoComprador({ tipoDocumento, documento: documento.trim() });
    }
    void this.router.navigate(['../confirmar'], { relativeTo: this.route });
  }
}
