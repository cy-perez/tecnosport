import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar, ErrorHttp } from '../../../../core/http/respuesta-http';
import {
  FiltroLotes,
  LoteEliminado,
  LoteIngesta,
  LotesPaginados,
  OrdenIngesta,
  SubirExportacion,
} from '../domain/ingesta.model';
import { RepositorioIngestasAdmin } from '../domain/repositorio-ingestas-admin.puerto';
import { aLoteIngesta, aLotesPaginados } from './mapeador-ingesta';

/**
 * Lo que el navegador manda como tipo del zip. Chrome dice `application/zip`, Windows a veces
 * `application/x-zip-compressed` y un archivo sin extensión conocida llega vacío: el servidor
 * firma la URL para un tipo concreto y el PUT tiene que repetir exactamente ese, así que se
 * normaliza aquí, una sola vez, antes de pedir la firma.
 */
const TIPO_ZIP = 'application/zip';

const TAMANO_PAGINA = 20;

/**
 * La exportación no pasa por la API: se pide una URL firmada, se sube derecho al bucket privado
 * de proveedores y luego se avisa al servidor con la key. Es el mismo camino que las imágenes del
 * catálogo (`productos-admin-http.repositorio.ts`), y por la misma razón: un zip de fotos pesa
 * decenas de megas y Cloud Run no tiene por qué recibirlos para volverlos a mandar.
 */
@Injectable()
export class IngestasAdminHttpRepositorio implements RepositorioIngestasAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroLotes): Promise<LotesPaginados> {
    const respuesta = await this.cliente.GET('/api/v1/admin/ingestas', {
      params: {
        query: {
          ...(filtro.proveedorId ? { proveedorId: filtro.proveedorId } : {}),
          pagina: filtro.pagina,
          tamano: TAMANO_PAGINA,
        },
      },
    });
    return aLotesPaginados(desempaquetar(respuesta, 'no se pudieron cargar las ingestas'));
  }

  async obtener(id: string): Promise<LoteIngesta> {
    const respuesta = await this.cliente.GET('/api/v1/admin/ingestas/{id}', {
      params: { path: { id } },
    });
    return aLoteIngesta(desempaquetar(respuesta, 'no se pudo cargar la ingesta'));
  }

  async eliminar(id: string): Promise<LoteEliminado> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/ingestas/{id}', {
      params: { path: { id } },
    });
    const cuerpo = desempaquetar(respuesta, 'no se pudo eliminar la ingesta');
    return {
      productosEliminados: cuerpo.productosEliminados ?? 0,
      productosConservados: cuerpo.productosConservados ?? 0,
    };
  }

  /** Un `switch` y no una ruta armada: el cliente tipado solo acepta rutas literales del contrato. */
  async ordenar(id: string, orden: OrdenIngesta): Promise<LoteIngesta> {
    const params = { path: { id } };
    switch (orden) {
      case 'pausar':
        return aLoteIngesta(
          desempaquetar(
            await this.cliente.POST('/api/v1/admin/ingestas/{id}/pausar', { params }),
            'no se pudo pausar la ingesta',
          ),
        );
      case 'reanudar':
        return aLoteIngesta(
          desempaquetar(
            await this.cliente.POST('/api/v1/admin/ingestas/{id}/reanudar', { params }),
            'no se pudo reanudar la ingesta',
          ),
        );
      case 'detener':
        return aLoteIngesta(
          desempaquetar(
            await this.cliente.POST('/api/v1/admin/ingestas/{id}/detener', { params }),
            'no se pudo detener la ingesta',
          ),
        );
    }
  }

  async subir(comando: SubirExportacion): Promise<LoteIngesta> {
    const respuestaSolicitud = await this.cliente.POST(
      '/api/v1/admin/proveedores/{id}/ingestas/url-subida',
      {
        params: { path: { id: comando.proveedorId } },
        body: { contentType: TIPO_ZIP },
      },
    );
    const solicitud = desempaquetar(respuestaSolicitud, 'no se pudo solicitar la URL de subida');
    if (!solicitud.url || !solicitud.objectKey) {
      throw new ErrorHttp(respuestaSolicitud.response.status, 'la URL de subida llegó incompleta');
    }

    const respuestaSubida = await fetch(solicitud.url, {
      method: 'PUT',
      headers: { 'Content-Type': TIPO_ZIP },
      body: comando.archivo,
    });
    if (!respuestaSubida.ok) {
      throw new Error('No se pudo subir la exportación a Cloud Storage.');
    }

    const respuesta = await this.cliente.POST('/api/v1/admin/proveedores/{id}/ingestas', {
      params: { path: { id: comando.proveedorId } },
      body: { objectKey: solicitud.objectKey },
    });
    return aLoteIngesta(desempaquetar(respuesta, 'no se pudo iniciar la ingesta'));
  }
}
