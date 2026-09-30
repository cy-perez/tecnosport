import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { DatosProveedor, Proveedor } from '../domain/proveedor.model';
import { RepositorioProveedoresAdmin } from '../domain/repositorio-proveedores-admin.puerto';
import { aProveedor, aProveedorPeticion } from './mapeador-proveedor';

@Injectable()
export class ProveedoresAdminHttpRepositorio implements RepositorioProveedoresAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(): Promise<Proveedor[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/proveedores');
    return desempaquetar(respuesta, 'no se pudieron cargar los proveedores').map(aProveedor);
  }

  async obtener(id: string): Promise<Proveedor> {
    const respuesta = await this.cliente.GET('/api/v1/admin/proveedores/{id}', {
      params: { path: { id } },
    });
    return aProveedor(desempaquetar(respuesta, 'no se pudo cargar el proveedor'));
  }

  async crear(datos: DatosProveedor): Promise<Proveedor> {
    const respuesta = await this.cliente.POST('/api/v1/admin/proveedores', {
      body: aProveedorPeticion(datos),
    });
    return aProveedor(desempaquetar(respuesta, 'no se pudo crear el proveedor'));
  }

  async editar(id: string, datos: DatosProveedor): Promise<Proveedor> {
    const respuesta = await this.cliente.PUT('/api/v1/admin/proveedores/{id}', {
      params: { path: { id } },
      body: aProveedorPeticion(datos),
    });
    return aProveedor(desempaquetar(respuesta, 'no se pudo guardar el proveedor'));
  }
}
