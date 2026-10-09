import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { TsEsqueleto } from '../../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { clasesDeEstadoBorrador } from '../../../borradores/presentation/estado-borrador';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarProveedoresAdmin } from '../../../proveedores/application/listar-proveedores.consulta';
import { usarListarBorradoresTecnologia } from '../../application/borradores-tecnologia.consulta';
import {
  BorradorTecnologia,
  ESTADOS_BORRADOR_TECNOLOGIA,
  EstadoBorradorTecnologia,
} from '../../domain/borrador-tecnologia.model';

/** Sin `?estado` se ven los que esperan revisión: es lo que se viene a hacer aquí. */
function estadoDesde(valor: unknown): EstadoBorradorTecnologia {
  return ESTADOS_BORRADOR_TECNOLOGIA.includes(valor as EstadoBorradorTecnologia)
    ? (valor as EstadoBorradorTecnologia)
    : 'EN_REVISION';
}

/**
 * Los modelos de tecnología que trajo la lista del proveedor y nadie ha decidido todavía. El estado
 * vive en la URL; cada fila lleva a su revisión.
 */
@Component({
  selector: 'app-lista-tecnologia-admin',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsEsqueleto,
    TsMigas,
    TsSelect,
    TsSelectControl,
  ],
  templateUrl: './lista-tecnologia-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaTecnologiaAdminPage {
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.tecnologia.titulo' }]);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });
  protected readonly estado = computed(() => estadoDesde(this.queryParams()['estado']));

  protected readonly proveedores = usarProveedoresAdmin();
  protected readonly consulta = usarListarBorradoresTecnologia(this.estado);
  protected readonly borradores = computed<readonly BorradorTecnologia[]>(
    () => this.consulta.data() ?? [],
  );

  protected readonly estadoFiltro = new FormControl<string>('EN_REVISION', { nonNullable: true });

  protected readonly opcionesEstado = computed<OpcionSelect[]>(() =>
    ESTADOS_BORRADOR_TECNOLOGIA.map((estado) => ({
      valor: estado,
      etiqueta: this.traducir()('admin.borradores.estados.' + estado),
    })),
  );

  private readonly nombresDeProveedor = computed(
    () => new Map((this.proveedores.data() ?? []).map((p) => [p.id, p.nombre] as const)),
  );

  constructor() {
    // Con "atrás" del navegador cambia la URL y el desplegable tiene que seguirla.
    effect(() => this.estadoFiltro.setValue(this.estado(), { emitEvent: false }));
  }

  protected nombreDelProveedor(borrador: BorradorTecnologia): string {
    return this.nombresDeProveedor().get(borrador.proveedorId) ?? '';
  }

  protected etiquetaEstado(estado: EstadoBorradorTecnologia): string {
    return this.traducir()('admin.borradores.estados.' + estado);
  }

  protected clasesEstado(estado: EstadoBorradorTecnologia): string {
    return clasesDeEstadoBorrador(estado);
  }

  protected formatearFecha(iso: string): string {
    return iso ? fechaConHora(iso, this.transloco.activeLang()) : '';
  }

  protected filtrarPorEstado(estado: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: estado === 'EN_REVISION' ? {} : { estado },
    });
  }
}
