import { Injectable, inject } from '@angular/core';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { baseUrl } from '../../../../core/http/base-url';
import { desempaquetar } from '../../../../core/http/respuesta-http';
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
    const respuesta = await this.cliente.GET('/api/v1/admin/pedidos', {
      params: { query: { pagina: filtro.pagina, tamano: filtro.tamano, estado: filtro.estado ?? undefined } },
    });
    return aPedidosPaginadosAdmin(desempaquetar(respuesta, 'no se pudo listar los pedidos'));
  }

  async conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/conciliar-transferencia', {
      params: { path: { id: pedidoId } },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo conciliar la transferencia'));
  }

  async verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/verificar-contraentrega', {
      params: { path: { id: pedidoId } },
      body: { motivo },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo verificar la contraentrega'));
  }

  async despachar(pedidoId: string, transportadora: string, guia: string, costoEnvio: number): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/despacho', {
      params: { path: { id: pedidoId } },
      body: { transportadora, guia, costoEnvio },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo despachar el pedido'));
  }

  async marcarEntregado(pedidoId: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/entrega', {
      params: { path: { id: pedidoId } },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo marcar el pedido como entregado'));
  }

  async rechazarEnEntrega(pedidoId: string, motivo: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/rechazo-entrega', {
      params: { path: { id: pedidoId } },
      body: { motivo },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo registrar el rechazo en la entrega'));
  }

  async conciliarRecaudo(pedidoId: string, comisionRecaudo: number): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/recaudo', {
      params: { path: { id: pedidoId } },
      body: { comisionRecaudo },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo conciliar el recaudo'));
  }
}
