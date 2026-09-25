import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { cn } from '../cn';
import { iconoAcciones } from '../icono/iconos';
import { TsIcono } from '../icono/ts-icono';

/**
 * Una opción del menú, ya traducida por quien lo usa — `shared/ui` no conoce Transloco.
 *
 * <p>`enlace` la convierte en un `<a>` en vez de un `<button>`, y no es cosmético: "Editar" lleva a
 * una URL que se puede abrir en otra pestaña con el clic central o con Ctrl, y un `<button>` que
 * navega por código pierde las dos cosas. Mismo criterio que el input `enlace` de `ts-boton`.
 */
export interface AccionDeMenu {
  /** Lo que se emite al elegirla. Solo se usa cuando no lleva `enlace`. */
  readonly id: string;
  readonly etiqueta: string;
  readonly enlace?: readonly unknown[];
  /** Pinta la opción en el color de error. Para lo que no tiene vuelta. */
  readonly destructiva?: boolean;
}

/** El botón de los tres puntos. `min-w-tactil` además de `min-h-`: es cuadrado y sin texto. */
const CLASES_DISPARADOR =
  'anillo-foco inline-flex min-h-tactil min-w-tactil cursor-pointer items-center justify-center ' +
  'rounded-md border border-transparent bg-transparent text-ts-texto-suave ' +
  'hover:bg-ts-superficie-alt hover:text-ts-texto';

const CLASES_PANEL =
  'flex min-w-filtro flex-col gap-4 rounded-lg border border-ts-borde bg-ts-superficie p-4 ' +
  'shadow-lg';

const CLASES_OPCION =
  'anillo-foco flex min-h-tactil w-full cursor-pointer items-center rounded-md border ' +
  'border-transparent bg-transparent px-16 text-left font-texto text-sm no-underline ' +
  'hover:bg-ts-superficie-alt';

/**
 * El menú de acciones de una fila: un botón de tres puntos que abre las opciones que no caben.
 *
 * <p>Sobre `@angular/cdk/menu` y no sobre un `@if` con un `<div absolute>`, por dos motivos que no
 * son de gusto:
 *
 * <ul>
 *   <li><b>El recorte.</b> Una tabla ancha vive dentro de un `overflow-x-auto`, y ahí un panel
 *       posicionado en la fila se corta por el borde de la caja. El CDK lo pinta en un portal a
 *       nivel de `body`.</li>
 *   <li><b>El teclado.</b> `role="menu"`, flechas para recorrer, Escape para cerrar, foco que
 *       vuelve al disparador y cierre al pulsar fuera. Son unas cien líneas de trabajo fácil de
 *       hacer a medias, y hacerlo a medias en un menú se nota justo con lector de pantalla.</li>
 * </ul>
 *
 * <p>Las opciones llegan como <b>dato</b> y no proyectadas con `<ng-content>`: `CdkMenu` encuentra
 * sus opciones con una consulta de contenido, y lo que entra por un `ng-content` de un componente
 * de envoltura no es contenido suyo. El menú se habría pintado sin una sola opción navegable, con
 * las flechas muertas y sin que nada fallara.
 *
 * <p>Requiere la hoja de posicionamiento del CDK, que `src/styles.scss` importa — ver el comentario
 * de ahí.
 */
@Component({
  selector: 'ts-menu-acciones',
  imports: [CdkMenu, CdkMenuItem, CdkMenuTrigger, RouterLink, TsIcono],
  templateUrl: './ts-menu-acciones.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsMenuAcciones {
  /**
   * El nombre accesible del disparador. Obligatorio porque el botón no tiene texto: con una fila
   * por producto, diez botones llamados "Acciones" no dicen de cuál producto son.
   */
  readonly etiqueta = input.required<string>();
  readonly acciones = input.required<readonly AccionDeMenu[]>();

  /** El `id` de la acción elegida. Las que llevan `enlace` navegan y no emiten. */
  readonly elegida = output<string>();

  protected readonly iconoAcciones = iconoAcciones;
  protected readonly clasesDisparador = CLASES_DISPARADOR;
  protected readonly clasesPanel = CLASES_PANEL;

  protected clasesDe(accion: AccionDeMenu): string {
    return cn(CLASES_OPCION, accion.destructiva ? 'text-ts-error' : 'text-ts-texto');
  }
}
