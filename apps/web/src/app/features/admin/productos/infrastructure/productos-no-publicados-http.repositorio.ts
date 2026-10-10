import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { TandaDeProductosEliminados } from '../domain/producto-admin.model';
import { RepositorioProductosNoPublicados } from '../domain/productos-no-publicados.puerto';

@Injectable()
export class ProductosNoPublicadosHttpRepositorio implements RepositorioProductosNoPublicados {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async contar(): Promise<number> {
    const respuesta = await this.cliente.GET('/api/v1/admin/productos/no-publicados');
    return (
      desempaquetar(respuesta, 'no se pudieron contar los productos no publicados').cantidad ?? 0
    );
  }

  async eliminarTanda(
    desde: string | null,
    hasta: string | null,
  ): Promise<TandaDeProductosEliminados> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/productos/no-publicados', {
      params: { query: { ...(desde ? { desde } : {}), ...(hasta ? { hasta } : {}) } },
    });
    const tanda = desempaquetar(respuesta, 'no se pudieron borrar los productos no publicados');
    return {
      eliminados: tanda.eliminados ?? 0,
      conservadosPorVentas: tanda.conservadosPorVentas ?? 0,
      conservadosPorExistencias: tanda.conservadosPorExistencias ?? 0,
      siguiente: tanda.siguiente ?? null,
      hasta: tanda.hasta ?? null,
    };
  }
}
