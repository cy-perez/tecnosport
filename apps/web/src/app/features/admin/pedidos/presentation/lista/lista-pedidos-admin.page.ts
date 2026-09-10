import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { TsPrecio } from '../../../../../shared/ts-precio/ts-precio';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { PanelGarantia } from '../../../garantias/presentation/panel-garantia/panel-garantia';
import { PanelRetracto } from '../../../retractos/presentation/panel-retracto/panel-retracto';
import { PanelReversion } from '../../../reversiones/presentation/panel-reversion/panel-reversion';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarAccionesPedidoAdmin } from '../../application/acciones-pedido-admin.mutaciones';
import { usarListarPedidosAdmin } from '../../application/listar-pedidos-admin.consulta';
import { MedioReintegro } from '../../../retractos/domain/retracto.model';
import {
  ESTADOS_QUE_ADMITEN_CANCELACION,
  EstadoPedido,
  FiltroPedidosAdmin,
  MOTIVOS_CANCELACION,
  MotivoCancelacion,
  PedidoAdmin,
} from '../../domain/pedido-admin.model';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';

const ESTADOS: readonly EstadoPedido[] = [
  'PAGO_PENDIENTE',
  'PAGADO',
  'PAGO_FALLIDO',
  'CONFIRMADO_CONTRAENTREGA',
  'EN_PREPARACION',
  'DESPACHADO',
  'ENTREGADO',
  'RECHAZADO_EN_ENTREGA',
  'DEVUELTO',
  'RECAUDO_PENDIENTE',
  'RECAUDO_CONCILIADO',
];

const CLAVE_ETIQUETA_ESTADO: Record<EstadoPedido, string> = {
  PAGO_PENDIENTE: 'admin.pedidos.estados.pago_pendiente',
  PAGADO: 'admin.pedidos.estados.pagado',
  PAGO_FALLIDO: 'admin.pedidos.estados.pago_fallido',
  CONFIRMADO_CONTRAENTREGA: 'admin.pedidos.estados.confirmado_contraentrega',
  EN_PREPARACION: 'admin.pedidos.estados.en_preparacion',
  DESPACHADO: 'admin.pedidos.estados.despachado',
  ENTREGADO: 'admin.pedidos.estados.entregado',
  RECHAZADO_EN_ENTREGA: 'admin.pedidos.estados.rechazado_en_entrega',
  DEVUELTO: 'admin.pedidos.estados.devuelto',
  RECAUDO_PENDIENTE: 'admin.pedidos.estados.recaudo_pendiente',
  RECAUDO_CONCILIADO: 'admin.pedidos.estados.recaudo_conciliado',
};

interface FormularioMotivo {
  motivo: FormControl<string>;
}

interface FormularioDespacho {
  transportadora: FormControl<string>;
  guia: FormControl<string>;
  costoEnvio: FormControl<number | null>;
}

interface FormularioRecaudo {
  comisionRecaudo: FormControl<number | null>;
}

interface FormularioCancelacion {
  motivo: FormControl<string>;
  monto: FormControl<number | null>;
  medio: FormControl<string>;
  comprobante: FormControl<string>;
}

const CLAVE_MOTIVO_CANCELACION: Record<MotivoCancelacion, string> = {
  NO_DISPONIBILIDAD: 'admin.pedidos.cancelacion.motivos.no_disponibilidad',
  PLAZO_INCUMPLIDO: 'admin.pedidos.cancelacion.motivos.plazo_incumplido',
};

/**
 * Fila expandible en vez de una pantalla de detalle aparte: no existe
 * `GET /admin/pedidos/{id}` (`docs/03-api.md`), así que el detalle usa los datos
 * que ya trajo la lista. Un formulario de acción por fila (mapa creado a
 * demanda, no un componente aparte: cada forma se usa una sola vez aquí).
 */
@Component({
  selector: 'app-lista-pedidos-admin',
  imports: [
    PanelGarantia,
    PanelRetracto,
    PanelReversion,
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsEsqueleto,
    TsMigas,
    TsPaginador,
    TsPrecio,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './lista-pedidos-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaPedidosAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.pedidos.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly acciones = usarAccionesPedidoAdmin();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  protected readonly filtro = computed<FiltroPedidosAdmin>(() =>
    filtroDesdeQueryParams(this.queryParams()),
  );

  protected readonly consulta = usarListarPedidosAdmin(this.filtro);

  protected readonly pedidos = computed<readonly PedidoAdmin[]>(
    () => this.consulta.data()?.items ?? [],
  );

  /**
   * Vacía de verdad, no una página fuera de rango: `Page.getTotalPages()` da 0
   * solo cuando no hay ningún pedido, y 1 cuando sí los hay pero la página
   * pedida se pasó del final. Sin la distinción, `?pagina=5` afirmaba que no
   * había pedidos habiéndolos — visto en el navegador, en la lista hermana de
   * productos.
   */
  protected readonly sinPedidos = computed(
    () => this.pedidos().length === 0 && (this.consulta.data()?.totalPaginas ?? 0) === 0,
  );

  protected readonly formularioEstado = new FormControl('', { nonNullable: true });

  protected readonly opcionesEstado = computed<OpcionSelect[]>(() =>
    ESTADOS.map((estado) => ({
      valor: estado,
      etiqueta: this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]),
    })),
  );

  protected readonly pedidoExpandidoId = signal<string | null>(null);
  protected readonly errorAccion = signal<string | null>(null);

  private readonly formulariosMotivo = new Map<string, FormGroup<FormularioMotivo>>();
  private readonly formulariosDespacho = new Map<string, FormGroup<FormularioDespacho>>();
  private readonly formulariosRecaudo = new Map<string, FormGroup<FormularioRecaudo>>();
  private readonly formulariosCancelacion = new Map<
    string,
    FormGroup<FormularioCancelacion>
  >();

  constructor() {
    effect(() => {
      this.formularioEstado.setValue(this.filtro().estado ?? '', { emitEvent: false });
    });

    this.formularioEstado.valueChanges.pipe(takeUntilDestroyed()).subscribe((estado) => {
      this.navegarA({
        ...this.filtro(),
        pagina: 0,
        estado: (estado || null) as EstadoPedido | null,
      });
    });
  }

  protected etiquetaEstado(estado: EstadoPedido): string {
    return this.traducir()(CLAVE_ETIQUETA_ESTADO[estado]);
  }

  protected alternarExpandido(pedidoId: string): void {
    this.pedidoExpandidoId.set(this.pedidoExpandidoId() === pedidoId ? null : pedidoId);
  }

  protected irAPagina(pagina: number): void {
    this.navegarA({ ...this.filtro(), pagina });
  }

  private navegarA(filtro: FiltroPedidosAdmin): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: queryParamsDesdeFiltro(filtro),
    });
  }

  protected formularioMotivo(pedidoId: string): FormGroup<FormularioMotivo> {
    let form = this.formulariosMotivo.get(pedidoId);
    if (!form) {
      form = new FormGroup({
        motivo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      });
      this.formulariosMotivo.set(pedidoId, form);
    }
    return form;
  }

  protected formularioDespacho(pedidoId: string): FormGroup<FormularioDespacho> {
    let form = this.formulariosDespacho.get(pedidoId);
    if (!form) {
      form = new FormGroup({
        transportadora: new FormControl('', {
          nonNullable: true,
          validators: [Validators.required],
        }),
        guia: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
        costoEnvio: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
      });
      this.formulariosDespacho.set(pedidoId, form);
    }
    return form;
  }

  protected formularioRecaudo(pedidoId: string): FormGroup<FormularioRecaudo> {
    let form = this.formulariosRecaudo.get(pedidoId);
    if (!form) {
      form = new FormGroup({
        comisionRecaudo: new FormControl<number | null>(null, [
          Validators.required,
          Validators.min(0),
        ]),
      });
      this.formulariosRecaudo.set(pedidoId, form);
    }
    return form;
  }

  protected formularioCancelacion(pedidoId: string): FormGroup<FormularioCancelacion> {
    let form = this.formulariosCancelacion.get(pedidoId);
    if (!form) {
      form = new FormGroup({
        motivo: new FormControl<string>('NO_DISPONIBILIDAD', { nonNullable: true }),
        monto: new FormControl<number | null>(null),
        medio: new FormControl<string>('WOMPI', { nonNullable: true }),
        comprobante: new FormControl('', { nonNullable: true }),
      });
      this.formulariosCancelacion.set(pedidoId, form);
    }
    return form;
  }

  protected readonly opcionesMotivoCancelacion = computed<OpcionSelect[]>(() =>
    MOTIVOS_CANCELACION.map((motivo) => ({
      valor: motivo,
      etiqueta: this.traducir()(CLAVE_MOTIVO_CANCELACION[motivo]),
    })),
  );

  protected puedeCancelar(pedido: PedidoAdmin): boolean {
    return (ESTADOS_QUE_ADMITEN_CANCELACION as readonly string[]).includes(pedido.estado);
  }

  /**
   * Un contraentrega cobra al entregar, asi que antes de despachar nunca entro un peso. En los
   * demas metodos, llegar a PAGADO o EN_PREPARACION significa que el pago se aplico. La misma regla
   * que aplica el servidor, aqui solo para decidir que campos mostrar: quien manda es el.
   */
  protected elDineroYaEntro(pedido: PedidoAdmin): boolean {
    if (pedido.metodoPago === 'CONTRAENTREGA') {
      return false;
    }
    return pedido.estado === 'PAGADO' || pedido.estado === 'EN_PREPARACION';
  }

  protected async cancelar(pedido: PedidoAdmin): Promise<void> {
    const form = this.formularioCancelacion(pedido.id);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    const valores = form.getRawValue();
    const hayDinero = this.elDineroYaEntro(pedido);
    const comprobante = valores.comprobante.trim();
    await this.ejecutar(() =>
      this.acciones.cancelar.mutateAsync({
        pedidoId: pedido.id,
        motivo: valores.motivo as MotivoCancelacion,
        monto: hayDinero ? (valores.monto ?? pedido.total.valor) : null,
        medio: hayDinero ? (valores.medio as MedioReintegro) : null,
        comprobante: hayDinero && comprobante !== '' ? comprobante : null,
      }),
    );
  }

  protected async conciliarTransferencia(pedidoId: string): Promise<void> {
    await this.ejecutar(() => this.acciones.conciliarTransferencia.mutateAsync(pedidoId));
  }

  protected async verificarContraentrega(pedido: PedidoAdmin): Promise<void> {
    const form = this.formularioMotivo(pedido.id);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    await this.ejecutar(() =>
      this.acciones.verificarContraentrega.mutateAsync({
        pedidoId: pedido.id,
        motivo: form.controls.motivo.value,
      }),
    );
    form.reset({ motivo: '' });
  }

  protected async despachar(pedido: PedidoAdmin): Promise<void> {
    const form = this.formularioDespacho(pedido.id);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    const valores = form.getRawValue();
    await this.ejecutar(() =>
      this.acciones.despachar.mutateAsync({
        pedidoId: pedido.id,
        transportadora: valores.transportadora,
        guia: valores.guia,
        costoEnvio: valores.costoEnvio ?? 0,
      }),
    );
  }

  protected async marcarEntregado(pedidoId: string): Promise<void> {
    await this.ejecutar(() => this.acciones.marcarEntregado.mutateAsync(pedidoId));
  }

  protected async rechazarEnEntrega(pedido: PedidoAdmin): Promise<void> {
    const form = this.formularioMotivo(pedido.id);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    await this.ejecutar(() =>
      this.acciones.rechazarEnEntrega.mutateAsync({
        pedidoId: pedido.id,
        motivo: form.controls.motivo.value,
      }),
    );
    form.reset({ motivo: '' });
  }

  protected async conciliarRecaudo(pedido: PedidoAdmin): Promise<void> {
    const form = this.formularioRecaudo(pedido.id);
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    const valores = form.getRawValue();
    await this.ejecutar(() =>
      this.acciones.conciliarRecaudo.mutateAsync({
        pedidoId: pedido.id,
        comisionRecaudo: valores.comisionRecaudo ?? 0,
      }),
    );
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.errorAccion.set(null);
    try {
      await accion();
    } catch (error) {
      // El codigo que manda el backend decide el mensaje; sin codigo, el generico de siempre.
      this.errorAccion.set(mensajeDeError(error, this.transloco, 'admin.pedidos.acciones.error'));
    }
  }

  protected conciliandoTransferencia(pedidoId: string): boolean {
    return (
      this.acciones.conciliarTransferencia.isPending() &&
      this.acciones.conciliarTransferencia.variables() === pedidoId
    );
  }

  protected verificandoContraentrega(pedidoId: string): boolean {
    return (
      this.acciones.verificarContraentrega.isPending() &&
      this.acciones.verificarContraentrega.variables()?.pedidoId === pedidoId
    );
  }

  protected despachando(pedidoId: string): boolean {
    return (
      this.acciones.despachar.isPending() &&
      this.acciones.despachar.variables()?.pedidoId === pedidoId
    );
  }

  protected marcandoEntregado(pedidoId: string): boolean {
    return (
      this.acciones.marcarEntregado.isPending() &&
      this.acciones.marcarEntregado.variables() === pedidoId
    );
  }

  protected rechazando(pedidoId: string): boolean {
    return (
      this.acciones.rechazarEnEntrega.isPending() &&
      this.acciones.rechazarEnEntrega.variables()?.pedidoId === pedidoId
    );
  }

  protected conciliandoRecaudo(pedidoId: string): boolean {
    return (
      this.acciones.conciliarRecaudo.isPending() &&
      this.acciones.conciliarRecaudo.variables()?.pedidoId === pedidoId
    );
  }

  protected formatearFecha(iso: string): string {
    if (!iso) {
      return '';
    }
    const idioma = this.transloco.activeLang();
    const locale = idioma === 'en' ? 'en-US' : 'es-CO';
    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Bogota',
    }).format(new Date(iso));
  }
}
