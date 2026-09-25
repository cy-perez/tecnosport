import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { cn } from '../cn';

export type VarianteBoton = 'primario' | 'secundario' | 'texto' | 'peligro' | 'acento';

/**
 * Estilos por variante. Fuera de la clase a propósito: son datos, no estado,
 * y así se leen de un vistazo contra `docs/04-ui-marca.md`.
 *
 * `not-disabled:` y no `hover:` a secas: sin esa guarda, el hover de la
 * variante y el `disabled:` de la base compiten con la misma especificidad y
 * gana el que el compilador haya puesto último — un botón deshabilitado que
 * se aclara al pasar el ratón. El SCSS lo resolvía con `&:hover:not(:disabled)`.
 */
const VARIANTES: Record<VarianteBoton, string> = {
  primario:
    'min-h-tactil bg-ts-primario text-ts-sobre-primario not-disabled:hover:bg-ts-primario-hover not-disabled:active:bg-ts-primario-pressed',
  secundario:
    'min-h-tactil bg-transparent text-ts-primario border-ts-borde-control not-disabled:hover:bg-ts-superficie-alt',
  // `min-h-tactil` también aquí, y esto **revierte** lo que hacía el SCSS.
  // El SCSS ponía el mínimo en la base y lo anulaba con `min-height: auto` en
  // esta variante; al traducirlo se conservó la exención. Medido en el
  // navegador a 380 px, el resultado era "Limpiar filtros" en 126 x 37 y
  // "Eliminar" del carrito en 85 x 37 — por debajo de los 44 px que
  // `docs/04-ui-marca.md` exige **sin distinguir variantes**. Un botón de texto
  // se pulsa igual que uno con fondo; que no pinte relleno no lo hace más
  // fácil de acertar con el pulgar. Las cuatro variantes lo llevan ahora.
  texto: 'min-h-tactil bg-transparent text-ts-primario px-12 py-8 not-disabled:hover:underline',
  peligro: 'min-h-tactil bg-ts-error text-ts-sobre-primario not-disabled:hover:brightness-110',
  // El ámbar de marca como relleno, con grafito encima — la única forma en que
  // `docs/04-ui-marca.md` admite este color. Existe como variante y no como un
  // `clase="bg-ts-acento"` de quien llama por el anillo de foco: en tema oscuro
  // `--color-foco` y `--color-acento` son los dos `#F5B301`, así que el anillo
  // que trae `BASE` sería invisible justo sobre este fondo. Aquí se cambia a
  // `--color-sobre-acento`, que es grafito en los dos temas; `cn` descarta el
  // de `BASE` porque `tailwind-merge` agrupa `outline-*` por prefijo.
  // Y sigue rigiendo "una sola cosa por pantalla": esta variante es para *la*
  // acción de la pantalla, no para repartir ámbar por ella.
  acento:
    'min-h-tactil bg-ts-acento text-ts-sobre-acento focus-visible:outline-ts-sobre-acento ' +
    'not-disabled:hover:bg-ts-acento-hover not-disabled:active:bg-ts-acento-pressed',
};

/**
 * El mínimo táctil de 44 px vive en cada variante y no en la base, aunque las
 * cuatro lo repitan. El SCSS lo ponía en la base y lo anulaba con
 * `min-height: auto` en la variante `texto`, pero ese truco no se puede
 * traducir: al borrar la escala de espacio por omisión (`src/tailwind.css`),
 * ni `min-h-0` ni `min-h-auto` existen, y una clase que no existe no falla —
 * simplemente no hace nada. Verificado leyendo el CSS compilado, que es la
 * única forma de detectarlo. Repetir tres veces es peor que una clase
 * fantasma solo en apariencia.
 *
 * `chaflan` viene de `tokens.css`, no de Tailwind: es la firma de la marca
 * (corte a 45 grados en dos esquinas) y ya resuelve por su cuenta el detalle
 * de que `clip-path` recorta el anillo de foco, desactivándose en
 * `:focus-visible`. Ninguna utilidad de Tailwind puede reproducir eso.
 */
/**
 * `border border-transparent` en la base, y no `border-0`: las cuatro
 * variantes llevan el mismo borde de 1 px, pintado o no. Con `border-0` en la
 * base y `border` solo en `secundario`, un botón primario medía 44 px y uno
 * secundario 46 — medido en el navegador en el selector de variante de la
 * ficha, donde la opción elegida (primaria) y las demás (secundarias) van en
 * la misma fila y la diferencia se veía como un escalón. El color lo pone
 * cada variante; el ancho es de todas.
 */
const BASE =
  'inline-flex items-center justify-center gap-8 py-12 px-24 border border-transparent ' +
  // `no-underline` es por la rama de enlace y no sobra: sin Preflight
  // (`src/tailwind.css`) un `<a>` conserva el subrayado del navegador, y
  // "Ir a pagar" salió subrayado dentro de su fondo ámbar la primera vez que
  // se miró en el navegador. En un `<button>` no hace nada, que es el precio
  // correcto por tenerlo en un solo sitio. La variante `texto` subraya al
  // pasar el ratón y sigue funcionando: `hover:` gana por orden de capa.
  'font-texto font-medio text-base cursor-pointer chaflan no-underline ' +
  'focus-visible:outline-2 focus-visible:outline-ts-foco focus-visible:outline-offset-2 ' +
  'disabled:bg-ts-deshabilitado disabled:text-ts-sobre-deshabilitado ' +
  'disabled:border-ts-deshabilitado disabled:cursor-not-allowed';

@Component({
  selector: 'ts-boton',
  imports: [NgTemplateOutlet, RouterLink],
  templateUrl: './ts-boton.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsBoton {
  readonly variante = input<VarianteBoton>('primario');
  readonly tipo = input<'button' | 'submit'>('button');
  readonly cargando = input(false);
  /**
   * Como `cargando`, pero **sin deshabilitar el botón**: solo pinta `aria-busy`.
   *
   * Existe porque `cargando` hace las dos cosas y la segunda tiene un precio que no siempre se
   * quiere pagar: deshabilitar el botón que la persona acaba de pulsar le quita el foco, y el
   * navegador lo manda a `<body>`. En una acción de fila —publicar, contar, medir, quitar— eso
   * devuelve al principio del documento a quien navega con teclado. Ese defecto costó dos
   * correcciones en la misma semana y seguía vivo en cuatro pantallas del panel.
   *
   * `cargando` sigue siendo lo correcto donde deshabilitar es el punto: el envío de un formulario
   * de página completa, donde no hay foco de fila que perder. Para una acción de fila, este más una
   * guarda de reentrada en el manejador.
   */
  readonly ocupado = input(false);
  readonly deshabilitado = input(false);
  /** Para usarlo como botón de alternancia (p. ej. una opción de un selector de variante). */
  readonly presionado = input<boolean | null>(null);
  /** Reemplaza el contenido proyectado mientras carga. Traducido por quien llama. */
  readonly etiquetaCargando = input<string | null>(null);
  /**
   * Nombre accesible cuando el contenido proyectado no es texto (p. ej. un
   * glifo "+"/"−"). Sin esto, un `[attr.aria-label]` puesto directamente en
   * `<ts-boton>` cae en el host del componente, no en el `<button>` real, y
   * el nombre accesible del control no cambia.
   */
  readonly etiquetaAccesible = input<string | null>(null);
  /**
   * Para usarlo como *disclosure* (un botón que muestra u oculta una región):
   * `expandido` va a `aria-expanded` y `controla` al `aria-controls` del
   * `<button>` real. Existen por la misma razón que `etiquetaAccesible`: un
   * `[attr.aria-expanded]` puesto en `<ts-boton>` cae en el host, y el lector
   * de pantalla no se entera. `null` no pinta el atributo, que es distinto de
   * `false`.
   */
  readonly expandido = input<boolean | null>(null);
  readonly controla = input<string | null>(null);
  /** Ajustes puntuales de quien llama, p. ej. `w-full`. Gana sobre la base. */
  readonly clase = input('');
  /**
   * Con destino, el componente renderiza un `<a routerLink>` en vez de un
   * `<button>`. Existe porque seis pantallas —el carrito, el resumen del
   * checkout y la transferencia— envolvían `<ts-boton>` en un `<a>` para
   * navegar, y eso es HTML inválido: `<a>` no admite contenido interactivo
   * descendiente. El resultado en el navegador eran dos paradas de tabulación
   * por acción, el ancla con el anillo del navegador y el botón con el de la
   * marca.
   *
   * `RouterLink` es lo único de `@angular/router` que entra en `shared/ui`, y
   * entra porque navegar es del framework, no del negocio: el componente sigue
   * sin saber qué se vende ni de dónde vienen los datos. La regla que importa
   * —nada de Transloco ni de TanStack Query aquí— sigue intacta.
   */
  readonly enlace = input<unknown[] | string | null>(null);
  /** Los `queryParams` del enlace. Solo se usa junto con `enlace`. */
  readonly parametrosEnlace = input<Record<string, unknown> | null>(null);

  protected readonly clases = computed(() => cn(BASE, VARIANTES[this.variante()], this.clase()));
}
