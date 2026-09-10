import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import { DesenlaceGarantia, ReclamacionGarantia } from '../domain/garantia.model';
import { RepositorioGarantias } from '../domain/repositorio-garantias.puerto';
import { aReclamacionGarantia } from './mapeador-garantia';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class GarantiasHttpRepositorio implements RepositorioGarantias {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarDePedido(pedidoId: string): Promise<readonly ReclamacionGarantia[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/pedidos/{pedidoId}/garantias', {
      params: { path: { pedidoId } },
    });
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las garantias del pedido');
    return datos.map(aReclamacionGarantia);
  }

  async radicar(
    pedidoId: string,
    varianteId: string,
    descripcionDelFallo: string,
  ): Promise<ReclamacionGarantia> {
    const respuesta = await this.cliente.POST('/api/v1/admin/pedidos/{pedidoId}/garantias', {
      params: { path: { pedidoId } },
      body: { varianteId, descripcionDelFallo },
    });
    return aReclamacionGarantia(desempaquetar(respuesta, 'no se pudo radicar la garantia'));
  }

  async resolver(entrada: {
    reclamacionId: string;
    desenlace: DesenlaceGarantia;
    resumenParaElComprador: string;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<ReclamacionGarantia> {
    const respuesta = await this.cliente.POST('/api/v1/admin/garantias/{id}/resolucion', {
      params: { path: { id: entrada.reclamacionId } },
      body: {
        desenlace: entrada.desenlace,
        resumenParaElComprador: entrada.resumenParaElComprador,
        monto: entrada.monto ?? undefined,
        medio: entrada.medio ?? undefined,
        comprobante: entrada.comprobante ?? undefined,
      },
    });
    return aReclamacionGarantia(desempaquetar(respuesta, 'no se pudo resolver la garantia'));
  }
}
