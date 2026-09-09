import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { FiltroProductos } from '../domain/filtro-productos.model';
import { Producto } from '../domain/producto.model';
import { RepositorioProductos } from '../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../domain/resultado-paginado.model';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { aProducto } from './mapeador-productos';

@Injectable()
export class ProductosHttpRepositorio implements RepositorioProductos {
  private readonly cliente = crearClienteContratos(baseUrl());

  async buscar(filtro: FiltroProductos, cursor: string | null): Promise<ResultadoPaginado<Producto>> {
    const respuesta = await this.cliente.GET('/api/v1/productos', {
      params: {
        query: {
          categoria: filtro.categoria,
          marca: filtro.marca,
          linea: filtro.linea,
          precioMin: filtro.precioMin,
          precioMax: filtro.precioMax,
          texto: filtro.texto,
          orden: filtro.orden,
          cursor: cursor ?? undefined,
          tamano: filtro.tamano,
        },
      },
    });

    const datos = desempaquetar(respuesta, 'no se pudo cargar el catálogo');

    return {
      items: (datos.items ?? []).map(aProducto),
      cursorSiguiente: datos.cursorSiguiente ?? null,
    };
  }

  async buscarPorSlug(slug: string): Promise<Producto | null> {
    const respuesta = await this.cliente.GET('/api/v1/productos/{slug}', {
      params: { path: { slug } },
    });

    if (respuesta.response.status === 404) {
      return null;
    }

    return aProducto(desempaquetar(respuesta, 'no se pudo cargar el producto'));
  }
}
