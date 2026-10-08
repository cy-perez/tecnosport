import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { PaquetesDePedido } from '../domain/paquetes-de-pedido.model';
import { RepositorioPaquetesPedido } from '../domain/repositorio-paquetes-pedido.puerto';
import { aPaquetesDePedido } from './mapeador-paquetes-pedido';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class PaquetesPedidoHttpRepositorio implements RepositorioPaquetesPedido {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async consultar(pedidoId: string): Promise<PaquetesDePedido> {
    const respuesta = await this.cliente.GET('/api/v1/admin/envios/paquetes/{pedidoId}', {
      params: { path: { pedidoId } },
    });
    return aPaquetesDePedido(
      desempaquetar(respuesta, 'no se pudieron calcular los paquetes del pedido'),
    );
  }
}
