import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { FiltroProductos } from '../domain/filtro-productos.model';
import { Producto } from '../domain/producto.model';
import { RepositorioProductos } from '../domain/repositorio-productos.puerto';
import { ResultadoPaginado } from '../domain/resultado-paginado.model';
import { aProducto } from './mapeador-productos';

/**
 * En el navegador, base relativa a propósito, nunca una URL literal: en
 * producción el balanceador enruta /api al backend en el mismo dominio
 * (docs/07-infra-gcp.md); en local, proxy.conf.json hace lo mismo en
 * `ng serve`. Pero `fetch` en Node (SSR) no tiene un origen de página contra
 * el cual resolver una ruta relativa — ahí sí hace falta una base absoluta,
 * tomada de API_URL_PUBLICA (ya documentada en docs/07-infra-gcp.md y
 * .env.example), con el mismo valor por defecto que usa el backend en local.
 */
function baseUrl(): string {
  if (typeof window !== 'undefined') {
    return '';
  }
  const publica = typeof process !== 'undefined' ? process.env['API_URL_PUBLICA'] : undefined;
  return publica ? new URL(publica).origin : 'http://localhost:8080';
}

@Injectable()
export class ProductosHttpRepositorio implements RepositorioProductos {
  private readonly cliente = crearClienteContratos(baseUrl());

  async buscar(filtro: FiltroProductos, cursor: string | null): Promise<ResultadoPaginado<Producto>> {
    const { data, error } = await this.cliente.GET('/api/v1/productos', {
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

    if (error) {
      throw new Error('No se pudo cargar el catálogo.');
    }

    return {
      items: (data.items ?? []).map(aProducto),
      cursorSiguiente: data.cursorSiguiente ?? null,
    };
  }

  async buscarPorSlug(slug: string): Promise<Producto | null> {
    const { data, error, response } = await this.cliente.GET('/api/v1/productos/{slug}', {
      params: { path: { slug } },
    });

    if (response.status === 404) {
      return null;
    }
    if (error) {
      throw new Error('No se pudo cargar el producto.');
    }

    return aProducto(data);
  }
}
