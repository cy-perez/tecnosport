import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
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
   * Días que faltan para agotar el plazo de reintegro. Se calcula en el navegador **solo para
   * decidir el énfasis visual**: la fecha límite la manda el servidor ya resuelta, y esta cuenta
   * nunca decide nada que el backend no haya decidido antes.
   */
  protected readonly diasParaReintegrar = computed<number | null>(() => {
    const limite = this.enCurso()?.limiteDeReintegro;
    if (!limite) {
      return null;
    }
    const milisegundosPorDia = 86_400_000;
    return Math.ceil((new Date(limite).getTime() - Date.now()) / milisegundosPorDia);
  });

  protected readonly plazoVencido = computed(() => {
    const dias = this.diasParaReintegrar();
    return dias !== null && dias < 0;
  });

  protected async radicar(): Promise<void> {
    const motivo = this.formularioRadicar.controls.motivo.value.trim();
    const preferido = this.formularioRadicar.controls.medioPreferido.value;
    await this.ejecutar(() =>
      this.acciones.radicar.mutateAsync({
        pedidoId: this.pedidoId(),
        motivo: motivo === '' ? null : motivo,
        medioPreferido: preferido === '' ? null : (preferido as MedioReintegro),
      }),
    );
    this.formularioRadicar.reset({ motivo: '', medioPreferido: '' });
  }

  protected async recibirProducto(solicitud: SolicitudRetracto): Promise<void> {
    await this.ejecutar(() =>
      this.acciones.recibirProducto.mutateAsync({
        pedidoId: this.pedidoId(),
        solicitudId: solicitud.id,
      }),
    );
  }

  protected prepararReintegro(): void {
    if (this.formularioReintegro.controls.monto.value === null) {
      this.formularioReintegro.controls.monto.setValue(this.totalPedido());
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

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await accion();
    } catch {
      this.error.set(this.transloco.translate('admin.retractos.error'));
    }
  }
}
