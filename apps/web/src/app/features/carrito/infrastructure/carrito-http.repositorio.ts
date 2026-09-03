import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { Carrito } from '../domain/carrito.model';
import { RepositorioCarrito } from '../domain/repositorio-carrito.puerto';
import { aCarrito } from './mapeador-carrito';

@Injectable()
export class CarritoHttpRepositorio implements RepositorioCarrito {
  private readonly cliente = crearClienteContratos(baseUrl());

  async crear(): Promise<Carrito> {
    const { data, error } = await this.cliente.POST('/api/v1/carritos');
    if (error) {
      throw new Error('No se pudo crear el carrito.');
    }
    return aCarrito(data);
  }

  async ver(carritoId: string): Promise<Carrito | null> {
    const { data, error, response } = await this.cliente.GET('/api/v1/carritos/{id}', {
      params: { path: { id: carritoId } },
    });
    if (response.status === 404) {
      return null;
    }
    if (error) {
      throw new Error('No se pudo cargar el carrito.');
    }
    return aCarrito(data);
  }

  async agregarLinea(carritoId: string, varianteId: string, cantidad: number): Promise<Carrito> {
    const { data, error } = await this.cliente.POST('/api/v1/carritos/{id}/lineas', {
      params: { path: { id: carritoId } },
      body: { varianteId, cantidad },
    });
    if (error) {
      throw new Error('No se pudo agregar la línea al carrito.');
    }
    return aCarrito(data);
  }

  async actualizarCantidad(carritoId: string, lineaId: string, cantidad: number): Promise<Carrito> {
    const { data, error } = await this.cliente.PATCH('/api/v1/carritos/{id}/lineas/{lineaId}', {
      params: { path: { id: carritoId, lineaId } },
      body: { cantidad },
    });
    if (error) {
      throw new Error('No se pudo actualizar la cantidad.');
    }
    return aCarrito(data);
  }

  async eliminarLinea(carritoId: string, lineaId: string): Promise<Carrito> {
    const { data, error } = await this.cliente.DELETE('/api/v1/carritos/{id}/lineas/{lineaId}', {
      params: { path: { id: carritoId, lineaId } },
    });
    if (error) {
      throw new Error('No se pudo eliminar la línea.');
    }
    return aCarrito(data);
  }
}
