import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { leerDescripcion, Segmento } from './leer-descripcion';

/**
 * La descripción de un producto pintada por secciones: subtítulos, viñetas y la ficha técnica en
 * una tabla de dos columnas (`leer-descripcion.ts`).
 *
 * **El pie de fuentes no se pinta** (decisión del negocio del 23/10/2026): la ficha no dice de
 * dónde salió cada dato, y eso incluye el aviso de Open Icecat aunque su licencia lo pida
 * (`docs/14-consultas-al-abogado.md`). El lector lo sigue reconociendo para que una descripción
 * vieja con `---` no lo enseñe como párrafo; aquí se descarta. Las fuentes se guardan fuera del
 * texto, en la base de la ingesta.
 *
 * En `shared/ui/` y con el texto como entrada primitiva: la usan la ficha del catálogo, que es lo
 * que ve el cliente, y la vista previa de los borradores de tecnología del panel, que tiene que
 * verse igual para que quien aprueba sepa qué va a publicar. No trae textos propios —todo lo
 * visible sale de la descripción, incluido el encabezado de la tabla—, así que no necesita
 * Transloco.
 */
@Component({
  selector: 'ts-descripcion',
  imports: [NgTemplateOutlet],
  templateUrl: './ts-descripcion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col gap-16' },
})
export class TsDescripcion {
  readonly texto = input.required<string | null>();

  /**
   * El nivel de los subtítulos. 2 en la ficha, bajo el `h1` del producto; 3 donde la descripción
   * ya vive dentro de una sección con su propio `h2`, como el panel.
   */
  readonly nivelDeTitulo = input<2 | 3>(2);

  protected readonly bloques = computed(() =>
    leerDescripcion(this.texto()).filter((bloque) => bloque.tipo !== 'fuentes'),
  );

  /** El contexto del `ng-template` de los trozos: la plantilla no puede tipar `let-` sola. */
  protected trozos(segmentos: readonly Segmento[]): { $implicit: readonly Segmento[] } {
    return { $implicit: segmentos };
  }
}
