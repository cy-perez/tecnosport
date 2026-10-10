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
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { usarFoco } from '../../../../../shared/foco/foco';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import {
  usarBorrarBorradoresSinAprobar,
  usarContarBorradoresSinAprobar,
} from '../../application/borrar-sin-aprobar.mutacion';
import { usarListarBorradores } from '../../application/listar-borradores.consulta';
import {
  Borrador,
  ESTADOS_BORRADOR,
  EstadoBorrador,
  FiltroBorradores,
} from '../../domain/borrador.model';
import {
  filtroBorradoresDesdeQueryParams,
  queryParamsDesdeFiltroBorradores,
} from '../../domain/query-params-filtro';
import { clasesDeEstadoBorrador } from '../estado-borrador';

/**
 * Los borradores que dejó la extracción, para revisar. Por omisión se ven todos; el filtro por
 * estado y por proveedor vive en la URL. Cada fila lleva a su detalle, que es donde se decide.
 *
 * Arriba, la limpieza de la bandeja: borrar de una vez los que nadie aprobó. No sigue el filtro de
 * la tabla —borra todos los sin aprobar, de todos los proveedores—, y por eso la pregunta dice
 * cuántos son.
 */
@Component({
  selector: 'app-lista-borradores-admin',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsEsqueleto,
    TsMigas,
    TsPaginador,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './lista-borradores-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaBorradoresAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.borradores.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  protected readonly filtro = computed<FiltroBorradores>(() =>
    filtroBorradoresDesdeQueryParams(this.queryParams()),
  );

  protected readonly proveedores = usarProveedoresAdmin();
  protected readonly consulta = usarListarBorradores(this.filtro);

  protected readonly borradores = computed<readonly Borrador[]>(
    () => this.consulta.data()?.items ?? [],
  );
  protected readonly total = computed(() => this.consulta.data()?.totalBorradores ?? 0);
  protected readonly sinBorradores = computed(
    () => this.borradores().length === 0 && (this.consulta.data()?.totalPaginas ?? 0) === 0,
  );

  protected readonly estadoFiltro = new FormControl('', { nonNullable: true });
  protected readonly proveedorFiltro = new FormControl('', { nonNullable: true });

  protected readonly opcionesEstado = computed<OpcionSelect[]>(() =>
    ESTADOS_BORRADOR.map((estado) => ({
      valor: estado,
      etiqueta: this.traducir()('admin.borradores.estados.' + estado),
    })),
  );

  protected readonly opcionesProveedor = computed<OpcionSelect[]>(() =>
    (this.proveedores.data() ?? []).map((proveedor) => ({
      valor: proveedor.id,
      etiqueta: proveedor.nombre,
    })),
  );

  private readonly nombresDeProveedor = computed(
    () => new Map((this.proveedores.data() ?? []).map((p) => [p.id, p.nombre] as const)),
  );

  protected readonly sinAprobar = usarContarBorradoresSinAprobar();
  protected readonly cantidadSinAprobar = computed(() => this.sinAprobar.data() ?? 0);
  private readonly borrarSinAprobar = usarBorrarBorradoresSinAprobar();
  private limpiezaEnVuelo = false;
  protected readonly limpiando = computed(() => this.borrarSinAprobar.isPending());
  protected readonly confirmandoLimpiar = signal(false);
  /** Lo que lleva borrado la limpieza en curso, para el texto del botón ocupado. */
  protected readonly borradosHastaAhora = signal(0);
  protected readonly avisoLimpiar = signal<string | null>(null);
  protected readonly errorLimpiar = signal<string | null>(null);

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly cajaLimpiar = viewChild<ElementRef<HTMLElement>>('cajaLimpiar');
  private readonly botonLimpiar = viewChild('botonLimpiar', { read: ElementRef });
  private readonly avisoLimpiarRef = viewChild<ElementRef<HTMLElement>>('avisoLimpiarRef');

  constructor() {
    // Un `effect` y no una sola asignación: con "atrás" del navegador cambia la URL y la tabla, y
    // los desplegables tienen que seguirla (mismo criterio que la lista de pedidos).
    effect(() => {
      this.estadoFiltro.setValue(this.filtro().estado, { emitEvent: false });
      this.proveedorFiltro.setValue(this.filtro().proveedorId, { emitEvent: false });
    });
  }

  protected preguntarSiLimpiar(): void {
    this.errorLimpiar.set(null);
    this.avisoLimpiar.set(null);
    this.confirmandoLimpiar.set(true);
    // Sin esto, tabular desde el botón salta directo a "Sí, borrar todos" sin pasar por la
    // advertencia de que no hay vuelta atrás (mismo criterio que borrar un borrador).
    this.enfocarDespuesDePintar(() => this.cajaLimpiar()?.nativeElement);
  }

  protected cancelarLimpiar(): void {
    this.confirmandoLimpiar.set(false);
    this.errorLimpiar.set(null);
    this.enfocarDespuesDePintar(() => this.botonLimpiar()?.nativeElement.querySelector('button'));
  }

  /** La caja desaparece con el botón dentro: el foco va al aviso de cuántos se borraron. */
  protected limpiar(): void {
    // Una marca propia y no `limpiando()`: `isPending` no cambia en el mismo tic del `mutate`.
    if (this.limpiezaEnVuelo) {
      return;
    }
    this.limpiezaEnVuelo = true;
    this.errorLimpiar.set(null);
    this.borradosHastaAhora.set(0);
    this.borrarSinAprobar.mutate(
      { alAvanzar: (borrados) => this.borradosHastaAhora.set(borrados) },
      {
        onSettled: () => (this.limpiezaEnVuelo = false),
        onSuccess: (borrados) => {
          // Ninguno: los que quedan los está decidiendo alguien más. No es un éxito que anunciar.
          if (borrados === 0) {
            this.errorLimpiar.set(this.transloco.translate('admin.borradores.limpiar.ninguno'));
            return;
          }
          this.confirmandoLimpiar.set(false);
          this.avisoLimpiar.set(
            this.transloco.translate('admin.borradores.limpiar.hecho', { borrados }),
          );
          this.enfocarDespuesDePintar(() => this.avisoLimpiarRef()?.nativeElement);
        },
        onError: (error: unknown) =>
          this.errorLimpiar.set(
            mensajeDeError(error, this.transloco, 'admin.borradores.limpiar.error'),
          ),
      },
    );
  }

  protected nombreDelProveedor(borrador: Borrador): string {
    return this.nombresDeProveedor().get(borrador.proveedorId) ?? '';
  }

  protected etiquetaEstado(estado: EstadoBorrador): string {
    return this.traducir()('admin.borradores.estados.' + estado);
  }

  protected clasesEstado(estado: EstadoBorrador): string {
    return clasesDeEstadoBorrador(estado);
  }

  protected precio(valor: number | null): string {
    return valor === null ? '-' : formatearPrecio(valor, 'COP', this.transloco.activeLang());
  }

  protected formatearFecha(iso: string): string {
    return iso ? fechaConHora(iso, this.transloco.activeLang()) : '';
  }

  protected filtrarPorEstado(estado: string): void {
    this.navegarA({ ...this.filtro(), estado: estado as EstadoBorrador | '', pagina: 0 });
  }

  protected filtrarPorProveedor(proveedorId: string): void {
    this.navegarA({ ...this.filtro(), proveedorId, pagina: 0 });
  }

  protected irAPagina(pagina: number): void {
    this.navegarA({ ...this.filtro(), pagina });
  }

  private navegarA(filtro: FiltroBorradores): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: queryParamsDesdeFiltroBorradores(filtro),
    });
  }
}
