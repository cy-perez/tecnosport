import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Marcador de carga. `aria-hidden`: no lee nada al lector de pantalla; el estado de carga lo anuncia el contenedor. */
@Component({
  selector: 'ts-esqueleto',
  templateUrl: './ts-esqueleto.html',
  styleUrl: './ts-esqueleto.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TsEsqueleto {}
