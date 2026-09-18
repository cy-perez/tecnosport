import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
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
  EmisionDeGuiaAdmin,
  ESTADOS_QUE_ADMITEN_CANCELACION,
  EstadoPedido,
  FiltroPedidosAdmin,
  MODALIDADES_RECAUDO,
  ModalidadRecaudo,
  MOTIVOS_CANCELACION,
  MotivoCancelacion,
  PedidoAdmin,
} from '../../domain/pedido-admin.model';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { fechaConHora, ultimoDia } from '../../../../../core/i18n/fecha-colombia';

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
  'CANCELADO',
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
  // Faltaba desde que se construyó la cancelación: el panel pintaba la celda de Estado en blanco
  // para todo pedido cancelado, porque `etiquetaEstado` no encontraba clave. Se vio mirando la
  // pantalla, no en una prueba.
  CANCELADO: 'admin.pedidos.estados.cancelado',
};

interface FormularioMotivo {
  motivo: FormControl<string>;
}

interface FormularioGuia {
  transportadora: FormControl<string>;
  guia: FormControl<string>;
  costoEnvio: FormControl<number | null>;
}

/**
 * Un despacho son una o varias guías (`adr/0031`): ninguna transportadora colombiana admite
 * multipaquete, así que un pedido de dos variantes sale en dos paquetes con dos cobros.
 */
interface FormularioDespacho {
  guias: FormArray<FormGroup<FormularioGuia>>;
}

interface FormularioRecaudo {
  modalidadRecaudo: FormControl<ModalidadRecaudo>;
  comisionRecaudo: FormControl<number | null>;
}

interface FormularioCancelacion {
  motivo: FormControl<string>;
  monto: FormControl<number | null>;
  medio: FormControl<string>;
  comprobante: FormControl<string>;
}

const CLAVE_MODALIDAD_RECAUDO: Record<ModalidadRecaudo, string> = {
  CREDITOS: 'admin.pedidos.acciones.modalidades_recaudo.creditos',
  BANCO: 'admin.pedidos.acciones.modalidades_recaudo.banco',
};

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

  /** La última emisión pedida en esta pantalla, para avisar que la guía todavía no existe. Se
   * pierde al recargar y está bien: el estado de verdad lo tiene el servidor, y lo que esto muestra
   * es el acuse de un clic que se acaba de dar. */
  protected readonly emisionPedida = signal<{
    pedidoId: string;
    emision: EmisionDeGuiaAdmin;
  } | null>(null);

  private readonly formulariosMotivo = new Map<string, FormGroup<FormularioMotivo>>();
  private readonly formulariosDespacho = new Map<string, FormGroup<FormularioDespacho>>();
  private readonly formulariosRecaudo = new Map<string, FormGroup<FormularioRecaudo>>();
  private readonly formulariosCancelacion = new Map<string, FormGroup<FormularioCancelacion>>();

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
      form = new FormGroup<FormularioDespacho>({
        guias: new FormArray([grupoDeGuia()]),
      });
      this.formulariosDespacho.set(pedidoId, form);
    }
    return form;
  }

  protected guiasDe(pedidoId: string): FormArray<FormGroup<FormularioGuia>> {
    return this.formularioDespacho(pedidoId).controls.guias;
  }

  protected agregarGuia(pedidoId: string): void {
    this.guiasDe(pedidoId).push(grupoDeGuia());
  }

  /** Nunca se queda en cero: un despacho sin guía no es un despacho. */
  protected quitarGuia(pedidoId: string, indice: number): void {
    const guias = this.guiasDe(pedidoId);
    if (guias.length > 1) {
      guias.removeAt(indice);
    }
  }

  protected formularioRecaudo(pedidoId: string): FormGroup<FormularioRecaudo> {
    let form = this.formulariosRecaudo.get(pedidoId);
    if (!form) {
      form = new FormGroup({
        // Créditos por omisión porque es la modalidad sin comisión: si alguien envía sin mirar, el
        // registro dice "no hubo comisión", que es lo que el formulario deja verdadero sin tocar
        // nada. El servidor rechaza créditos con comisión encima.
        modalidadRecaudo: new FormControl<ModalidadRecaudo>('CREDITOS', { nonNullable: true }),
        comisionRecaudo: new FormControl<number | null>(0, [
          Validators.required,
          Validators.min(0),
        ]),
      });
      // El selector gobierna el campo: con créditos no hay comisión que escribir —la etiqueta de la
      // opción lo dice— así que pedir un cero obligatorio en un campo que la opción declara
      // inexistente era una contradicción en pantalla. Lo levantó la auditoría de accesibilidad.
      const comision = form.controls.comisionRecaudo;
      const gobernar = (modalidad: ModalidadRecaudo) => {
        if (modalidad === 'CREDITOS') {
          comision.setValue(0, { emitEvent: false });
          comision.disable({ emitEvent: false });
        } else {
          comision.enable({ emitEvent: false });
        }
      };
      gobernar(form.controls.modalidadRecaudo.value);
      form.controls.modalidadRecaudo.valueChanges.subscribe(gobernar);
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

  protected readonly opcionesModalidadRecaudo = computed<OpcionSelect[]>(() =>
    MODALIDADES_RECAUDO.map((modalidad) => ({
      valor: modalidad,
      etiqueta: this.traducir()(CLAVE_MODALIDAD_RECAUDO[modalidad]),
    })),
  );

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
        guias: valores.guias.map((guia) => ({
          transportadora: guia.transportadora,
          guia: guia.guia,
          costoEnvio: guia.costoEnvio ?? 0,
        })),
      }),
    );
  }

  /**
   * Le pide las guías a la transportadora. No despacha: lo que vuelve es una emisión en curso, y el
   * pedido sigue en preparación hasta que la tarea del servidor traiga los números. El aviso que se
   * pinta después dice justo eso, para que nadie se quede mirando la pantalla esperando la guía.
   */
  protected async emitirGuia(pedidoId: string): Promise<void> {
    this.emisionPedida.set(null);
    await this.ejecutar(async () => {
      const emision = await this.acciones.emitirGuia.mutateAsync(pedidoId);
      this.emisionPedida.set({ pedidoId, emision });
    });
  }

  /** El aviso de "ya se pidió" solo se pinta sobre el pedido al que corresponde. */
  protected emisionDe(pedidoId: string): EmisionDeGuiaAdmin | null {
    const pedida = this.emisionPedida();
    return pedida?.pedidoId === pedidoId ? pedida.emision : null;
  }

  protected emitiendoGuia(pedidoId: string): boolean {
    return (
      this.acciones.emitirGuia.isPending() && this.acciones.emitirGuia.variables() === pedidoId
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
        modalidadRecaudo: valores.modalidadRecaudo,
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

  /**
   * El mensaje de un control que falta, para pasárselo a `[error]`.
   *
   * <p>Sin esto, los cuatro formularios de esta pantalla eran mudos: sus manejadores terminan en
   * `markAllAsTouched(); return;`, y eso no pinta absolutamente nada si la plantilla no le pasa
   * `[error]` a ningún control — tampoco pone `aria-invalid`. Pulsar el botón no hacía nada y el
   * motivo no se decía en ningún sitio. Es la WCAG 3.3.1, identificación de errores, y lo levantó
   * la auditoría de accesibilidad.
   *
   * <p>Un método y no un `computed` por control: los formularios se crean por fila en un `Map`, así
   * que no hay una señal por control que observar. La plantilla lo vuelve a evaluar en cada
   * detección de cambios, y la que importa la dispara el propio `(submit)`.
   */
  protected errorDe(control: AbstractControl | null | undefined, clave: string): string | null {
    if (!control || !control.touched || control.valid) {
      return null;
    }
    return this.traducir()(clave);
  }

  protected errorComisionRecaudo(pedidoId: string): string | null {
    return this.errorDe(
      this.formularioRecaudo(pedidoId).controls.comisionRecaudo,
      'admin.pedidos.acciones.comision_recaudo_requerida',
    );
  }

  protected conciliandoRecaudo(pedidoId: string): boolean {
    return (
      this.acciones.conciliarRecaudo.isPending() &&
      this.acciones.conciliarRecaudo.variables()?.pedidoId === pedidoId
    );
  }

  /**
   * El último día en que todavía se podía entregar, sin hora.
   *
   * El límite que manda el servidor es el instante en que el plazo se agota, o sea el comienzo del
   * día siguiente: pintarlo tal cual decía «vence 1/09/2026, 12:00 a. m.» y se leía como que había
   * hasta el 1 de septiembre, cuando el último día era el 31 de agosto. Se resta un milisegundo y
   * se pinta solo el día, que es la granularidad que tiene un plazo contado en días.
   */
  protected formatearUltimoDia(iso: string): string {
    if (!iso) {
      return '';
    }
    return ultimoDia(iso, this.transloco.activeLang());
  }

  protected formatearFecha(iso: string): string {
    if (!iso) {
      return '';
    }
    return fechaConHora(iso, this.transloco.activeLang());
  }
}

function grupoDeGuia(): FormGroup<FormularioGuia> {
  return new FormGroup<FormularioGuia>({
    transportadora: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    guia: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    costoEnvio: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
  });
}
