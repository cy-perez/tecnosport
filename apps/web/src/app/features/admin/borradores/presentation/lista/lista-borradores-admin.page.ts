import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { formatearPrecio } from '../../../../../shared/ts-precio/formato-precio';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsPaginador } from '../../../../../shared/ts-paginador/ts-paginador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
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

  constructor() {
    // Un `effect` y no una sola asignación: con "atrás" del navegador cambia la URL y la tabla, y
    // los desplegables tienen que seguirla (mismo criterio que la lista de pedidos).
    effect(() => {
      this.estadoFiltro.setValue(this.filtro().estado, { emitEvent: false });
      this.proveedorFiltro.setValue(this.filtro().proveedorId, { emitEvent: false });
    });
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
    return valor === null ? '—' : formatearPrecio(valor, 'COP', this.transloco.activeLang());
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
