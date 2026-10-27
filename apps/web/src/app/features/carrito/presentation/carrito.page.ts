import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { usarIdiomaActivo, usarTraductor } from '../../../core/i18n/traductor';
import { formatearPrecio } from '../../../shared/ts-precio/formato-precio';
import { usarFoco } from '../../../shared/foco/foco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsCheckbox } from '../../../shared/ui/checkbox/ts-checkbox';
import { TsEsqueleto } from '../../../shared/ts-esqueleto/ts-esqueleto';
import { TsPrecio } from '../../../shared/ts-precio/ts-precio';
import { CarritoStore } from '../application/carrito.store';
import { LineaCarritoComponent } from './linea-carrito/linea-carrito';

@Component({
  selector: 'app-carrito',
  imports: [TranslocoPipe, TsBoton, TsCheckbox, TsEsqueleto, TsPrecio, LineaCarritoComponent],
  templateUrl: './carrito.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CarritoPage {
  protected readonly store = inject(CarritoStore);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');
  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly traducir = usarTraductor();
  private readonly idioma = usarIdiomaActivo();

  /**
   * Lo último que pasó con una línea, para la región viva. Cambiar la cantidad o quitar una línea
   * no anunciaba nada —ni la cantidad nueva, ni el total, ni que había fallado—, y un fallo se
   * tragaba con `void`.
   */
  protected readonly anuncioLinea = signal<
    | { readonly tipo: 'cantidad'; readonly nombre: string; readonly cantidad: number }
    | { readonly tipo: 'eliminada'; readonly nombre: string }
    | { readonly tipo: 'seleccion' }
    | { readonly tipo: 'error' }
    | null
  >(null);

  /**
   * La foto se resuelve aquí y no en la plantilla. Llamar a `snapshotDeLinea` desde el `@for`
   * parecía inocente y era una lectura de `localStorage` por línea **en cada ciclo de detección**;
   * un `computed` solo se recalcula cuando el carrito cambia de verdad.
   */
  protected readonly lineas = computed(() => {
    const carrito = this.store.consulta.data();
    if (!carrito) {
      return [];
    }
    return carrito.lineas.map((linea) => ({
      linea,
      snapshot: this.store.snapshotDeLinea(linea.varianteId),
      precio: this.store.precioDeLinea(linea.id),
      seVende: this.store.lineaSeVende(linea.id),
      marcada: this.store.estaMarcada(linea.id),
    }));
  });

  /** «2 de 3 productos en este pago»: las vendibles, que son las únicas que tienen casilla. */
  protected readonly seleccion = computed(() => ({
    marcados: this.store.lineasPorPagar().length,
    total: this.lineas().filter((item) => item.seVende).length,
  }));

  /**
   * Se pulsó «Ir a pagar» sin nada que pagar. El botón no se deshabilita —saldría del orden de
   * tabulación sin explicar nada—: se deja vivo y se dice qué falta. Son dos casos con dos textos:
   * con todo desmarcado se pide marcar algo; si nada se vende, no hay casilla que marcar, y pedirlo
   * mandaba a buscar algo que no está.
   */
  protected readonly faltaPorPagar = signal<'nada_marcado' | 'nada_a_la_venta' | null>(null);

  /**
   * El de lo marcado (`CarritoStore.subtotal`), con los precios por línea del servidor; no la suma
   * de los precios guardados al agregar.
   */
  protected readonly total = computed(() => this.store.subtotal());

  /** El tope que manda el servidor con el carrito; `null` mientras el carrito no ha llegado. */
  protected readonly cantidadMaximaPorLinea = computed(
    () => this.store.consulta.data()?.cantidadMaximaPorLinea ?? null,
  );

  /**
   * El ahorro de pagar sin pasarela, ya formateado para ir dentro de la frase; `null` si no hay.
   * Un ahorro y no un segundo total (ADR-0095): con dos precios a la vista, el artículo 26 de la
   * Ley 1480 obliga a cobrar el más bajo.
   */
  protected readonly ahorro = computed(() => {
    const ahorro = this.store.ahorroSinPasarela();
    return ahorro > 0 ? formatearPrecio(ahorro, 'COP', this.idioma()) : null;
  });

  protected cambiarCantidad(lineaId: string, cantidad: number, nombre?: string): void {
    this.store.actualizarCantidad(lineaId, cantidad).then(
      () => this.anuncioLinea.set({ tipo: 'cantidad', nombre: this.nombreDe(nombre), cantidad }),
      () => this.anuncioLinea.set({ tipo: 'error' }),
    );
  }

  /**
   * Al quitar una línea su botón desaparece con ella, y el navegador manda el foco a `<body>`. Se
   * lleva a la línea que ocupa ahora su lugar —la siguiente— o, si era la última, a la anterior; y
   * si el carrito quedó vacío, al título. Si falla, el botón sigue ahí y el foco no se toca.
   */
  protected eliminarLinea(lineaId: string, indice: number, nombre?: string): void {
    this.store.eliminarLinea(lineaId).then(
      () => {
        this.anuncioLinea.set({ tipo: 'eliminada', nombre: this.nombreDe(nombre) });
        this.enfocarDespuesDePintar(() => {
          const lineas = this.anfitrion.nativeElement.querySelectorAll<HTMLElement>('[data-linea]');
          return lineas[indice] ?? lineas[indice - 1] ?? this.titulo()?.nativeElement;
        });
      },
      () => this.anuncioLinea.set({ tipo: 'error' }),
    );
  }

  protected marcar(lineaId: string, marcada: boolean): void {
    this.store.marcar(lineaId, marcada);
    this.alCambiarSeleccion();
  }

  protected marcarTodas(marcadas: boolean): void {
    this.store.marcarTodas(marcadas);
    this.alCambiarSeleccion();
  }

  /** Marcar cambia el total más abajo, y quien usa lector de pantalla no lo ve cambiar. */
  private alCambiarSeleccion(): void {
    this.faltaPorPagar.set(null);
    this.anuncioLinea.set({ tipo: 'seleccion' });
  }

  /** «Ir a pagar» sin nada que pagar: con algo marcado, el botón es un enlace y esto no corre. */
  protected avisarQueFalta(): void {
    this.faltaPorPagar.set(this.store.hayLineasVendibles() ? 'nada_marcado' : 'nada_a_la_venta');
  }

  /** El nombre guardado de la línea, o el genérico si no hay foto guardada (otro dispositivo). */
  private nombreDe(nombre: string | undefined): string {
    return nombre ?? this.traducir()('carrito.producto_sin_foto');
  }
}
