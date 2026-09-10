import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsPrecio } from '../../../../../shared/ts-precio/ts-precio';
import { usarAccionesRetracto } from '../../application/acciones-retracto.mutaciones';
import { usarRetractosDePedido } from '../../application/retractos-de-pedido.consulta';
import {
  ESTADOS_QUE_ADMITEN_RETRACTO,
  MedioReintegro,
  SolicitudRetracto,
  VerdictoPlazo,
} from '../../domain/retracto.model';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';

/** El plazo de reintegro se muestra en días, y esta es la única conversión. */
const MILISEGUNDOS_POR_DIA = 86_400_000;

const MEDIOS: readonly MedioReintegro[] = [
  'TRANSFERENCIA_BANCARIA',
  'WOMPI',
  'EFECTIVO',
  'OTRO',
];

const CLAVE_MEDIO: Record<MedioReintegro, string> = {
  TRANSFERENCIA_BANCARIA: 'admin.retractos.medios.transferencia_bancaria',
  WOMPI: 'admin.retractos.medios.wompi',
  EFECTIVO: 'admin.retractos.medios.efectivo',
  OTRO: 'admin.retractos.medios.otro',
};

const CLAVE_VERDICTO: Record<VerdictoPlazo, string> = {
  EN_PLAZO: 'admin.retractos.verdicto.en_plazo',
  VENCIDO: 'admin.retractos.verdicto.vencido',
  INDETERMINADO: 'admin.retractos.verdicto.indeterminado',
};

const CLAVE_ESTADO: Record<SolicitudRetracto['estado'], string> = {
  RADICADA: 'admin.retractos.estados.radicada',
  PRODUCTO_RECIBIDO: 'admin.retractos.estados.producto_recibido',
  REEMBOLSADA: 'admin.retractos.estados.reembolsada',
  RECHAZADA: 'admin.retractos.estados.rechazada',
};

/**
 * Vive dentro de la fila expandida de la lista de pedidos, no en pantalla propia: un retracto no se
 * entiende sin el pedido delante —el total, las líneas, la fecha de entrega— y obligar a navegar a
 * otro sitio para radicarlo sería pedirle a quien atiende que recuerde de memoria lo que acaba de
 * mirar.
 *
 * <p>Consulta y mutaciones propias, en vez de crecer `ListaPedidosAdminPage`: esa página ya lleva
 * seis acciones y tres formularios, y el retracto tiene su propio ciclo de tres pasos.
 */
@Component({
  selector: 'app-panel-retracto',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsPrecio,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './panel-retracto.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelRetracto {
  readonly pedidoId = input.required<string>();
  readonly estadoPedido = input.required<string>();
  /** El total del pedido: precarga el monto del reintegro, que es el caso normal. */
  readonly totalPedido = input.required<number>();

  /**
   * Cuánto entró de verdad por el pedido y cuánto ya volvió al comprador. Se pintan porque el 422 del
   * tope decía "revisa cuánto se le devolvió ya a este pedido" y <b>no había dónde revisarlo</b>:
   * ningún endpoint lo exponía, así que el único camino era reintentar con cifras hasta que una
   * entrara — que es exactamente cómo se registra un reintegro por el monto equivocado.
   */
  readonly dineroRecibido = input.required<number>();
  readonly yaDevuelto = input.required<number>();

  /** Lo que el pedido todavía puede devolver. Nunca negativo: si ya se pasó, es cero. */
  protected readonly quedaPorDevolver = computed(() =>
    Math.max(this.totalPedido() - this.yaDevuelto(), 0),
  );

  /**
   * Si se le va a devolver un dinero que todavía no ha entrado. No bloquea —el comprador que pagó en
   * efectivo tiene derecho aunque la transportadora no haya dispersado— pero quien decide tiene que
   * verlo: si el recaudo nunca llega, la pérdida es doble.
   */
  protected readonly dineroSinEntrar = computed(() => this.dineroRecibido() < this.totalPedido());

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarRetractosDePedido(() => this.pedidoId());
  protected readonly acciones = usarAccionesRetracto();

  protected readonly error = signal<string | null>(null);

  protected readonly solicitudes = computed<readonly SolicitudRetracto[]>(
    () => this.consulta.data() ?? [],
  );

  /** La que sigue viva. Una rechazada no cuenta: se puede volver a radicar sobre ella. */
  protected readonly enCurso = computed<SolicitudRetracto | null>(
    () => this.solicitudes().find((s) => s.estado !== 'RECHAZADA') ?? null,
  );

  protected readonly puedeRadicar = computed(
    () =>
      !this.enCurso() &&
      (ESTADOS_QUE_ADMITEN_RETRACTO as readonly string[]).includes(this.estadoPedido()),
  );

  protected readonly formularioRadicar = new FormGroup({
    motivo: new FormControl('', { nonNullable: true }),
    // Vacío es "no lo dijo", y es el valor por omisión a propósito: preseleccionar un medio sería
    // inventar lo que el comprador pidió, y esa anotación no se corrige después.
    medioPreferido: new FormControl<string>('', { nonNullable: true }),
  });

  protected readonly formularioReintegro = new FormGroup({
    monto: new FormControl<number | null>(null, [Validators.required, Validators.min(1)]),
    medio: new FormControl<string>('TRANSFERENCIA_BANCARIA', { nonNullable: true }),
    medioPreferido: new FormControl<string>('', { nonNullable: true }),
    comprobante: new FormControl('', { nonNullable: true }),
  });

  protected readonly opcionesMedio = computed<OpcionSelect[]>(() =>
    MEDIOS.map((medio) => ({ valor: medio, etiqueta: this.traducir()(CLAVE_MEDIO[medio]) })),
  );

  /**
   * El medio elegido, como señal. Hace falta porque la advertencia de la Ley 2439 tiene que
   * aparecer mientras se elige y no al guardar: un `FormControl` no es una señal, así que un
   * `computed` que leyera `.value` no volvería a evaluarse nunca.
   */
  private readonly medioElegido = toSignal(this.formularioReintegro.controls.medio.valueChanges, {
    initialValue: this.formularioReintegro.controls.medio.value,
  });

  /**
   * El medio que el comprador pidió, cuando el que está por guardarse no lo respeta. Nulo si
   * coinciden o si no pidió nada.
   *
   * <p>Advierte y no bloquea: puede haber un motivo real —una cuenta que rebota— y quien decide es
   * una persona. Lo que no puede pasar es que devuelva por otro medio sin haberlo visto.
   */
  protected readonly preferenciaEnRiesgo = computed<string | null>(() => {
    const pedido = this.enCurso()?.medioPreferido;
    if (!pedido || pedido === this.medioElegido()) {
      return null;
    }
    return this.traducir()(CLAVE_MEDIO[pedido]);
  });

  protected etiquetaMedio(medio: MedioReintegro): string {
    return this.traducir()(CLAVE_MEDIO[medio]);
  }

  protected etiquetaVerdicto(verdicto: VerdictoPlazo): string {
    return this.traducir()(CLAVE_VERDICTO[verdicto]);
  }

  protected etiquetaEstado(estado: SolicitudRetracto['estado']): string {
    return this.traducir()(CLAVE_ESTADO[estado]);
  }

  /**
   * El instante en que se agota el plazo de reintegro, o `null` mientras no corra —el producto no
   * ha vuelto—. Todo lo demás se decide sobre este número y no sobre los días redondeados, que es
   * de donde salió un defecto real: `Math.ceil` de un plazo vencido hace unas horas da `-0`, y
   * `-0` es *falsy* y tampoco es `< 0`. El aviso desaparecía de la pantalla —ni "quedan días" ni
   * "vencido"— durante las primeras veinticuatro horas de incumplimiento, que es justo cuando hay
   * que verlo. Desde la hora 24 volvía a aparecer, así que tampoco se notaba mirando.
   *
   * Una fecha que no se puede leer se trata como "no hay plazo": no es un plazo vencido, y pintar
   * "quedan NaN días" es peor que no pintar nada.
   */
  private readonly limiteDeReintegro = computed<number | null>(() => {
    const limite = this.enCurso()?.limiteDeReintegro;
    if (!limite) {
      return null;
    }
    const instante = new Date(limite).getTime();
    return Number.isFinite(instante) ? instante : null;
  });

  /**
   * Si hay plazo del que hablar. La plantilla pregunta esto y no un número: `@if (dias; as ...)`
   * esconde el bloque cuando el número es cero, y cero es un día que existe.
   */
  protected readonly hayPlazoDeReintegro = computed(() => this.limiteDeReintegro() !== null);

  /**
   * El reloj, leído **una vez** por evaluación. Dos lecturas distintas —una en `plazoVencido` y otra
   * en `diasParaReintegrar`— podían caer una a cada lado del límite y devolver "quedan 0 días" con
   * el plazo ya vencido, que es el mismo `-0` del defecto original por otra puerta.
   *
   * <p>Sigue siendo una foto del momento en que se evaluó la señal: con la pestaña abierta durante
   * horas, el aviso no se refresca solo. Está anotado y no resuelto porque la salida —un temporizador
   * que invalide la señal— es otra decisión: hoy quien atiende recarga la fila para operar.
   */
  private readonly ahora = computed(() => {
    this.enCurso();
    return Date.now();
  });

  /** Vencido se decide comparando instantes. Nunca días, nunca signos. */
  protected readonly plazoVencido = computed(() => {
    const limite = this.limiteDeReintegro();
    return limite !== null && limite <= this.ahora();
  });

  /**
   * Días que faltan para agotar el plazo. Se calcula en el navegador **solo para decidir el énfasis
   * visual**: la fecha límite la manda el servidor ya resuelta, y esta cuenta nunca decide nada que
   * el backend no haya decidido antes. Solo se pinta con el plazo vivo, así que siempre es uno o
   * más.
   */
  protected readonly diasParaReintegrar = computed<number | null>(() => {
    const limite = this.limiteDeReintegro();
    if (limite === null) {
      return null;
    }
    return Math.ceil((limite - this.ahora()) / MILISEGUNDOS_POR_DIA);
  });

  protected async radicar(): Promise<void> {
    const motivo = this.formularioRadicar.controls.motivo.value.trim();
    const preferido = this.formularioRadicar.controls.medioPreferido.value;
    const salioBien = await this.ejecutar(() =>
      this.acciones.radicar.mutateAsync({
        pedidoId: this.pedidoId(),
        motivo: motivo === '' ? null : motivo,
        medioPreferido: preferido === '' ? null : (preferido as MedioReintegro),
      }),
    );
    // Solo si funcionó: borrar el motivo que alguien acabó de escribir, justo cuando el mensaje de
    // error le pide corregir algo, es perder su trabajo en el peor momento.
    if (salioBien) {
      this.formularioRadicar.reset({ motivo: '', medioPreferido: '' });
    }
  }

  protected async recibirProducto(solicitud: SolicitudRetracto): Promise<void> {
    await this.ejecutar(() =>
      this.acciones.recibirProducto.mutateAsync({
        pedidoId: this.pedidoId(),
        solicitudId: solicitud.id,
      }),
    );
  }

  constructor() {
    // Se precarga al abrir y no solo al enfocar el campo, por lo mismo que en el panel de reversion:
    // `(focusin)` deja el monto vacio para quien llega directo al boton, y un monto vacio en un
    // formulario de dinero acaba en un 422 en vez de en una cifra. El efecto depende de las entradas,
    // que no existen antes del primer render.
    effect(() => {
      this.quedaPorDevolver();
      this.prepararReintegro();
    });
  }

  /**
   * Precarga lo que queda por devolver y no el total: con un reintegro previo del mismo pedido, el
   * total era una cifra que el tope iba a rechazar, y el operador se enteraba por un 422.
   */
  protected prepararReintegro(): void {
    if (this.formularioReintegro.controls.monto.value === null) {
      this.formularioReintegro.controls.monto.setValue(this.quedaPorDevolver());
    }
  }

  protected async registrarReintegro(solicitud: SolicitudRetracto): Promise<void> {
    if (this.formularioReintegro.invalid) {
      this.formularioReintegro.markAllAsTouched();
      return;
    }
    const valores = this.formularioReintegro.getRawValue();
    const comprobante = valores.comprobante.trim();
    await this.ejecutar(() =>
      this.acciones.registrarReintegro.mutateAsync({
        pedidoId: this.pedidoId(),
        solicitudId: solicitud.id,
        monto: valores.monto ?? 0,
        medio: valores.medio as MedioReintegro,
        // Solo si la solicitud no lo traía: ya anotado, el backend se niega a cambiarlo, y
        // mandarlo otra vez desde aquí sería pedirle que lo haga.
        medioPreferido:
          solicitud.medioPreferido || valores.medioPreferido === ''
            ? null
            : (valores.medioPreferido as MedioReintegro),
        comprobante: comprobante === '' ? null : comprobante,
      }),
    );
  }

  /** Devuelve si la acción salió bien, para que quien llame decida si limpia su formulario. */
  private async ejecutar(accion: () => Promise<unknown>): Promise<boolean> {
    this.error.set(null);
    try {
      await accion();
      return true;
    } catch (error) {
      // El codigo que manda el backend decide el mensaje; sin codigo, el generico de siempre.
      this.error.set(mensajeDeError(error, this.transloco, 'admin.retractos.error'));
      return false;
    }
  }
}
