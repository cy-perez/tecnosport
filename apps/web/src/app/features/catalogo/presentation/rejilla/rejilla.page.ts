import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { usarFoco } from '../../../../shared/foco/foco';
import { usarTraductor } from '../../../../core/i18n/traductor';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsTarjetaProducto } from '../tarjeta-producto/ts-tarjeta-producto';
import { usarBusquedaProductos } from '../../application/buscar-productos.consulta';
import { hayFiltrosActivos } from '../../domain/filtro-productos.model';
import { filtroDesdeQueryParams } from '../../domain/query-params-filtro';
import { FiltrosProductos } from '../filtros/filtros-productos';

@Component({
  selector: 'app-rejilla',
  imports: [TranslocoPipe, TsTarjetaProducto, TsEsqueleto, TsBoton, FiltrosProductos],
  templateUrl: './rejilla.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RejillaPage {
  /** Una fila en escritorio: las candidatas reales a LCP. Ver la plantilla. */
  protected readonly TARJETAS_PRIORITARIAS = 4;

  private readonly route = inject(ActivatedRoute);

  private readonly queryParams = toSignal(this.route.queryParams, {
    initialValue: this.route.snapshot.queryParams,
  });

  private readonly filtro = computed(() => filtroDesdeQueryParams(this.queryParams()));

  protected readonly consulta = usarBusquedaProductos(this.filtro);

  protected readonly hayFiltros = computed(() => hayFiltrosActivos(this.filtro()));

  protected readonly productos = computed(
    () => this.consulta.data()?.pages.flatMap((pagina) => pagina.items) ?? [],
  );

  protected readonly marcadoresDeCarga = [1, 2, 3, 4];

  private readonly traducir = usarTraductor();
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly enfocarDespuesDePintar = usarFoco();

  /** Cuántos trajo el último «Cargar más», o `null` si lo último que pasó fue filtrar. */
  private readonly masCargados = signal<number | null>(null);

  /**
   * El texto de la región viva. Filtrar cambiaba la rejilla sin decir nada, y «Cargar más» igual:
   * quien no la ve no sabía si el filtro había dejado tres productos o ninguno. Solo cambia cuando
   * cambia la cantidad, así que una revalidación en segundo plano que trae lo mismo no se vuelve a
   * leer en voz alta.
   */
  protected readonly anuncio = computed(() => {
    const traducir = this.traducir();
    if (this.consulta.isPending() || this.consulta.isError()) {
      return '';
    }
    const mas = this.masCargados();
    // Uno y varios son dos textos, no una interpolación: "1 productos" es lo que sale de fingir
    // que el plural es siempre.
    if (mas !== null) {
      return mas === 1
        ? traducir('catalogo.anuncio_mas_uno')
        : traducir('catalogo.anuncio_mas', { cantidad: mas });
    }
    const total = this.productos().length;
    if (total === 0) {
      // Un texto propio y no el del párrafo visible: repetido, la página tendría dos veces la
      // misma frase y el lector la leería dos veces al recorrerla.
      return traducir('catalogo.anuncio_ninguno');
    }
    return total === 1
      ? traducir('catalogo.anuncio_resultados_uno')
      : traducir('catalogo.anuncio_resultados', { cantidad: total });
  });

  constructor() {
    // Otro filtro, otro anuncio: «12 productos más» ya no habla de esta lista.
    effect(() => {
      this.filtro();
      untracked(() => this.masCargados.set(null));
    });
  }

  /**
   * Trae la página siguiente, anuncia cuántos llegaron y lleva el foco a la primera tarjeta nueva.
   * Sin eso, quien navega con teclado seguía en el botón —que baja con la rejilla o desaparece en la
   * última página— y tenía que volver a recorrer todo lo que ya había visto.
   */
  protected async cargarMas(): Promise<void> {
    if (this.consulta.isFetchingNextPage()) {
      return;
    }
    const antes = this.productos().length;
    // Se cuenta sobre lo que devuelve la consulta y no sobre la señal: la señal se pone al día un
    // instante después de que la promesa resuelva.
    const resultado = await this.consulta.fetchNextPage();
    const ahora =
      resultado.data?.pages.reduce((suma, pagina) => suma + pagina.items.length, 0) ?? antes;
    if (ahora <= antes) {
      return;
    }
    this.masCargados.set(ahora - antes);
    this.enfocarDespuesDePintar(() =>
      this.anfitrion.nativeElement
        .querySelectorAll('ts-tarjeta-producto')
        [antes]?.querySelector<HTMLElement>('a'),
    );
  }
}
