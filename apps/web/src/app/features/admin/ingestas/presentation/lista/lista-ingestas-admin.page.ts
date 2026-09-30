import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import { usarListarIngestas } from '../../application/listar-ingestas.consulta';
import { usarSubirExportacion } from '../../application/subir-exportacion.mutacion';
import { EstadoLote, FiltroLotes, LoteIngesta } from '../../domain/ingesta.model';
import {
  filtroLotesDesdeQueryParams,
  queryParamsDesdeFiltroLotes,
} from '../../domain/query-params-filtro';

const CLAVE_ESTADO: Record<EstadoLote, string> = {
  RECIBIDO: 'admin.ingestas.estados.RECIBIDO',
  PROCESANDO: 'admin.ingestas.estados.PROCESANDO',
  TERMINADO: 'admin.ingestas.estados.TERMINADO',
  ERROR: 'admin.ingestas.estados.ERROR',
};

const CLASES_INSIGNIA =
  'inline-flex items-center rounded-completo border px-12 py-4 text-xs font-medio';

const CLASES_ESTADO: Record<EstadoLote, string> = {
  RECIBIDO: 'border-ts-borde text-ts-texto-suave',
  PROCESANDO: 'border-ts-primario text-ts-primario',
  TERMINADO: 'border-ts-exito text-ts-exito',
  ERROR: 'border-ts-error text-ts-error',
};

/**
 * Lo que acepta el `<input type="file">`: el zip que exporta WhatsApp, con el tipo que diga el
 * sistema. Un archivo sin tipo pasa solo si termina en `.zip`.
 */
const TIPOS_DE_ZIP = ['application/zip', 'application/x-zip-compressed'];

/**
 * Subir una exportación de chat y ver cómo van los lotes.
 *
 * <p>El formulario es corto a propósito: proveedor y archivo. Todo lo demás —qué mensajes son del
 * proveedor, cómo se agrupan, qué dice cada uno— lo decide el servidor en segundo plano, y la
 * tabla de abajo se refresca sola mientras haya un lote corriendo (`usarListarIngestas`).
 *
 * <p>Volver a subir el mismo archivo es seguro: el servidor reconoce los mensajes que ya tiene
 * por su huella y el lote termina con cero nuevos. Se dice en la explicación para que nadie tenga
 * miedo de repetir.
 */
@Component({
  selector: 'app-lista-ingestas-admin',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    TsBoton,
    TsEsqueleto,
    TsMigas,
    TsPaginador,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './lista-ingestas-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaIngestasAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.ingestas.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  protected readonly filtro = computed<FiltroLotes>(() =>
    filtroLotesDesdeQueryParams(this.queryParams()),
  );

  protected readonly proveedores = usarProveedoresAdmin();
  protected readonly consulta = usarListarIngestas(this.filtro);
  private readonly subida = usarSubirExportacion();

  protected readonly lotes = computed<readonly LoteIngesta[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly totalLotes = computed(() => this.consulta.data()?.totalLotes ?? 0);
  protected readonly sinLotes = computed(
    () => this.lotes().length === 0 && (this.consulta.data()?.totalPaginas ?? 0) === 0,
  );

  protected readonly subiendo = computed(() => this.subida.isPending());
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly archivo = signal<File | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoSubida = viewChild<ElementRef<HTMLElement>>('avisoSubida');
  private readonly entradaArchivo = viewChild<ElementRef<HTMLInputElement>>('entradaArchivo');

  /** El proveedor al que se le sube: independiente del filtro de la tabla. */
  protected readonly proveedorParaSubir = new FormControl('', { nonNullable: true });
  /** El filtro de la tabla, que vive en la URL: el control solo la navega. */
  protected readonly proveedorFiltro = new FormControl('', { nonNullable: true });

  protected readonly opcionesProveedor = computed<OpcionSelect[]>(() =>
    (this.proveedores.data() ?? []).map((proveedor) => ({
      valor: proveedor.id,
      etiqueta: proveedor.nombre,
    })),
  );

  /** Solo los activos para subir: al inactivo el servidor se lo rechaza igual. */
  protected readonly opcionesProveedorActivo = computed<OpcionSelect[]>(() =>
    (this.proveedores.data() ?? [])
      .filter((proveedor) => proveedor.activo)
      .map((proveedor) => ({ valor: proveedor.id, etiqueta: proveedor.nombre })),
  );

  private readonly nombresDeProveedor = computed(
    () => new Map((this.proveedores.data() ?? []).map((p) => [p.id, p.nombre] as const)),
  );

  /** Sin ningún proveedor activo no hay a quién subirle: se dice y se enlaza el alta. */
  protected readonly sinProveedoresActivos = computed(
    () => this.proveedores.isSuccess() && this.opcionesProveedorActivo().length === 0,
  );

  constructor() {
    // Un `effect`: con "atrás" del navegador cambia la URL y la tabla, y el desplegable la sigue.
    effect(() => {
      this.proveedorFiltro.setValue(this.filtro().proveedorId, { emitEvent: false });
    });
  }

  protected nombreDelProveedor(lote: LoteIngesta): string {
    return this.nombresDeProveedor().get(lote.proveedorId) ?? lote.proveedorId;
  }

  protected etiquetaEstado(estado: EstadoLote): string {
    return this.traducir()(CLAVE_ESTADO[estado]);
  }

  protected clasesEstado(estado: EstadoLote): string {
    return CLASES_INSIGNIA + ' ' + CLASES_ESTADO[estado];
  }

  protected formatearFecha(iso: string | null): string {
    return iso ? fechaConHora(iso, this.transloco.activeLang()) : '';
  }

  protected onArchivoSeleccionado(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    this.error.set(null);
    if (archivo && !TIPOS_DE_ZIP.includes(archivo.type) && !archivo.name.endsWith('.zip')) {
      this.error.set(this.transloco.translate('admin.ingestas.subir.tipoNoSoportado'));
      input.value = '';
      this.archivo.set(null);
      return;
    }
    this.archivo.set(archivo);
  }

  protected subir(): void {
    if (this.subiendo()) {
      return;
    }
    const proveedorId = this.proveedorParaSubir.value;
    const archivo = this.archivo();
    if (!proveedorId || !archivo) {
      this.error.set(this.transloco.translate('admin.ingestas.subir.faltanCampos'));
      return;
    }
    this.error.set(null);
    this.aviso.set(null);

    this.subida.mutate(
      { proveedorId, archivo },
      {
        onSuccess: () => {
          this.aviso.set(archivo.name);
          this.archivo.set(null);
          const entrada = this.entradaArchivo()?.nativeElement;
          if (entrada) {
            entrada.value = '';
          }
          this.enfocarDespuesDePintar(() => this.avisoSubida()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.error.set(mensajeDeError(error, this.transloco, 'admin.ingestas.subir.error')),
      },
    );
  }

  protected filtrarPorProveedor(proveedorId: string): void {
    this.navegarA({ proveedorId, pagina: 0 });
  }

  protected irAPagina(pagina: number): void {
    this.navegarA({ ...this.filtro(), pagina });
  }

  private navegarA(filtro: FiltroLotes): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: queryParamsDesdeFiltroLotes(filtro),
    });
  }
}
