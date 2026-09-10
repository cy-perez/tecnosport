import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { MedioReintegro, VerdictoPlazo } from '../../../retractos/domain/retracto.model';
import { usarAccionesReversion } from '../../application/acciones-reversion.mutaciones';
import { usarReversionesDePedido } from '../../application/reversiones-de-pedido.consulta';
import {
  CAUSALES,
  CausalReversion,
  DESENLACES_REVERSION,
  DesenlaceReversion,
  SolicitudReversion,
} from '../../domain/reversion.model';

const CLAVE_CAUSAL: Record<CausalReversion, string> = {
  FRAUDE: 'admin.reversiones.causales.fraude',
  PRODUCTO_NO_ENTREGADO: 'admin.reversiones.causales.no_entregado',
  PRODUCTO_NO_CORRESPONDE: 'admin.reversiones.causales.no_corresponde',
  PRODUCTO_DEFECTUOSO: 'admin.reversiones.causales.defectuoso',
};

const CLAVE_DESENLACE: Record<DesenlaceReversion, string> = {
  REVERTIDO_POR_EL_EMISOR: 'admin.reversiones.desenlaces.revertido_por_el_emisor',
  REINTEGRADO_DIRECTAMENTE: 'admin.reversiones.desenlaces.reintegrado_directamente',
  RECHAZADA: 'admin.reversiones.desenlaces.rechazada',
  DESISTIDA: 'admin.reversiones.desenlaces.desistida',
};

const CLAVE_ESTADO: Record<SolicitudReversion['estado'], string> = {
  RADICADA: 'admin.reversiones.estados.radicada',
  GESTIONADA: 'admin.reversiones.estados.gestionada',
  RESUELTA: 'admin.reversiones.estados.resuelta',
};

const CLAVE_VERDICTO: Record<VerdictoPlazo, string> = {
  EN_PLAZO: 'admin.reversiones.verdicto.en_plazo',
  VENCIDO: 'admin.reversiones.verdicto.vencido',
  INDETERMINADO: 'admin.reversiones.verdicto.indeterminado',
};

/**
 * Vive dentro de la fila expandida del pedido, igual que el retracto y la garantia.
 *
 * Tres pasos y no dos: radicar con la causal, dejar escrito que se hizo para facilitar el tramite
 * ante el emisor, y cerrar con el desenlace. El paso del medio existe porque los terminos
 * publicados prometen facilitarlo, y una promesa de conducta sin rastro es indemostrable.
 */
@Component({
  selector: 'app-panel-reversion',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsSelect, TsSelectControl],
  templateUrl: './panel-reversion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelReversion {
  readonly pedidoId = input.required<string>();
  /** El total del pedido: techo del reintegro cuando el comercio devuelve directamente. */
  readonly totalPedido = input.required<number>();

  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly consulta = usarReversionesDePedido(() => this.pedidoId());
  protected readonly acciones = usarAccionesReversion();

  protected readonly error = signal<string | null>(null);

  protected readonly reversiones = computed<readonly SolicitudReversion[]>(
    () => this.consulta.data() ?? [],
  );

  protected readonly formularioRadicar = new FormGroup({
    causal: new FormControl<string>('PRODUCTO_NO_ENTREGADO', { nonNullable: true }),
    fechaDelHecho: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descripcion: new FormControl('', { nonNullable: true }),
  });

  protected readonly formularioGestion = new FormGroup({
    gestion: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly formularioResolver = new FormGroup({
    desenlace: new FormControl<string>('REVERTIDO_POR_EL_EMISOR', { nonNullable: true }),
    resumenParaElComprador: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    monto: new FormControl<number | null>(null),
    medio: new FormControl<string>('TRANSFERENCIA_BANCARIA', { nonNullable: true }),
    comprobante: new FormControl('', { nonNullable: true }),
  });

  private readonly desenlaceElegido = signal<string>('REVERTIDO_POR_EL_EMISOR');

  /** El bloque de dinero solo con el desenlace en que la plata sale de aqui. */
  protected readonly devolvemosNosotros = computed(
    () => this.desenlaceElegido() === 'REINTEGRADO_DIRECTAMENTE',
  );

  protected readonly opcionesCausal = computed<OpcionSelect[]>(() =>
    CAUSALES.map((causal) => ({
      valor: causal,
      etiqueta: this.traducir()(CLAVE_CAUSAL[causal]),
    })),
  );

  protected readonly opcionesDesenlace = computed<OpcionSelect[]>(() =>
    DESENLACES_REVERSION.map((desenlace) => ({
      valor: desenlace,
      etiqueta: this.traducir()(CLAVE_DESENLACE[desenlace]),
    })),
  );

  protected etiquetaCausal(causal: CausalReversion): string {
    return this.traducir()(CLAVE_CAUSAL[causal]);
  }

  protected etiquetaDesenlace(desenlace: DesenlaceReversion): string {
    return this.traducir()(CLAVE_DESENLACE[desenlace]);
  }

  protected etiquetaEstado(estado: SolicitudReversion['estado']): string {
    return this.traducir()(CLAVE_ESTADO[estado]);
  }

  protected etiquetaVerdicto(verdicto: VerdictoPlazo): string {
    return this.traducir()(CLAVE_VERDICTO[verdicto]);
  }

  protected alCambiarDesenlace(): void {
    const valor = this.formularioResolver.controls.desenlace.value;
    this.desenlaceElegido.set(valor);
    const monto = this.formularioResolver.controls.monto;
    if (valor === 'REINTEGRADO_DIRECTAMENTE' && monto.value === null) {
      monto.setValue(this.totalPedido());
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
        causal: valores.causal as CausalReversion,
        fechaDelHecho: new Date(valores.fechaDelHecho).toISOString(),
        descripcion: valores.descripcion.trim(),
      }),
    );
    this.formularioRadicar.reset({
      causal: 'PRODUCTO_NO_ENTREGADO',
      fechaDelHecho: '',
      descripcion: '',
    });
  }

  protected async gestionar(reversion: SolicitudReversion): Promise<void> {
    if (this.formularioGestion.invalid) {
      this.formularioGestion.markAllAsTouched();
      return;
    }
    await this.ejecutar(() =>
      this.acciones.gestionar.mutateAsync({
        pedidoId: this.pedidoId(),
        reversionId: reversion.id,
        gestion: this.formularioGestion.controls.gestion.value.trim(),
      }),
    );
    this.formularioGestion.reset({ gestion: '' });
  }

  protected async resolver(reversion: SolicitudReversion): Promise<void> {
    if (this.formularioResolver.invalid) {
      this.formularioResolver.markAllAsTouched();
      return;
    }
    const valores = this.formularioResolver.getRawValue();
    const devolvemos = valores.desenlace === 'REINTEGRADO_DIRECTAMENTE';
    const comprobante = valores.comprobante.trim();
    await this.ejecutar(() =>
      this.acciones.resolver.mutateAsync({
        pedidoId: this.pedidoId(),
        reversionId: reversion.id,
        desenlace: valores.desenlace as DesenlaceReversion,
        resumenParaElComprador: valores.resumenParaElComprador.trim(),
        monto: devolvemos ? (valores.monto ?? 0) : null,
        medio: devolvemos ? (valores.medio as MedioReintegro) : null,
        comprobante: devolvemos && comprobante !== '' ? comprobante : null,
      }),
    );
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await accion();
    } catch {
      this.error.set(this.transloco.translate('admin.reversiones.error'));
    }
  }
}
