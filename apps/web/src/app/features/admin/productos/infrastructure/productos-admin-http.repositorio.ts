import { Injectable, inject } from '@angular/core';
import { sha256Hex } from '../../../../core/hash/sha256';
import { baseUrl } from '../../../../core/http/base-url';
import { ErrorHttp, desempaquetar, exigirExito } from '../../../../core/http/respuesta-http';
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
    const respuesta = await this.cliente.GET('/api/v1/admin/productos', {
      params: { query: { pagina: filtro.pagina, tamano: filtro.tamano } },
    });
    return aProductosPaginadosAdmin(desempaquetar(respuesta, 'no se pudo listar los productos'));
  }

  async crear(comando: CrearProductoAdmin): Promise<ProductoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/productos', {
      body: {
        nombre: comando.nombre,
        descripcion: comando.descripcion,
        marcaId: comando.marcaId,
        categoriaId: comando.categoriaId,
      },
    });
    return aProductoAdmin(desempaquetar(respuesta, 'no se pudo crear el producto'));
  }

  async obtener(id: string): Promise<ProductoAdmin> {
    const respuesta = await this.cliente.GET('/api/v1/admin/productos/{id}', {
      params: { path: { id } },
    });
    return aProductoAdmin(desempaquetar(respuesta, 'no se pudo cargar el producto'));
  }

  async editar(id: string, comando: EditarProductoAdmin): Promise<ProductoAdmin> {
    const respuesta = await this.cliente.PATCH('/api/v1/admin/productos/{id}', {
      params: { path: { id } },
      body: {
        nombre: comando.nombre,
        descripcion: comando.descripcion,
        marcaId: comando.marcaId,
        categoriaId: comando.categoriaId,
      },
    });
    return aProductoAdmin(desempaquetar(respuesta, 'no se pudo editar el producto'));
  }

  async agregarVariante(comando: AgregarVarianteAdmin): Promise<void> {
    const respuesta = await this.cliente.POST('/api/v1/admin/variantes', {
      body: {
        productoId: comando.productoId,
        sku: comando.sku,
        precio: comando.precio,
        tasaIva: comando.tasaIva,
        codigoBarras: comando.codigoBarras ?? undefined,
        existenciaInicial: comando.existenciaInicial,
        pesoGramos: comando.pesoGramos,
        largoCm: comando.largoCm,
        anchoCm: comando.anchoCm,
        altoCm: comando.altoCm,
        atributos: comando.atributos.map((a) => ({
          atributoId: a.atributoId,
          valor: a.valor,
          colorHex: a.colorHex ?? undefined,
        })),
      },
    });
    exigirExito(respuesta, 'no se pudo agregar la variante');
  }

  async subirImagenPrincipal(comando: SubirImagenPrincipalAdmin): Promise<ImagenAdmin> {
    const respuestaSolicitud = await this.cliente.POST(
      '/api/v1/admin/productos/{id}/imagen-principal/url-subida',
      {
        params: { path: { id: comando.productoId } },
        body: { contentType: comando.archivo.type },
      },
    );
    const solicitud = desempaquetar(respuestaSolicitud, 'no se pudo solicitar la URL de subida');
    if (!solicitud.url || !solicitud.objectKey) {
      throw new ErrorHttp(respuestaSolicitud.response.status, 'la URL de subida llegó incompleta');
    }

    const respuestaSubida = await fetch(solicitud.url, {
      method: 'PUT',
      headers: { 'Content-Type': comando.archivo.type },
      body: comando.archivo,
    });
    if (!respuestaSubida.ok) {
      throw new Error('No se pudo subir la imagen a Cloud Storage.');
    }

    const respuesta = await this.cliente.POST('/api/v1/admin/productos/{id}/imagen-principal', {
      params: { path: { id: comando.productoId } },
      body: {
        objectKey: solicitud.objectKey,
        ancho: comando.ancho,
        alto: comando.alto,
        hash: await sha256Hex(comando.archivo),
        altEs: comando.altEs,
        altEn: comando.altEn,
      },
    });
    return aImagenAdmin(desempaquetar(respuesta, 'no se pudo confirmar la imagen principal'));
  }
}
