import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';
import { TsBoton } from '../../../../shared/ts-boton/ts-boton';
import { TsCampo } from '../../../../shared/ts-campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../shared/ts-select/ts-select';
import { usarOpcionesFiltro } from '../../application/listar-opciones-filtro.consulta';
import { FiltroProductos, OrdenProductos } from '../../domain/filtro-productos.model';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';

interface ValoresFormularioFiltros {
  categoria: string;
  marca: string;
  linea: string;
  precioMin: number | null;
  precioMax: number | null;
  texto: string;
  orden: string;
}

const LINEAS = ['ROPA_Y_CALZADO', 'BOLSOS', 'CELULARES'] as const;
const ORDENES: readonly OrdenProductos[] = ['RELEVANCIA', 'PRECIO_ASC', 'PRECIO_DESC', 'MAS_RECIENTES'];

function datosFormularioDesdeFiltro(filtro: FiltroProductos): ValoresFormularioFiltros {
  return {
    categoria: filtro.categoria ?? '',
    marca: filtro.marca ?? '',
    linea: filtro.linea ?? '',
    precioMin: filtro.precioMin ?? null,
    precioMax: filtro.precioMax ?? null,
    texto: filtro.texto ?? '',
    orden: filtro.orden ?? '',
  };
}

function filtroDesdeFormulario(valores: ValoresFormularioFiltros): FiltroProductos {
  return {
    categoria: valores.categoria || undefined,
    marca: valores.marca || undefined,
    linea: valores.linea || undefined,
    precioMin: valores.precioMin ?? undefined,
    precioMax: valores.precioMax ?? undefined,
    texto: valores.texto || undefined,
    orden: (valores.orden || undefined) as OrdenProductos | undefined,
  };
}

/**
 * Los filtros viven en la URL, no en estado del componente: compartibles,
 * sobreviven un refresh, y el resolver de la ruta puede seguir precargando
 * exactamente la página que se va a renderizar (docs/05-i18n.md: mismo
 * criterio que el selector de idioma, que navega en vez de solo cambiar
 * estado local).
 */
@Component({
  selector: 'app-filtros-productos',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsSelect],
  templateUrl: './filtros-productos.html',
  styleUrl: './filtros-productos.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltrosProductos {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  protected readonly opciones = usarOpcionesFiltro();

  protected readonly form = new FormGroup({
    categoria: new FormControl('', { nonNullable: true }),
    marca: new FormControl('', { nonNullable: true }),
    linea: new FormControl('', { nonNullable: true }),
    precioMin: new FormControl<number | null>(null),
    precioMax: new FormControl<number | null>(null),
    texto: new FormControl('', { nonNullable: true }),
    orden: new FormControl('', { nonNullable: true }),
  });

  private readonly lineaSeleccionada = toSignal(this.form.controls.linea.valueChanges, {
    initialValue: this.form.controls.linea.value,
  });

  protected readonly opcionesLinea = computed<OpcionSelect[]>(() =>
    LINEAS.map((linea) => ({
      valor: linea,
      etiqueta: this.transloco.translate(`catalogo.filtros.linea.${linea.toLowerCase()}`),
    })),
  );

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const todas = this.opciones.categorias.data() ?? [];
    const linea = this.lineaSeleccionada();
    const filtradas = linea ? todas.filter((categoria) => categoria.linea === linea) : todas;
    return filtradas.map((categoria) => ({ valor: categoria.slug, etiqueta: categoria.nombre }));
  });

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({ valor: marca.id, etiqueta: marca.nombre })),
  );

  protected readonly opcionesOrden = computed<OpcionSelect[]>(() =>
    ORDENES.map((orden) => ({
      valor: orden,
      etiqueta: this.transloco.translate(`catalogo.filtros.orden.${orden.toLowerCase()}`),
    })),
  );

  constructor() {
    const queryParams = toSignal(this.route.queryParams, { initialValue: this.route.snapshot.queryParams });

    effect(() => {
      const filtro = filtroDesdeQueryParams(queryParams());
      this.form.patchValue(datosFormularioDesdeFiltro(filtro), { emitEvent: false });
    });

    this.form.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((valores) => {
      const filtro = filtroDesdeFormulario(valores as ValoresFormularioFiltros);
      this.router.navigate([], { relativeTo: this.route, queryParams: queryParamsDesdeFiltro(filtro) });
    });
  }

  protected limpiar(): void {
    this.form.reset(undefined, { emitEvent: false });
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
}
