import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar, exigirExito } from '../../../../core/http/respuesta-http';
import { MedidasDeReferencia, ReferenciasDeEnvio } from '../domain/referencias-envio.model';
import { RepositorioReferenciasEnvio } from '../domain/repositorio-referencias-envio.puerto';
import { aMedidasDeReferencia, aReferenciasDeEnvio } from './mapeador-referencias-envio';

/** Todo bajo `/api/v1/admin/**` exige `Authorization: Bearer`. */
@Injectable()
export class ReferenciasEnvioHttpRepositorio implements RepositorioReferenciasEnvio {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async consultar(): Promise<ReferenciasDeEnvio> {
    const respuesta = await this.cliente.GET('/api/v1/admin/envios/referencias', {});
    return aReferenciasDeEnvio(
      desempaquetar(respuesta, 'no se pudieron cargar las referencias de envio'),
    );
  }

  async fijarMedidas(medidas: MedidasDeReferencia): Promise<MedidasDeReferencia> {
    const respuesta = await this.cliente.PUT('/api/v1/admin/envios/referencias/medidas', {
      body: { largoCm: medidas.largoCm, anchoCm: medidas.anchoCm, altoCm: medidas.altoCm },
    });
    return aMedidasDeReferencia(
      desempaquetar(respuesta, 'no se pudieron guardar las medidas de referencia'),
    );
  }

  async fijarPeso(categoriaId: string, pesoGramos: number): Promise<void> {
    const respuesta = await this.cliente.PUT(
      '/api/v1/admin/envios/referencias/pesos/{categoriaId}',
      {
        params: { path: { categoriaId } },
        body: { pesoGramos },
      },
    );
    desempaquetar(respuesta, 'no se pudo guardar el peso de referencia');
  }

  async quitarPeso(categoriaId: string): Promise<void> {
    const respuesta = await this.cliente.DELETE(
      '/api/v1/admin/envios/referencias/pesos/{categoriaId}',
      { params: { path: { categoriaId } } },
    );
    exigirExito(respuesta, 'no se pudo quitar el peso de referencia');
  }
}
