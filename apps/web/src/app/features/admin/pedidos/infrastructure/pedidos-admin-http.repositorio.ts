import { Injectable, inject } from '@angular/core';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { baseUrl } from '../../../../core/http/base-url';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { FiltroPedidosAdmin, PedidoAdmin, PedidosPaginadosAdmin } from '../domain/pedido-admin.model';
import { RepositorioPedidosAdmin } from '../domain/repositorio-pedidos-admin.puerto';
import { aPedidoAdmin, aPedidosPaginadosAdmin } from './mapeador-pedido-admin';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer` — de ahí el cliente autenticado en
 * vez del `crearClienteContratos` a secas que usan los repositorios públicos. */
@Injectable()
export class PedidosAdminHttpRepositorio implements RepositorioPedidosAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroPedidosAdmin): Promise<PedidosPaginadosAdmin> {
    const { data, error } = await this.cliente.GET('/api/v1/admin/pedidos', {
      params: { query: { pagina: filtro.pagina, tamano: filtro.tamano, estado: filtro.estado ?? undefined } },
    });
    if (error) {
      throw new Error('No se pudo listar los pedidos.');
    }
    return aPedidosPaginadosAdmin(data);
  }

  async conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/conciliar-transferencia', {
      params: { path: { id: pedidoId } },
    });
    if (error) {
      throw new Error('No se pudo conciliar la transferencia.');
    }
    return aPedidoAdmin(data);
  }

  async verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/verificar-contraentrega', {
      params: { path: { id: pedidoId } },
      body: { motivo },
    });
    if (error) {
      throw new Error('No se pudo verificar la contraentrega.');
    }
    return aPedidoAdmin(data);
  }

  async despachar(pedidoId: string, transportadora: string, guia: string, costoEnvio: number): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/despacho', {
      params: { path: { id: pedidoId } },
      body: { transportadora, guia, costoEnvio },
    });
    if (error) {
      throw new Error('No se pudo despachar el pedido.');
    }
    return aPedidoAdmin(data);
  }

  async marcarEntregado(pedidoId: string): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/entrega', {
      params: { path: { id: pedidoId } },
    });
    if (error) {
      throw new Error('No se pudo marcar el pedido como entregado.');
    }
    return aPedidoAdmin(data);
  }

  async rechazarEnEntrega(pedidoId: string, motivo: string): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/rechazo-entrega', {
      params: { path: { id: pedidoId } },
      body: { motivo },
    });
    if (error) {
      throw new Error('No se pudo registrar el rechazo en la entrega.');
    }
    return aPedidoAdmin(data);
  }

  async conciliarRecaudo(pedidoId: string, comisionRecaudo: number): Promise<PedidoAdmin> {
    const { data, error } = await this.cliente.POST('/api/v1/admin/pedidos/{id}/recaudo', {
      params: { path: { id: pedidoId } },
      body: { comisionRecaudo },
    });
    if (error) {
      throw new Error('No se pudo conciliar el recaudo.');
    }
    return aPedidoAdmin(data);
  }
}
