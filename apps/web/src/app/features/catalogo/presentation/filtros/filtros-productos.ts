import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Translation, TranslocoPipe, translateObjectSignal } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../shared/ui/select/ts-select';
import { usarOpcionesFiltro } from '../../application/listar-opciones-filtro.consulta';
import {
  FiltroProductos,
  LINEAS,
  ORDEN_POR_DEFECTO,
  OrdenProductos,
} from '../../domain/filtro-productos.model';
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

const ORDENES: readonly OrdenProductos[] = [
  'RELEVANCIA',
  'PRECIO_ASC',
  'PRECIO_DESC',
  'MAS_RECIENTES',
];

// `Translation` indexa a `any`: se estrecha a string en vez de confiar. El
// diccionario llega vacío mientras el scope perezoso no ha cargado.
function etiquetaDe(diccionario: Translation, clave: string): string {
  const valor = diccionario[clave];
  return typeof valor === 'string' ? valor : '';
}

function datosFormularioDesdeFiltro(filtro: FiltroProductos): ValoresFormularioFiltros {
  return {
    categoria: filtro.categoria ?? '',
    marca: filtro.marca ?? '',
    linea: filtro.linea ?? '',
    precioMin: filtro.precioMin ?? null,
    precioMax: filtro.precioMax ?? null,
    texto: filtro.texto ?? '',
    // Sin `orden` en la URL el backend ordena por relevancia igual, así que el
    // control lo muestra en vez de quedarse en el vacío: `''` no corresponde a
    // ninguna `<option>`, y este es el único select de los filtros sin
    // placeholder que lo cubra.
    orden: filtro.orden ?? ORDEN_POR_DEFECTO,
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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltrosProductos {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly opciones = usarOpcionesFiltro();

  protected readonly form = new FormGroup({
    categoria: new FormControl('', { nonNullable: true }),
    marca: new FormControl('', { nonNullable: true }),
    linea: new FormControl('', { nonNullable: true }),
    precioMin: new FormControl<number | null>(null),
    precioMax: new FormControl<number | null>(null),
    texto: new FormControl('', { nonNullable: true }),
    // Arranca en el orden por defecto, no en vacío: así también `limpiar()`
    // (`form.reset()`, que vuelve al valor inicial del control) deja el select
    // mostrando "Relevancia" en vez de reproducir el blanco.
    orden: new FormControl<string>(ORDEN_POR_DEFECTO, { nonNullable: true }),
  });

  private readonly lineaSeleccionada = toSignal(this.form.controls.linea.valueChanges, {
    initialValue: this.form.controls.linea.value,
  });

  // Diccionarios del scope perezoso `catalogo`, no `transloco.translate()`
  // dentro del computed: ese se evalúa una sola vez, antes de que el JSON del
  // scope llegue por HTTP, deja la clave cruda en pantalla y nunca se
  // recalcula — nada reactivo cambia cuando el scope termina de cargar.
  // `translateObjectSignal` sí se resuscribe a esa carga y al cambio de idioma.
  // La clave va relativa al scope: Transloco le antepone `catalogo.` por
  // `scopes.autoPrefixKeys`, que viene en `true` por defecto.
  private readonly etiquetasLinea = translateObjectSignal('filtros.linea', undefined, {
    scope: 'catalogo',
  });

  private readonly etiquetasOrden = translateObjectSignal('filtros.orden', undefined, {
    scope: 'catalogo',
  });

  protected readonly opcionesLinea = computed<OpcionSelect[]>(() => {
    const etiquetas = this.etiquetasLinea();
    return LINEAS.map((linea) => ({
      valor: linea,
      etiqueta: etiquetaDe(etiquetas, linea.toLowerCase()),
    }));
  });

  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const todas = this.opciones.categorias.data() ?? [];
    const linea = this.lineaSeleccionada();
    const filtradas = linea ? todas.filter((categoria) => categoria.linea === linea) : todas;
    return filtradas.map((categoria) => ({ valor: categoria.slug, etiqueta: categoria.nombre }));
  });

  protected readonly opcionesMarca = computed<OpcionSelect[]>(() =>
    (this.opciones.marcas.data() ?? []).map((marca) => ({
      valor: marca.id,
      etiqueta: marca.nombre,
    })),
  );

  protected readonly opcionesOrden = computed<OpcionSelect[]>(() => {
    const etiquetas = this.etiquetasOrden();
    return ORDENES.map((orden) => ({
      valor: orden,
      etiqueta: etiquetaDe(etiquetas, orden.toLowerCase()),
    }));
  });

  constructor() {
    const queryParams = toSignal(this.route.queryParams, {
      initialValue: this.route.snapshot.queryParams,
    });

    effect(() => {
      const filtro = filtroDesdeQueryParams(queryParams());
      this.form.patchValue(datosFormularioDesdeFiltro(filtro), { emitEvent: false });
    });

    this.form.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((valores) => {
      const filtro = filtroDesdeFormulario(valores as ValoresFormularioFiltros);
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: queryParamsDesdeFiltro(filtro),
      });
    });
  }

  protected limpiar(): void {
    this.form.reset(undefined, { emitEvent: false });
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
}
