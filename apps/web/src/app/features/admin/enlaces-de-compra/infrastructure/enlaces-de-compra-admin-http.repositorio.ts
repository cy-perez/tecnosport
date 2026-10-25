import { Injectable, inject } from '@angular/core';
import type { components } from '@tecnosport/contratos';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { EnlaceDeCompraAdmin, EstadoEnlaceDeCompra } from '../domain/enlace-de-compra-admin.model';
import { RepositorioEnlacesDeCompraAdmin } from '../domain/repositorio-enlaces-de-compra-admin.puerto';

type EnlaceDto = components['schemas']['EnlaceDeCompraRespuesta'];

/** DTO generado -> modelo propio. El estado es el nombre del enum de Java, como en pedidos. */
export function aEnlaceDeCompraAdmin(dto: EnlaceDto): EnlaceDeCompraAdmin {
  return {
    id: dto.id,
    token: dto.token,
    varianteId: dto.varianteId,
    producto: dto.producto ?? null,
    slug: dto.slug ?? null,
    detalle: dto.detalle ?? null,
    estado: dto.estado as EstadoEnlaceDeCompra,
    creadoEn: dto.creadoEn,
    expiraEn: dto.expiraEn,
    pedidoId: dto.pedidoId ?? null,
  };
}

@Injectable()
export class EnlacesDeCompraAdminHttpRepositorio implements RepositorioEnlacesDeCompraAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listar(): Promise<EnlaceDeCompraAdmin[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/enlaces-de-compra');
    return desempaquetar(respuesta, 'no se pudieron cargar los enlaces de compra').map(
      aEnlaceDeCompraAdmin,
    );
  }

  async generar(varianteId: string): Promise<EnlaceDeCompraAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/enlaces-de-compra', {
      body: { varianteId },
    });
    return aEnlaceDeCompraAdmin(desempaquetar(respuesta, 'no se pudo generar el enlace'));
  }

  async anular(enlaceId: string): Promise<EnlaceDeCompraAdmin> {
    const respuesta = await this.cliente.POST('/api/v1/admin/enlaces-de-compra/{id}/anular', {
      params: { path: { id: enlaceId } },
    });
    return aEnlaceDeCompraAdmin(desempaquetar(respuesta, 'no se pudo anular el enlace'));
  }

  async definirPaleta(productoId: string, colores: readonly string[]): Promise<number> {
    const respuesta = await this.cliente.PUT('/api/v1/admin/productos/{id}/paleta', {
      params: { path: { id: productoId } },
      body: { colores: [...colores] },
    });
    return desempaquetar(respuesta, 'no se pudo guardar la paleta').variantesNuevas;
  }
}
