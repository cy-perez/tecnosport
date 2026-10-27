import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { PrecioPorRecalcular } from '../domain/producto-admin.model';
import { RepositorioRecalculoPrecios } from '../domain/recalculo-precios.puerto';
import { aPreciosPorRecalcular } from './mapeador-recalculo-precios';

@Injectable()
export class RecalculoPreciosHttpRepositorio implements RepositorioRecalculoPrecios {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async previsualizar(): Promise<readonly PrecioPorRecalcular[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/precios/recalculo');
    return aPreciosPorRecalcular(
      desempaquetar(respuesta, 'no se pudieron leer los precios por recalcular'),
    );
  }

  async aplicar(): Promise<number> {
    const respuesta = await this.cliente.POST('/api/v1/admin/precios/recalculo');
    return desempaquetar(respuesta, 'no se pudieron recalcular los precios').recalculadas;
  }
}
