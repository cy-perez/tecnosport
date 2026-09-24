import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Translation, TranslocoPipe, translateObjectSignal } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { usarOpcionesFiltro } from '../../application/listar-opciones-filtro.consulta';
import { hojasConRuta } from '../../domain/arbol-categorias';
import {
  FiltroProductos,
  hayFiltrosActivos,
  claveDeLinea,
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
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsSelect, TsSelectControl],
  templateUrl: './filtros-productos.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltrosProductos {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly opciones = usarOpcionesFiltro();

  /**
   * Solo cuenta por debajo del primer punto de quiebre, donde el formulario va
   * tras un botón (ver la plantilla). Arranca abierto si la URL ya trae algún
   * filtro, para que lo aplicado se vea; el orden solo no cuenta como filtro.
   * Un cambio de filtro no lo pliega: quien está afinando la búsqueda suele
   * tocar más de un control seguido.
   */
  protected readonly abierto = signal(
    hayFiltrosActivos(filtroDesdeQueryParams(this.route.snapshot.queryParams)),
  );

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
  // Los nombres de línea viven en el diccionario **raíz** desde que los leen además el menú
  // lateral, la pantalla de categorías del panel y los desplegables de crear y editar producto:
  // ninguno de esos tres carga el scope de catálogo, así que los tres pintaban la clave cruda.
  //
  // Y se leen con `usarTraductor` y no con `translateObjectSignal`, que es lo que hacían: ese
  // antepone el scope activo a la clave (`scopes.autoPrefixKeys`), así que una clave de la raíz
  // leída desde este componente se convertía en `catalogo.lineas` y no existía.
  private readonly traducir = usarTraductor();

  private readonly etiquetasOrden = translateObjectSignal('filtros.orden', undefined, {
    scope: 'catalogo',
  });

  /**
   * Las cuatro líneas del modelo, siempre.
   *
   * <b>Esto afirmaba lo contrario hasta el 24 de septiembre de 2026</b>: se deducían de las
   * categorías que el servidor devolvía, que entonces eran solo las que tenían algo publicado
   * detrás, para no ofrecer un filtro que lleva a una rejilla en blanco. El endpoint dejó de
   * esconderlas al llegar el árbol —lo razona `ListarCategorias`—, así que aquel cálculo ya no
   * filtra nada y solo queda el efecto de fondo: <b>el filtro tiene que ofrecer lo mismo que el
   * menú</b>. Un menú que enseña "Calzado deportivo" y un desplegable que no lo tiene son dos
   * respuestas distintas a la misma pregunta en la misma pantalla.
   *
   * Se recorre `LINEAS` para conservar el orden canónico del modelo, que es el del negocio y no el
   * alfabético — el mismo en el que el menú pinta las ramas.
   */
  protected readonly opcionesLinea = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    return LINEAS.map((linea) => ({ valor: linea, etiqueta: traducir(claveDeLinea(linea)) }));
  });

  /**
   * Solo las <b>hojas</b>, con la ruta completa como etiqueta.
   *
   * Las dos cosas son consecuencia del árbol. Las hojas, porque de una rama no cuelga ningún
   * producto —lo defiende el backend—, así que filtrar por "Dama" daría siempre una rejilla vacía.
   * Y la ruta, porque sin ella el desplegable tiene entradas que no se distinguen: "Busos" sale dos
   * veces, una por Dama y otra por Caballero, y "Dama" tres veces, una por línea.
   */
  protected readonly opcionesCategoria = computed<OpcionSelect[]>(() => {
    const traducir = this.traducir();
    const todas = this.opciones.categorias.data() ?? [];
    const linea = this.lineaSeleccionada();
    const hojas = hojasConRuta(todas, (valor) => traducir(claveDeLinea(valor)));
    return hojas
      .filter((hoja) => !linea || hoja.categoria.linea === linea)
      .map((hoja) => ({ valor: hoja.categoria.slug, etiqueta: hoja.ruta }));
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

  protected alternar(): void {
    this.abierto.update((abierto) => !abierto);
  }

  protected limpiar(): void {
    this.form.reset(undefined, { emitEvent: false });
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
}
