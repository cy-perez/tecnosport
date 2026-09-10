import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { RepositorioRetractos } from '../domain/repositorio-retractos.puerto';
import { MedioReembolso, SolicitudRetracto } from '../domain/retracto.model';
import { aSolicitudRetracto } from './mapeador-retracto';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class RetractosHttpRepositorio implements RepositorioRetractos {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarDePedido(pedidoId: string): Promise<readonly SolicitudRetracto[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/pedidos/{pedidoId}/retractos', {
      params: { path: { pedidoId } },
    });
    const datos = desempaquetar(respuesta, 'no se pudieron cargar los retractos del pedido');
    return datos.map(aSolicitudRetracto);
  }

  async radicar(pedidoId: string, motivo: string | null): Promise<SolicitudRetracto> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{pedidoId}/retractos', {
      params: { path: { pedidoId } },
      body: { motivo: motivo ?? undefined },
    });
    return aSolicitudRetracto(desempaquetar(respuesta, 'no se pudo radicar el retracto'));
  }

  async recibirProducto(solicitudId: string): Promise<SolicitudRetracto> {
    const respuesta = await this.cliente.POST('/api/v1/admin/retractos/{id}/recepcion', {
      params: { path: { id: solicitudId } },
    });
    return aSolicitudRetracto(
      desempaquetar(respuesta, 'no se pudo registrar la recepción del producto'),
    );
  }

  async registrarReembolso(
    solicitudId: string,
    monto: number,
    medio: MedioReembolso,
    comprobante: string | null,
  ): Promise<SolicitudRetracto> {
    const respuesta = await this.cliente.POST('/api/v1/admin/retractos/{id}/reembolso', {
      params: { path: { id: solicitudId } },
      body: { monto, medio, comprobante: comprobante ?? undefined },
    });
    return aSolicitudRetracto(desempaquetar(respuesta, 'no se pudo registrar el reembolso'));
  }
}
