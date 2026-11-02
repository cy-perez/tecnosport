import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Translation, TranslocoPipe, translateObjectSignal } from '@jsverse/transloco';
import { debounceTime } from 'rxjs';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { iconoBuscar, iconoOrden } from '../../../../shared/ui/icono/iconos';
import { OpcionSelect, TsSelect } from '../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../shared/ui/select/ts-select-control';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { usarOpcionesFiltro } from '../../application/listar-opciones-filtro.consulta';
import { escalaDeTallasDe, hojasConRuta } from '../../domain/arbol-categorias';
import {
  FiltroProductos,
  hayFiltrosActivos,
  claveDeLinea,
  LINEAS,
  ORDEN_POR_DEFECTO,
  OrdenProductos,
} from '../../domain/filtro-productos.model';
import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from '../../domain/query-params-filtro';
import {
  lineasDeTallaPosibles,
  OpcionDeTalla,
  ordenarTallas,
  VALOR_TALLA_UNICA,
} from '../../domain/tallas.model';

interface ValoresFormularioFiltros {
  categoria: string;
  marca: string;
  linea: string;
  texto: string;
  tallaRopa: string;
  tallaCalzado: string;
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
    texto: filtro.texto ?? '',
    tallaRopa: filtro.tallaRopa ?? '',
    tallaCalzado: filtro.tallaCalzado ?? '',
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
    texto: valores.texto || undefined,
    tallaRopa: valores.tallaRopa || undefined,
    tallaCalzado: valores.tallaCalzado || undefined,
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

  protected readonly iconoBuscar = iconoBuscar;
  protected readonly iconoOrden = iconoOrden;

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
    texto: new FormControl('', { nonNullable: true }),
    tallaRopa: new FormControl('', { nonNullable: true }),
    tallaCalzado: new FormControl('', { nonNullable: true }),
    // Arranca en el orden por defecto, no en vacío: así también `limpiar()`
    // (`form.reset()`, que vuelve al valor inicial del control) deja el select
    // mostrando "Relevancia" en vez de reproducir el blanco.
    orden: new FormControl<string>(ORDEN_POR_DEFECTO, { nonNullable: true }),
  });

  /**
   * La línea que acota los otros dos desplegables.
   *
   * <b>Era `toSignal(this.form.controls.linea.valueChanges)`, y así no acotaba nada al llegar
   * desde un botón de la portada.</b> El efecto que vuelca la URL en el formulario usa
   * `patchValue(..., { emitEvent: false })` —y tiene que usarlo, o cada navegación dispararía la
   * siguiente—, así que ese observable solo emite cuando alguien toca el control con el ratón.
   * Entrando por `/productos?linea=CALZADO` la señal se quedaba en cadena vacía y el desplegable
   * de categorías ofrecía las treinta y una de las cuatro líneas.
   *
   * Ahora es una señal escribible que actualizan los dos caminos que de verdad cambian la línea:
   * el efecto de la URL y la suscripción al control. La del control va **sin** el `debounceTime`
   * de la navegación: los desplegables se reacomodan en el momento, no 300 ms después.
   */
  private readonly lineaSeleccionada = signal(
    filtroDesdeQueryParams(this.route.snapshot.queryParams).linea ?? '',
  );

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

  private readonly etiquetasTalla = translateObjectSignal('talla', undefined, {
    scope: 'catalogo',
  });

  /**
   * Las tallas con existencia de ropa, en el orden de la escala y no en el alfabético que trae el
   * servidor. Las de calzado igual, con las suyas.
   */
  protected readonly opcionesTallaRopa = computed<OpcionSelect[]>(() =>
    this.opcionesDeTalla('ROPA', this.opciones.tallas.data()?.ropa ?? [], this.tallaRopaElegida()),
  );

  protected readonly opcionesTallaCalzado = computed<OpcionSelect[]>(() =>
    this.opcionesDeTalla(
      'CALZADO',
      this.opciones.tallas.data()?.calzado ?? [],
      this.tallaCalzadoElegida(),
    ),
  );

  /** La talla de la URL, para no perderla de vista si ya no está entre las que tienen existencia. */
  private readonly tallaRopaElegida = signal(
    filtroDesdeQueryParams(this.route.snapshot.queryParams).tallaRopa ?? '',
  );

  private readonly tallaCalzadoElegida = signal(
    filtroDesdeQueryParams(this.route.snapshot.queryParams).tallaCalzado ?? '',
  );

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

  /**
   * Solo las marcas que tienen algo publicado <b>en la línea que se está mirando</b>.
   *
   * Esto no se podía hacer en el navegador hasta el 6 de octubre de 2026: la respuesta de
   * `/api/v1/marcas` traía el id y el nombre, y una marca no tiene línea —la tiene la categoría de
   * cada uno de sus productos—. Ahora viaja la lista de líneas con cada marca y el filtro aplica
   * el mismo criterio que ya aplicaba a las categorías: ofrecer una marca que en esta línea no
   * tiene nada es mandar a quien compra a una rejilla vacía.
   */
  protected readonly opcionesMarca = computed<OpcionSelect[]>(() => {
    const linea = this.lineaSeleccionada();
    return (this.opciones.marcas.data() ?? [])
      .filter((marca) => !linea || marca.lineas.includes(linea))
      .map((marca) => ({ valor: marca.id, etiqueta: marca.nombre }));
  });

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
      this.lineaSeleccionada.set(filtro.linea ?? '');
      this.tallaRopaElegida.set(filtro.tallaRopa ?? '');
      this.tallaCalzadoElegida.set(filtro.tallaCalzado ?? '');
      untracked(() => this.ajustarTallasYCorregirLaUrl());
    });

    // Las categorías y las marcas llegan después del primer render, y hasta entonces no se sabe de
    // qué línea son las que trae la URL: al llegar, se vuelve a mirar qué talla cabe.
    effect(() => {
      this.opciones.categorias.data();
      this.opciones.marcas.data();
      untracked(() => this.ajustarTallasYCorregirLaUrl());
    });

    // Sin `debounceTime`, como la línea: el desplegable de talla se habilita o se apaga en el
    // momento, y la navegación que ya viene en camino se lleva la talla limpia.
    this.form.controls.categoria.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.ajustarTallas());
    this.form.controls.marca.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.ajustarTallas());

    // Una talla de ropa y una de calzado a la vez no tienen un solo resultado —cada una deja solo
    // lo de su línea—, así que elegir una suelta la otra. Sin evento, por lo mismo que
    // `alCambiarDeLinea`: la navegación la dispara el cambio que hizo quien eligió.
    this.form.controls.tallaRopa.valueChanges.pipe(takeUntilDestroyed()).subscribe((talla) => {
      if (talla) {
        this.form.controls.tallaCalzado.setValue('', { emitEvent: false });
      }
    });
    this.form.controls.tallaCalzado.valueChanges.pipe(takeUntilDestroyed()).subscribe((talla) => {
      if (talla) {
        this.form.controls.tallaRopa.setValue('', { emitEvent: false });
      }
    });

    // Sin `debounceTime`: esta no navega, solo reacomoda los otros dos desplegables, y hacerlo
    // 300 ms después del clic se ve como un salto.
    this.form.controls.linea.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((linea) => this.alCambiarDeLinea(linea));

    // El valor se lee del formulario y no de lo que emitió `valueChanges`, y la diferencia
    // importa: `alCambiarDeLinea` puede haber limpiado la categoría o la marca en el intervalo, y
    // la emisión es una foto de antes de esa limpieza. Navegar con ella reescribiría en la URL
    // justo el filtro que se acaba de quitar.
    this.form.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      const filtro = filtroDesdeFormulario(this.form.getRawValue());
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: queryParamsDesdeFiltro(filtro),
      });
    });
  }

  /**
   * Al cambiar de línea, lo que ya no pertenece a ella se cae.
   *
   * Sin esto el formulario queda con una categoría que no está entre sus opciones —el `<select>`
   * se pinta en blanco, sin nada que quitar— y la rejilla sale vacía: la URL pide calzado y una
   * categoría de tecnología a la vez, que es una combinación sin resultados. Es la misma trampa
   * que `FiltroProductos` describe para el rango de precio que se evaporaba solo.
   *
   * <b>Solo se limpia lo que consta que no pertenece.</b> Mientras las listas no hayan cargado no
   * se sabe, y borrar por no saber perdería el filtro que traía la URL.
   */
  private alCambiarDeLinea(linea: string): void {
    this.lineaSeleccionada.set(linea);
    if (!linea) {
      // Volver a todas las líneas no quita nada, pero puede devolver una talla que estaba apagada.
      this.ajustarTallas();
      return;
    }

    const categoria = (this.opciones.categorias.data() ?? []).find(
      (opcion) => opcion.slug === this.form.controls.categoria.value,
    );
    if (categoria && categoria.linea !== linea) {
      this.form.controls.categoria.setValue('', { emitEvent: false });
    }

    const marca = (this.opciones.marcas.data() ?? []).find(
      (opcion) => opcion.id === this.form.controls.marca.value,
    );
    if (marca && !marca.lineas.includes(linea)) {
      this.form.controls.marca.setValue('', { emitEvent: false });
    }

    this.ajustarTallas();
  }

  /**
   * Habilita el desplegable de talla que cabe con la línea, la categoría y la marca elegidas, y
   * apaga y vacía el que no: con una categoría o una marca de ropa, la talla de calzado; con una
   * de calzado, la de ropa; con tecnología o bolsos, las dos (`lineasDeTallaPosibles`).
   *
   * Apagado y no escondido: el control sigue ahí diciendo que existe, y la fila no se reacomoda
   * cada vez que se elige otra cosa.
   *
   * La categoría y la marca solo acotan cuando consta de qué línea son —sus listas ya cargaron—,
   * por lo mismo que `alCambiarDeLinea`. Devuelve si tuvo que vaciar alguna talla.
   */
  private ajustarTallas(): boolean {
    const { linea, categoria, marca } = this.form.getRawValue();
    const posibles = lineasDeTallaPosibles({
      linea: linea || undefined,
      lineaDeCategoria: (this.opciones.categorias.data() ?? []).find((c) => c.slug === categoria)
        ?.linea,
      lineasDeMarca: (this.opciones.marcas.data() ?? []).find((m) => m.id === marca)?.lineas,
    });

    let vacio = false;
    for (const [control, lineaDeTalla] of [
      [this.form.controls.tallaRopa, 'ROPA'],
      [this.form.controls.tallaCalzado, 'CALZADO'],
    ] as const) {
      if (posibles.has(lineaDeTalla)) {
        control.enable({ emitEvent: false });
        continue;
      }
      if (control.value) {
        control.setValue('', { emitEvent: false });
        vacio = true;
      }
      control.disable({ emitEvent: false });
    }
    return vacio;
  }

  /**
   * Lo mismo cuando quien cambió fue la URL y no una persona: si una talla no cabe —una URL
   * compartida con una marca de ropa y una talla de calzado—, vaciarla solo en el formulario
   * dejaría la rejilla filtrada por una talla que ya no se ve. Se navega sin ella.
   */
  private ajustarTallasYCorregirLaUrl(): void {
    if (this.ajustarTallas()) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: queryParamsDesdeFiltro(filtroDesdeFormulario(this.form.getRawValue())),
      });
    }
  }

  /**
   * Las opciones de un desplegable de talla.
   *
   * La talla única se lee con su nombre traducido —«Talla única», «One size»— y no con la
   * escritura del catálogo, que es la de la ficha en español. Y la talla que traía la URL se
   * conserva aunque ya no tenga existencia: si se cayera de la lista, el `<select>` se pintaría en
   * blanco con el filtro aplicado y nada que quitar — la misma trampa que `alCambiarDeLinea`
   * describe para la categoría.
   */
  private opcionesDeTalla(
    linea: 'ROPA' | 'CALZADO',
    tallas: readonly OpcionDeTalla[],
    elegida: string,
  ): OpcionSelect[] {
    const todas = this.opciones.categorias.data() ?? [];
    const escalas = todas
      .filter((categoria) => categoria.linea === linea)
      .map((categoria) => escalaDeTallasDe(categoria, todas));
    const conLaElegida =
      elegida && !tallas.some((talla) => talla.valor === elegida)
        ? [...tallas, { valor: elegida, etiqueta: elegida }]
        : tallas;
    const unica = etiquetaDe(this.etiquetasTalla(), 'unica');
    return ordenarTallas(conLaElegida, escalas).map((talla) => ({
      valor: talla.valor,
      etiqueta: talla.valor === VALOR_TALLA_UNICA && unica ? unica : talla.etiqueta,
    }));
  }

  protected alternar(): void {
    this.abierto.update((abierto) => !abierto);
  }

  protected limpiar(): void {
    this.form.reset(undefined, { emitEvent: false });
    this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }
}
