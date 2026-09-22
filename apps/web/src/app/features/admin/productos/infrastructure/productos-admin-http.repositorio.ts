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
  ImagenDeGaleriaAdmin,
  AjustarExistenciaAdmin,
  ExistenciaAjustada,
  ExistenciasDelCatalogo,
  InventarioSinMedir,
  MedidasDelCatalogo,
  MedirVarianteAdmin,
  ProductoAdmin,
  ProductoAdminDetalle,
  ProductosPaginadosAdmin,
  QuitarImagenDeGaleriaAdmin,
  ReordenarGaleriaAdmin,
  SubirImagenDeGaleriaAdmin,
  SubirImagenPrincipalAdmin,
  VarianteMedida,
} from '../domain/producto-admin.model';
import { RepositorioProductosAdmin } from '../domain/repositorio-productos-admin.puerto';
import {
  aImagenAdmin,
  aImagenDeGaleriaAdmin,
  aExistenciaAjustada,
  aExistenciasDelCatalogo,
  aInventarioSinMedir,
  aMedidasDelCatalogo,
  aProductoAdmin,
  aProductoAdminDetalle,
  aProductosPaginadosAdmin,
  aVarianteMedida,
} from './mapeador-producto-admin';

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

  async obtener(id: string): Promise<ProductoAdminDetalle> {
    const respuesta = await this.cliente.GET('/api/v1/admin/productos/{id}', {
      params: { path: { id } },
    });
    return aProductoAdminDetalle(desempaquetar(respuesta, 'no se pudo cargar el producto'));
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
        // Ausentes se omiten del cuerpo en vez de viajar como null: el servidor lee "no vino"
        // igual que "vino nulo", y omitir es lo que dice el contrato generado (ADR-0046).
        pesoGramos: comando.pesoGramos ?? undefined,
        largoCm: comando.largoCm ?? undefined,
        anchoCm: comando.anchoCm ?? undefined,
        altoCm: comando.altoCm ?? undefined,
        atributos: comando.atributos.map((a) => ({
          atributoId: a.atributoId,
          valor: a.valor,
          colorHex: a.colorHex ?? undefined,
        })),
      },
    });
    exigirExito(respuesta, 'no se pudo agregar la variante');
  }

  async publicar(id: string): Promise<ProductoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/productos/{id}/publicacion', {
      params: { path: { id } },
    });
    return aProductoAdmin(desempaquetar(respuesta, 'no se pudo publicar el producto'));
  }

  async despublicar(id: string): Promise<ProductoAdmin> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/productos/{id}/publicacion', {
      params: { path: { id } },
    });
    return aProductoAdmin(desempaquetar(respuesta, 'no se pudo despublicar el producto'));
  }

  async listarMedidas(): Promise<MedidasDelCatalogo> {
    const respuesta = await this.cliente.GET('/api/v1/admin/variantes/medidas', {});
    return aMedidasDelCatalogo(desempaquetar(respuesta, 'no se pudo consultar las medidas'));
  }

  async listarSinMedir(): Promise<InventarioSinMedir> {
    const respuesta = await this.cliente.GET('/api/v1/admin/variantes/sin-medir', {});
    return aInventarioSinMedir(
      desempaquetar(respuesta, 'no se pudo consultar las variantes sin medir'),
    );
  }

  async medirVariante(comando: MedirVarianteAdmin): Promise<VarianteMedida> {
    const respuesta = await this.cliente.PATCH('/api/v1/admin/variantes/{id}/paquete', {
      params: { path: { id: comando.varianteId } },
      body: {
        pesoGramos: comando.pesoGramos,
        largoCm: comando.largoCm,
        anchoCm: comando.anchoCm,
        altoCm: comando.altoCm,
      },
    });
    return aVarianteMedida(desempaquetar(respuesta, 'no se pudo medir la variante'));
  }

  async listarExistencias(): Promise<ExistenciasDelCatalogo> {
    const respuesta = await this.cliente.GET('/api/v1/admin/variantes/existencias', {});
    return aExistenciasDelCatalogo(
      desempaquetar(respuesta, 'no se pudo consultar las existencias'),
    );
  }

  async ajustarExistencia(comando: AjustarExistenciaAdmin): Promise<ExistenciaAjustada> {
    const respuesta = await this.cliente.PATCH('/api/v1/admin/variantes/{id}/existencia', {
      params: { path: { id: comando.varianteId } },
      body: { cantidadContada: comando.cantidadContada, motivo: comando.motivo },
    });
    return aExistenciaAjustada(desempaquetar(respuesta, 'no se pudo ajustar la existencia'));
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
        // Una sola variante: el panel sube el archivo que una persona eligió y no tiene de dónde
        // sacar otras resoluciones. La escalera completa la manda el cargador del catálogo, que sí
        // las tiene del procesamiento de estudio.
        variantes: [{ ancho: comando.ancho, objectKey: solicitud.objectKey }],
        alto: comando.alto,
        hash: await sha256Hex(comando.archivo),
        altEs: comando.altEs,
        altEn: comando.altEn,
      },
    });
    return aImagenAdmin(desempaquetar(respuesta, 'no se pudo confirmar la imagen principal'));
  }

  async subirImagenDeGaleria(comando: SubirImagenDeGaleriaAdmin): Promise<ImagenDeGaleriaAdmin> {
    const respuestaSolicitud = await this.cliente.POST(
      '/api/v1/admin/productos/{id}/galeria/url-subida',
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

    const respuesta = await this.cliente.POST('/api/v1/admin/productos/{id}/galeria', {
      params: { path: { id: comando.productoId } },
      body: {
        // Una sola variante: el panel sube el archivo que una persona eligió y no tiene de dónde
        // sacar otras resoluciones. La escalera completa la manda el cargador del catálogo, que sí
        // las tiene del procesamiento de estudio.
        variantes: [{ ancho: comando.ancho, objectKey: solicitud.objectKey }],
        alto: comando.alto,
        hash: await sha256Hex(comando.archivo),
        altEs: comando.altEs,
        altEn: comando.altEn,
      },
    });
    return aImagenDeGaleriaAdmin(
      desempaquetar(respuesta, 'no se pudo agregar la imagen a la galería'),
    );
  }

  async quitarImagenDeGaleria(comando: QuitarImagenDeGaleriaAdmin): Promise<void> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/productos/{id}/galeria/{imagenId}', {
      params: { path: { id: comando.productoId, imagenId: comando.imagenId } },
    });
    exigirExito(respuesta, 'no se pudo quitar la imagen de la galería');
  }

  async reordenarGaleria(comando: ReordenarGaleriaAdmin): Promise<void> {
    const respuesta = await this.cliente.PUT('/api/v1/admin/productos/{id}/galeria/orden', {
      params: { path: { id: comando.productoId } },
      body: { imagenIds: [...comando.imagenIds] },
    });
    exigirExito(respuesta, 'no se pudo cambiar el orden de la galería');
  }
}
