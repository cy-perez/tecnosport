import { Provider } from '@angular/core';
import { ColorDePaleta } from '../app/features/catalogo/domain/producto.model';
import {
  REPOSITORIO_PALETA_COLORES,
  RepositorioPaletaColores,
} from '../app/features/catalogo/domain/repositorio-paleta-colores.puerto';

/** Dos colores de la paleta, con su nombre en inglés. */
export class RepositorioPaletaColoresFalso implements RepositorioPaletaColores {
  async listarTodos(): Promise<ColorDePaleta[]> {
    return [
      { nombre: 'Negro', nombreEn: 'Black', hex: '#111111' },
      { nombre: 'Vino', nombreEn: 'Burgundy', hex: '#722F37' },
    ];
  }
}

/** Para las pantallas que pintan la tarjeta o la ficha: las dos leen la paleta. */
export function proveerPaletaDePrueba(): Provider {
  return { provide: REPOSITORIO_PALETA_COLORES, useClass: RepositorioPaletaColoresFalso };
}
