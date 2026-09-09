import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar } from '../../../core/http/respuesta-http';
import { Carrito } from '../domain/carrito.model';
import { RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { aCarrito } from './mapeador-carrito';

@Injectable()
export class CarritoHttpRepositorio implements RepositorioCarrito {
  private readonly cliente = crearClienteContratos(baseUrl());

  async crear(): Promise<Carrito> {
    const respuesta = await this.cliente.POST('/api/v1/carritos');
    return aCarrito(desempaquetar(respuesta, 'no se pudo crear el carrito'));
  }

  async ver(carritoId: string): Promise<Carrito | null> {
    const respuesta = await this.cliente.GET('/api/v1/carritos/{id}', {
      params: { path: { id: carritoId } },
    });
    if (respuesta.response.status === 404) {
      return null;
    }
    return aCarrito(desempaquetar(respuesta, 'no se pudo cargar el carrito'));
  }

  async agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito> {
    const respuesta = await this.cliente.POST('/api/v1/carritos/{id}/lineas', {
      params: { path: { id: carritoId } },
      body: { varianteId, cantidad },
    });
    return aCarrito(desempaquetar(respuesta, 'no se pudo agregar la línea al carrito'));
  }

  async actualizarCantidad(carritoId: string, lineaId: string, cantidad: number): Promise<Carrito> {
    const respuesta = await this.cliente.PATCH('/api/v1/carritos/{id}/lineas/{lineaId}', {
      params: { path: { id: carritoId, lineaId } },
      body: { cantidad },
    });
    return aCarrito(desempaquetar(respuesta, 'no se pudo actualizar la cantidad'));
  }

  async eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito> {
    const respuesta = await this.cliente.DELETE('/api/v1/carritos/{id}/lineas/{lineaId}', {
      params: { path: { id: carritoId, lineaId } },
    });
    return aCarrito(desempaquetar(respuesta, 'no se pudo eliminar la línea'));
  }
}
