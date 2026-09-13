import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { formatearPrecio } from './formato-precio';

/**
 * docs/05-i18n.md: la moneda no se convierte, solo cambia el formato por
 * idioma — "$ 189.900" en español, "COP 189,900" en inglés.
 *
 * Se queda en `shared/` y no baja a `shared/ui/`: lo usan ocho pantallas de
 * cuatro funcionalidades distintas —catálogo, carrito, checkout y admin—, así
 * que compartido lo es de verdad, y necesita saber el idioma activo para
 * formatear. Eso último es un uso legítimo de Transloco, no una dependencia de
 * negocio.
 *
 * **El prefijo llega ya traducido, y eso arregla un fallo latente.** Antes era
 * un `desde: boolean` y el componente resolvía `'catalogo.precio_desde'` por su
 * cuenta — una clave que vive en el scope **perezoso** del catálogo. Mientras
 * solo lo usara el catálogo funcionaba; el día que una pantalla del checkout
 * pasara `desde=true`, habría pintado la clave cruda, porque ese scope no está
 * cargado ahí (apps/web/CLAUDE.md, "i18n con scopes perezosos"). Ahora la clave
 * la resuelve quien llama, donde su scope sí existe.
 */
@Component({
  selector: 'ts-precio',
  templateUrl: './ts-precio.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // `tabular-nums` en el host y no en un `<span>` interno: es lo que hace que
  // una columna de precios alinee sola (docs/04-ui-marca.md).
  //
  // `flex-wrap`, porque el prefijo y la cifra son dos cajas: en la rejilla a
  // dos columnas de un teléfono (390 px) la tarjeta deja ~150 px al contenido,
  // y "Desde $ 1.299.900" mide más. Sin envolver, el `overflow-hidden` de la
  // tarjeta recortaba la cifra —se leía "$ 1.299.90"— que es lo peor que le
  // puede pasar a un precio. Envuelto, "Desde" baja de línea y la cifra se
  // lee entera; donde cabe, no cambia nada.
  host: { class: 'inline-flex flex-wrap items-baseline gap-4 font-mono tabular-nums' },
})
export class TsPrecio {
  private readonly transloco = inject(TranslocoService);

  readonly valor = input.required<number>();
  readonly moneda = input('COP');

  /** Ya traducido por quien llama, p. ej. "Desde". `null` para no mostrarlo. */
  readonly prefijo = input<string | null>(null);

  protected readonly formateado = computed(() =>
    formatearPrecio(this.valor(), this.moneda(), this.transloco.activeLang()),
  );
}
