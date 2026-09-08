import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { cn } from '../cn';

export type VarianteBoton = 'primario' | 'secundario' | 'texto' | 'peligro';

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
    'min-h-tactil bg-transparent text-ts-primario border border-ts-borde-control not-disabled:hover:bg-ts-superficie-alt',
  texto: 'bg-transparent text-ts-primario px-12 py-8 not-disabled:hover:underline',
  peligro:
    'min-h-tactil bg-ts-error text-ts-sobre-primario not-disabled:hover:brightness-110',
};

/**
 * El mínimo táctil de 44 px vive en cada variante y no en la base, aunque tres
 * de las cuatro lo repitan. El SCSS lo ponía en la base y lo anulaba con
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
const BASE =
  'inline-flex items-center justify-center gap-8 py-12 px-24 border-0 ' +
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

  protected readonly clases = computed(() =>
    cn(BASE, VARIANTES[this.variante()], this.clase()),
  );
}
