import { Injectable, inject } from '@angular/core';
import { crearClienteContratos, type components } from '@tecnosport/contratos';
import { aSesion } from '../../../../core/autenticacion/mapeador-sesion';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from '../../../../core/autenticacion/sesion.errores';
import { Sesion } from '../../../../core/autenticacion/sesion.model';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { desempaquetar, ErrorHttp } from '../../../../core/http/respuesta-http';
import { RepositorioSegundoFactor } from '../domain/repositorio-segundo-factor.puerto';
import {
  ActivacionIniciada,
  EstadoSegundoFactor,
  SesionConCodigos,
} from '../domain/segundo-factor.model';

type CodigosDto =
  | components['schemas']['ActivacionConfirmadaRespuesta']
  | components['schemas']['CodigosDeRespaldoRegeneradosRespuesta'];

/**
 * Con el cliente **sin** reintento y el token puesto a mano, como `cambiarClave` de
 * `SesionHttpRepositorio`, y por lo mismo: estos endpoints responden 401 por motivos de negocio —la
 * clave actual o el código equivocados—, y `crearClienteAutenticado` trataría cada uno como un
 * token vencido: refrescaría y repetiría la petición, que contaría doble contra el límite de
 * intentos del servidor, y si el refresco fallara cerraría la sesión de quien solo se equivocó de
 * código.
 *
 * <p>Sirve también con la sesión restringida (ADR-0096): esta ruta admite `ADMIN_POR_ACTIVAR`,
 * que es justo quien tiene que activar.
 */
@Injectable()
export class SegundoFactorHttpRepositorio implements RepositorioSegundoFactor {
  private readonly cliente = crearClienteContratos(baseUrl());
  private readonly sesionStore = inject(SesionStore);

  async consultar(): Promise<EstadoSegundoFactor> {
    const respuesta = await this.cliente.GET('/api/v1/auth/segundo-factor', {
      headers: this.autorizacion(),
    });
    const dto = this.desempaquetar(respuesta, 'no se pudo consultar el segundo factor');
    return {
      activo: dto.activo,
      // Llega `null`, no ausente, cuando no está activo: el tipo generado no lo dice.
      activadoEn: dto.activadoEn ?? null,
      codigosDeRespaldoRestantes: dto.codigosDeRespaldoRestantes,
      obligatorio: dto.obligatorio,
    };
  }

  async iniciarActivacion(claveActual: string): Promise<ActivacionIniciada> {
    const respuesta = await this.cliente.POST('/api/v1/auth/segundo-factor/activacion', {
      body: { claveActual },
      headers: this.autorizacion(),
    });
    const dto = this.desempaquetar(respuesta, 'no se pudo iniciar la activación');
    return {
      secreto: dto.secreto,
      uri: dto.uri,
      digitos: dto.digitos,
      periodoSegundos: dto.periodoSegundos,
    };
  }

  async confirmarActivacion(codigo: string): Promise<SesionConCodigos> {
    const respuesta = await this.cliente.POST('/api/v1/auth/segundo-factor/activacion/confirmar', {
      body: { codigo },
      headers: this.autorizacion(),
    });
    return aSesionConCodigos(this.desempaquetar(respuesta, 'no se pudo confirmar la activación'));
  }

  async regenerarCodigos(claveActual: string, codigo: string): Promise<SesionConCodigos> {
    const respuesta = await this.cliente.POST('/api/v1/auth/segundo-factor/codigos-de-respaldo', {
      body: { claveActual, codigo },
      headers: this.autorizacion(),
    });
    return aSesionConCodigos(this.desempaquetar(respuesta, 'no se pudieron regenerar los códigos'));
  }

  async desactivar(claveActual: string, codigo: string): Promise<Sesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/segundo-factor/desactivacion', {
      body: { claveActual, codigo },
      headers: this.autorizacion(),
    });
    return aSesion(this.desempaquetar(respuesta, 'no se pudo desactivar el segundo factor'));
  }

  private autorizacion(): Record<string, string> {
    const token = this.sesionStore.sesion()?.accessToken;
    if (!token) {
      throw new SesionExpiradaError();
    }
    return { Authorization: `Bearer ${token}` };
  }

  /**
   * Los 401 se separan por el `codigo` del cuerpo, no por el estado: la clave equivocada, el código
   * equivocado y el token vencido comparten número y piden a la persona cosas distintas.
   */
  private desempaquetar<T>(
    resultado: { data?: T; error?: unknown; response: Response },
    detalle: string,
  ): T {
    try {
      return desempaquetar(resultado, detalle);
    } catch (error) {
      if (!(error instanceof ErrorHttp)) {
        throw error;
      }
      if (error.codigo === 'CREDENCIALES_INVALIDAS') {
        throw new ClaveActualIncorrectaError();
      }
      if (error.codigo === 'CODIGO_SEGUNDO_FACTOR_INVALIDO') {
        throw new CodigoSegundoFactorInvalidoError();
      }
      if (error.codigo === 'NO_AUTENTICADO') {
        throw new SesionExpiradaError();
      }
      if (error.estado === 429) {
        throw new DemasiadosIntentosError();
      }
      throw error;
    }
  }
}

function aSesionConCodigos(dto: CodigosDto): SesionConCodigos {
  return { sesion: aSesion(dto.sesion), codigos: [...dto.codigosDeRespaldo] };
}
