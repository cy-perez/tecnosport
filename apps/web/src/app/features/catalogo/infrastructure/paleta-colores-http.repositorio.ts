import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { ColorDePaleta, PatronDeColor } from '../domain/producto.model';
import { RepositorioPaletaColores } from '../domain/repositorio-paleta-colores.puerto';

@Injectable()
export class PaletaColoresHttpRepositorio implements RepositorioPaletaColores {
  private readonly cliente = crearClienteContratos(baseUrl());

  async listarTodos(): Promise<ColorDePaleta[]> {
    const respuesta = await this.cliente.GET('/api/v1/colores');
    return desempaquetar(respuesta, 'no se pudo cargar la paleta de colores').map((color) => ({
      nombre: color.nombre ?? '',
      nombreEn: color.nombreEn ?? '',
      hex: color.hex ?? '',
      patron: (color.patron ?? null) as PatronDeColor | null,
      coloresPatron: color.coloresPatron ?? [],
    }));
  }
}
