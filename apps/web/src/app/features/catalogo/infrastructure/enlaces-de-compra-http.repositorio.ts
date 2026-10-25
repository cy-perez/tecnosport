import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { EnlaceDeCompra } from '../domain/enlace-de-compra.model';
import { RepositorioEnlacesDeCompra } from '../domain/repositorio-enlaces-de-compra.puerto';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';

@Injectable()
export class EnlacesDeCompraHttpRepositorio implements RepositorioEnlacesDeCompra {
  private readonly cliente = crearClienteContratos(baseUrl());

  async consultar(token: string): Promise<EnlaceDeCompra | null> {
    const respuesta = await this.cliente.GET('/api/v1/enlaces-de-compra/{token}', {
      params: { path: { token } },
    });
    // 410 es la respuesta de la API para cualquier enlace que no sirve; 404, por si un proxy
    // recorta la ruta. Los dos son «este enlace ya no compra», no un error de la página.
    if (respuesta.response.status === 410 || respuesta.response.status === 404) {
      return null;
    }
    const datos = desempaquetar(respuesta, 'no se pudo consultar el enlace de compra');
    return { token, varianteId: datos.varianteId, expiraEn: datos.expiraEn };
  }
}
