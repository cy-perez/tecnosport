import { Injectable, inject } from '@angular/core';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { baseUrl } from '../../../../core/http/base-url';
import { desempaquetar, exigirExito } from '../../../../core/http/respuesta-http';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import {
  EmisionDeGuiaAdmin,
  FiltroPedidosAdmin,
  ModalidadRecaudo,
  EstadoPedido,
  MetodoPago,
  MotivoCancelacion,
  PagoSinPedidoAdmin,
  PedidoAdmin,
  PedidosPaginadosAdmin,
} from '../domain/pedido-admin.model';
import {
  GuiaDespachada,
  RepositorioPedidosAdmin,
} from '../domain/repositorio-pedidos-admin.puerto';
import { aEmisionDeGuia, aPedidoAdmin, aPedidosPaginadosAdmin } from './mapeador-pedido-admin';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer` — de ahí el cliente autenticado en
 * vez del `crearClienteContratos` a secas que usan los repositorios públicos. */
@Injectable()
export class PedidosAdminHttpRepositorio implements RepositorioPedidosAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(filtro: FiltroPedidosAdmin): Promise<PedidosPaginadosAdmin> {
    const respuesta = await this.cliente.GET('/api/v1/admin/pedidos', {
      params: {
        query: { pagina: filtro.pagina, tamano: filtro.tamano, estado: filtro.estado ?? undefined },
      },
    });
    return aPedidosPaginadosAdmin(desempaquetar(respuesta, 'no se pudo listar los pedidos'));
  }

  async conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST(
      '/api/v1/admin/pedidos/{id}/conciliar-transferencia',
      {
        params: { path: { id: pedidoId } },
      },
    );
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo conciliar la transferencia'));
  }

  async verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST(
      '/api/v1/admin/pedidos/{id}/verificar-contraentrega',
      {
        params: { path: { id: pedidoId } },
        body: { motivo },
      },
    );
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo verificar la contraentrega'));
  }

  async despachar(pedidoId: string, guias: readonly GuiaDespachada[]): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/despacho', {
      params: { path: { id: pedidoId } },
      body: { guias: [...guias] },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo despachar el pedido'));
  }

  async emitirGuia(pedidoId: string): Promise<EmisionDeGuiaAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/emitir-guia', {
      params: { path: { id: pedidoId } },
    });
    return aEmisionDeGuia(desempaquetar(respuesta, 'no se pudo emitir la guía'));
  }

  async confirmarInventario(pedidoId: string): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/confirmar-inventario', {
      params: { path: { id: pedidoId } },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo confirmar el inventario'));
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

  async listarPagosSinPedido(): Promise<PagoSinPedidoAdmin[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/pagos/sin-pedido');
    return desempaquetar(respuesta, 'no se pudieron cargar los pagos sin pedido').map((p) => ({
      pagoId: p.pagoId,
      referencia: p.referencia,
      metodoPago: p.metodoPago as MetodoPago,
      monto: { valor: p.monto.valor, moneda: p.monto.moneda },
      desde: p.desde,
      pedidoId: p.pedidoId,
      numeroPedido: p.numeroPedido,
      estadoPedido: p.estadoPedido as EstadoPedido,
      correo: p.correo,
    }));
  }

  async registrarReintegroDePagoSinPedido(entrada: {
    pagoId: string;
    medio: MedioReintegro;
    comprobante: string | null;
  }): Promise<void> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pagos/{id}/reintegro', {
      params: { path: { id: entrada.pagoId } },
      body: { medio: entrada.medio, comprobante: entrada.comprobante ?? undefined },
    });
    exigirExito(respuesta, 'no se pudo registrar el reintegro del pago');
  }

  async recibirDevolucion(entrada: {
    pedidoId: string;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/devolucion-rechazo', {
      params: { path: { id: entrada.pedidoId } },
      body: {
        medio: entrada.medio ?? undefined,
        comprobante: entrada.comprobante ?? undefined,
      },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo recibir la devolución'));
  }

  async conciliarRecaudo(
    pedidoId: string,
    modalidadRecaudo: ModalidadRecaudo,
    comisionRecaudo: number,
  ): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/recaudo', {
      params: { path: { id: pedidoId } },
      body: { modalidadRecaudo, comisionRecaudo },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo conciliar el recaudo'));
  }

  async cancelar(entrada: {
    pedidoId: string;
    motivo: MotivoCancelacion;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<PedidoAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{id}/cancelacion', {
      params: { path: { id: entrada.pedidoId } },
      body: {
        motivo: entrada.motivo,
        monto: entrada.monto ?? undefined,
        medio: entrada.medio ?? undefined,
        comprobante: entrada.comprobante ?? undefined,
      },
    });
    return aPedidoAdmin(desempaquetar(respuesta, 'no se pudo cancelar el pedido'));
  }
}
