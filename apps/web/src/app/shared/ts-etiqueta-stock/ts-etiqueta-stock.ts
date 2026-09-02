import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'ts-etiqueta-stock',
  imports: [TranslocoPipe],
  templateUrl: './ts-etiqueta-stock.html',
  styleUrl: './ts-etiqueta-stock.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsEtiquetaStock {
  readonly disponible = input.required<boolean>();
}
