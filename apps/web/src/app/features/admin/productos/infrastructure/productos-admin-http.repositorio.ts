import { Injectable, inject } from '@angular/core';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import {
  AgregarVarianteAdmin,
  CrearProductoAdmin,
  EditarProductoAdmin,
  FiltroProductosAdmin,
  ImagenAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
  SubirImagenPrincipalAdmin,
} from '../domain/producto-admin.model';
import { RepositorioProductosAdmin } from '../domain/repositorio-productos-admin.puerto';
import { aImagenAdmin, aProductoAdmin, aProductosPaginadosAdmin } from './mapeador-producto-admin';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer` — mismo criterio que
 * `admin/pedidos/infrastructure/pedidos-admin-http.repositorio.ts`. */
@Injectable()
export class ProductosAdminHttpRepositorio implements RepositorioProductosAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroProductosAdmin): Promise<ProductosPaginadosAdmin> {
    const { data, error } = await this.cliente.GET('/api/v1/admin/productos', {
      params: { query: { pagina: filtro.pagina, tamano: filtro.tamano } },
    });
    if (error) {
      throw new Error('No se pudo listar los productos.');
    }
    return aProductosPaginadosAdmin(data);
  }

  async crear(comando: CrearProductoAdmin): Promise<ProductoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/productos', {
      body: {
        nombre: comando.nombre,
        descripcion: comando.descripcion,
        marcaId: comando.marcaId,
        categoriaId: comando.categoriaId,
      },
    });
    if (error) {
      throw new Error('No se pudo crear el producto.');
    }
    return aProductoAdmin(data);
  }

  async obtener(id: string): Promise<ProductoAdmin> {
    const { data, error } = await this.cliente.GET('/api/v1/admin/productos/{id}', {
      params: { path: { id } },
    });
    if (error) {
      throw new Error('No se pudo cargar el producto.');
    }
    return aProductoAdmin(data);
  }

  async editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin> {
    const { data, error } = await this.cliente.PATCH('/api/v1/admin/productos/{id}', {
      params: { path: { id } },
      body: {
        nombre: comando.nombre,
        descripcion: comando.descripcion,
        marcaId: comando.marcaId,
        categoriaId: comando.categoriaId,
      },
    });
    if (error) {
      throw new Error('No se pudo editar el producto.');
    }
    return aProductoAdmin(data);
  }

  async agregarVariante(comando: AgregarVarianteAdmin): Promise<void> {
    const { error } = await this.cliente.POST('/api/v1/admin/variantes', {
      body: {
        productoId: comando.productoId,
        sku: comando.sku,
        precio: comando.precio,
        tasaIva: comando.tasaIva,
        codigoBarras: comando.codigoBarras ?? undefined,
        existenciaInicial: comando.existenciaInicial,
        atributos: comando.atributos.map((a) => ({
          atributoId: a.atributoId,
          valor: a.valor,
          colorHex: a.colorHex ?? undefined,
        })),
      },
    });
    if (error) {
      throw new Error('No se pudo agregar la variante.');
    }
  }

  async subirImagenPrincipal(comando: SubirImagenPrincipalAdmin): Promise<ImagenAdmin> {
    const { data: solicitud, error: errorSolicitud } = await this.cliente.POST(
      '/api/v1/admin/productos/{id}/imagen-principal/url-subida',
      {
        params: { path: { id: comando.productoId } },
        body: { contentType: comando.archivo.type },
      },
    );
    if (errorSolicitud || !solicitud?.url || !solicitud.objectKey) {
      throw new Error('No se pudo solicitar la URL de subida.');
    }

    const respuestaSubida = await fetch(solicitud.url, {
      method: 'PUT',
      headers: { 'Content-Type': comando.archivo.type },
      body: comando.archivo,
    });
    if (!respuestaSubida.ok) {
      throw new Error('No se pudo subir la imagen a Cloud Storage.');
    }

    const { data, error } = await this.cliente.POST('/api/v1/admin/productos/{id}/imagen-principal', {
      params: { path: { id: comando.productoId } },
      body: {
        objectKey: solicitud.objectKey,
        ancho: comando.ancho,
        alto: comando.alto,
        altEs: comando.altEs,
        altEn: comando.altEn,
      },
    });
    if (error) {
      throw new Error('No se pudo confirmar la imagen principal.');
    }
    return aImagenAdmin(data);
  }
}
