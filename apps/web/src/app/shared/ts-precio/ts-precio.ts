import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

/**
 * docs/05-i18n.md: la moneda no se convierte, solo cambia el formato por
 * idioma — "$ 189.900" en español, "COP 189,900" en inglés.
 */
@Component({
  selector: 'ts-precio',
  imports: [TranslocoPipe],
  templateUrl: './ts-precio.html',
  styleUrl: './ts-precio.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsPrecio {
  private readonly transloco = inject(TranslocoService);

  readonly valor = input.required<number>();
  readonly moneda = input('COP');
  readonly desde = input(false);

  protected readonly formateado = computed(() => {
    const idioma = this.transloco.activeLang();
    const locale = idioma === 'en' ? 'en-US' : 'es-CO';
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: this.moneda(),
      currencyDisplay: idioma === 'en' ? 'code' : 'symbol',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(this.valor());
  });
}
