import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import { usarAccionesGarantia } from '../../application/acciones-garantia.mutaciones';
import { usarGarantiasDePedido } from '../../application/garantias-de-pedido.consulta';
import {
  DESENLACES,
  DesenlaceGarantia,
  ESTADOS_QUE_ADMITEN_GARANTIA,
  ReclamacionGarantia,
  VigenciaGarantia,
} from '../../domain/garantia.model';

/** Una linea del pedido, con lo minimo para elegir sobre cual se reclama. */
export interface LineaParaGarantia {
  readonly varianteId: string;
  readonly nombre: string;
}

const CLAVE_VIGENCIA: Record<VigenciaGarantia, string> = {
  CUBIERTA: 'admin.garantias.vigencia.cubierta',
  FUERA_DE_TERMINO: 'admin.garantias.vigencia.fuera_de_termino',
  INDETERMINADA: 'admin.garantias.vigencia.indeterminada',
};

const CLAVE_DESENLACE: Record<DesenlaceGarantia, string> = {
  REPARACION: 'admin.garantias.desenlaces.reparacion',
  REPOSICION: 'admin.garantias.desenlaces.reposicion',
  REINTEGRO: 'admin.garantias.desenlaces.reintegro',
};

/**
 * Vive dentro de la fila expandida del pedido, igual que el panel de retracto: una garantia se
 * reclama sobre una linea concreta de una compra, y elegirla sin el pedido delante seria pedirle a
 * quien atiende que recuerde de memoria lo que acaba de mirar.
 *
 * El plazo de respuesta de la reclamacion no se muestra aqui: vive en la bandeja de PQR, con el
 * resto de solicitudes, porque el reloj es el mismo para todas.
 */
@Component({
  selector: 'app-panel-garantia',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './panel-garantia.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelGarantia {
  readonly pedidoId = input.required<string>();
  readonly estadoPedido = input.required<string>();
  readonly lineas = input.required<readonly LineaParaGarantia[]>();
  /** El total del pedido: techo del reintegro cuando ese es el desenlace elegido. */
  readonly totalPedido = input.required<number>();

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarGarantiasDePedido(() => this.pedidoId());
  protected readonly acciones = usarAccionesGarantia();

  protected readonly error = signal<string | null>(null);

  protected readonly reclamaciones = computed<readonly ReclamacionGarantia[]>(
    () => this.consulta.data() ?? [],
  );

  protected readonly puedeRadicar = computed(() =>
    (ESTADOS_QUE_ADMITEN_GARANTIA as readonly string[]).includes(this.estadoPedido()),
  );

  protected readonly formularioRadicar = new FormGroup({
    varianteId: new FormControl<string>('', { nonNullable: true, validators: [Validators.required] }),
    descripcionDelFallo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected readonly formularioResolver = new FormGroup({
    desenlace: new FormControl<string>('REPARACION', { nonNullable: true }),
    resumenParaElComprador: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    monto: new FormControl<number | null>(null),
    medio: new FormControl<string>('TRANSFERENCIA_BANCARIA', { nonNullable: true }),
    comprobante: new FormControl('', { nonNullable: true }),
  });

  /** El bloque de dinero solo aparece con `REINTEGRO`: es la unica salida que mueve plata. */
  protected readonly esReintegro = computed(() => this.desenlaceElegido() === 'REINTEGRO');

  private readonly desenlaceElegido = signal<string>('REPARACION');

  protected readonly opcionesLinea = computed<OpcionSelect[]>(() =>
    this.lineas().map((linea) => ({ valor: linea.varianteId, etiqueta: linea.nombre })),
  );

  protected readonly opcionesDesenlace = computed<OpcionSelect[]>(() =>
    DESENLACES.map((desenlace) => ({
      valor: desenlace,
      etiqueta: this.traducir()(CLAVE_DESENLACE[desenlace]),
    })),
  );

  protected etiquetaVigencia(vigencia: VigenciaGarantia): string {
    return this.traducir()(CLAVE_VIGENCIA[vigencia]);
  }

  protected etiquetaDesenlace(desenlace: DesenlaceGarantia): string {
    return this.traducir()(CLAVE_DESENLACE[desenlace]);
  }

  protected alCambiarDesenlace(): void {
    const valor = this.formularioResolver.controls.desenlace.value;
    this.desenlaceElegido.set(valor);
    if (valor === 'REINTEGRO' && this.formularioResolver.controls.monto.value === null) {
      this.formularioResolver.controls.monto.setValue(this.totalPedido());
    }
  }

  protected async radicar(): Promise<void> {
    if (this.formularioRadicar.invalid) {
      this.formularioRadicar.markAllAsTouched();
      return;
    }
    const valores = this.formularioRadicar.getRawValue();
    await this.ejecutar(() =>
      this.acciones.radicar.mutateAsync({
        pedidoId: this.pedidoId(),
        varianteId: valores.varianteId,
        descripcionDelFallo: valores.descripcionDelFallo.trim(),
      }),
    );
    this.formularioRadicar.reset({ varianteId: '', descripcionDelFallo: '' });
  }

  protected async resolver(reclamacion: ReclamacionGarantia): Promise<void> {
    if (this.formularioResolver.invalid) {
      this.formularioResolver.markAllAsTouched();
      return;
    }
    const valores = this.formularioResolver.getRawValue();
    const esReintegro = valores.desenlace === 'REINTEGRO';
    const comprobante = valores.comprobante.trim();
    await this.ejecutar(() =>
      this.acciones.resolver.mutateAsync({
        pedidoId: this.pedidoId(),
        reclamacionId: reclamacion.id,
        desenlace: valores.desenlace as DesenlaceGarantia,
        resumenParaElComprador: valores.resumenParaElComprador.trim(),
        monto: esReintegro ? (valores.monto ?? 0) : null,
        medio: esReintegro ? (valores.medio as MedioReintegro) : null,
        comprobante: esReintegro && comprobante !== '' ? comprobante : null,
      }),
    );
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await accion();
    } catch {
      this.error.set(this.transloco.translate('admin.garantias.error'));
    }
  }
}
