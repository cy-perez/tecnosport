import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type VarianteBoton = 'primario' | 'secundario' | 'texto' | 'peligro';

@Component({
  selector: 'ts-boton',
  templateUrl: './ts-boton.html',
  styleUrl: './ts-boton.scss',
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
}
