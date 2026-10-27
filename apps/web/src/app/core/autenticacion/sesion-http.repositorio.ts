import { Injectable } from '@angular/core';
import { crearClienteContratos, type components } from '@tecnosport/contratos';
import { baseUrl } from '../http/base-url';
import { desempaquetar, ErrorHttp } from '../http/respuesta-http';
import { aDesafio, aSesion } from './mapeador-sesion';
import { RepositorioSesion } from './repositorio-sesion.puerto';
import {
  ClaveActualIncorrectaError,
  CodigoSegundoFactorInvalidoError,
  CorreoSinVerificarError,
  CuentaExistenteRequiereClaveError,
  CuentaGoogleSinRegistroError,
  DemasiadosIntentosError,
  DesafioSegundoFactorVencidoError,
  SesionExpiradaError,
} from './sesion.errores';
import { ResultadoInicioSesion, Sesion } from './sesion.model';

type SesionDto = components['schemas']['SesionRespuesta'];
type DesafioDto = components['schemas']['DesafioSegundoFactorRespuesta'];

/**
 * `/auth/refresco` y `/auth/cierre` dependen de la cookie `HttpOnly` de
 * refresco (`docs/08-seguridad-legal.md`) — nunca se lee ni se manda a mano
 * desde el frontend, JS no puede leer una cookie `HttpOnly` de todos modos.
 * El navegador la adjunta solo: `fetch` manda cookies por defecto en
 * peticiones del mismo origen (`credentials: 'same-origin'` es el valor por
 * defecto), y `baseUrl()` en el navegador siempre es relativa —
 * `proxy.conf.json` en desarrollo y el balanceador en producción hacen que
 * la API quede en el mismo origen (`docs/07-infra-gcp.md`).
 */
@Injectable()
export class SesionHttpRepositorio implements RepositorioSesion {
  private readonly cliente = crearClienteContratos(baseUrl());

  async iniciarSesion(correo: string, clave: string): Promise<ResultadoInicioSesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/sesion', {
      body: { correo, clave },
    });
    if (respuesta.response.status === 403) {
      throw new CorreoSinVerificarError();
    }
    // 429 y no un `ErrorHttp` pelado: quien llega al límite tiene la clave bien, y dejar que la
    // pantalla lo pinte como "correo o clave incorrectos" manda a cambiar una clave que no tiene
    // nada de malo. Los dos límites responden 429 —el de la cuenta, en `IniciarSesion`, y el de
    // la IP, en `FiltroLimiteIntentos`— y para quien mira la pantalla son lo mismo: espera.
    if (respuesta.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    const cuerpo = desempaquetar(respuesta, 'no se pudo iniciar sesión');
    // `ok` es cierto con el 200 y con el 202, y el tipo generado es la unión de los dos cuerpos: lo
    // que separa "sesión abierta" de "falta el código" es el estado, no la forma del JSON
    // (ADR-0096). El 202 no trae cookie ni token.
    if (respuesta.response.status === 202) {
      return aDesafio(cuerpo as DesafioDto);
    }
    return { tipo: 'sesion', sesion: aSesion(cuerpo as SesionDto) };
  }

  /**
   * El segundo paso del inicio de sesión (ADR-0096). Público como el primero: todavía no hay token.
   *
   * <p>**Dos 401 distintos**, y la pantalla hace cosas opuestas con cada uno: con
   * `CODIGO_SEGUNDO_FACTOR_INVALIDO` el desafío sigue vivo y basta con escribir otro código; con
   * `DESAFIO_SEGUNDO_FACTOR_INVALIDO` —vencido, usado, o cinco fallos— ningún código va a servir y
   * hay que volver a la clave. Confundirlos deja a alguien tecleando códigos buenos contra un
   * desafío muerto.
   */
  async verificarSegundoFactor(desafio: string, codigo: string): Promise<Sesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/segundo-factor/verificacion', {
      body: { desafio, codigo },
    });
    try {
      return aSesion(desempaquetar(respuesta, 'no se pudo verificar el segundo factor'));
    } catch (error) {
      if (!(error instanceof ErrorHttp)) {
        throw error;
      }
      if (error.codigo === 'CODIGO_SEGUNDO_FACTOR_INVALIDO') {
        throw new CodigoSegundoFactorInvalidoError();
      }
      if (error.codigo === 'DESAFIO_SEGUNDO_FACTOR_INVALIDO') {
        throw new DesafioSegundoFactorVencidoError();
      }
      if (error.estado === 429) {
        throw new DemasiadosIntentosError();
      }
      throw error;
    }
  }

  /**
   * Los dos 409 de negocio se distinguen por el `codigo` y no por el estado: el mismo endpoint
   * puede responder otro 409 —dos primeras entradas a la vez chocan con el índice único— y
   * decirle a esa persona "no hay una cuenta con ese correo" sería mentirle.
   */
  async iniciarSesionConGoogle(credencial: string, autorizaDatos: boolean): Promise<Sesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/google', {
      body: { credencial, autorizaDatos },
    });
    if (respuesta.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    try {
      return aSesion(desempaquetar(respuesta, 'no se pudo iniciar sesión con Google'));
    } catch (error) {
      if (error instanceof ErrorHttp && error.codigo === 'CUENTA_GOOGLE_SIN_REGISTRO') {
        throw new CuentaGoogleSinRegistroError();
      }
      if (error instanceof ErrorHttp && error.codigo === 'CUENTA_EXISTENTE_REQUIERE_CLAVE') {
        throw new CuentaExistenteRequiereClaveError();
      }
      throw error;
    }
  }

  /**
   * `204` es la respuesta normal de una visita anónima: no llegó cookie de refresco, así que no
   * hay sesión que refrescar. El servidor la distingue a propósito del `401` —cookie que llega
   * pero no sirve— porque el navegador registra en la consola todo 4xx de una petición, y esta
   * sale en cada primera carga de cualquier visitante.
   */
  async refrescar(): Promise<Sesion | null> {
    const respuesta = await this.cliente.POST('/api/v1/auth/refresco');
    if (respuesta.response.status === 204 || respuesta.response.status === 401) {
      return null;
    }
    return aSesion(desempaquetar(respuesta, 'no se pudo refrescar la sesión'));
  }

  /**
   * La cabecera se pone a mano por lo que dice el puerto: aquí no puede usarse
   * `crearClienteAutenticado`.
   *
   * <p>**Dos cosas distintas responden 401**: la clave actual equivocada
   * (`CREDENCIALES_INVALIDAS`) y el token que venció (`NO_AUTENTICADO`, que pone el punto de
   * entrada de la cadena de seguridad). Se separan por el `codigo` del cuerpo, que es el valor
   * estable del `ProblemDetail`, y no por el estado: confundirlos le diría "esa no es tu clave" a
   * quien la escribió bien.
   */
  async cambiarClave(
    accessToken: string,
    claveActual: string,
    claveNueva: string,
  ): Promise<Sesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/clave', {
      body: { claveActual, claveNueva },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    try {
      return aSesion(desempaquetar(respuesta, 'no se pudo cambiar la clave'));
    } catch (error) {
      if (!(error instanceof ErrorHttp)) {
        throw error;
      }
      if (error.codigo === 'CREDENCIALES_INVALIDAS') {
        throw new ClaveActualIncorrectaError();
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

  async cerrarSesion(): Promise<void> {
    await this.cliente.POST('/api/v1/auth/cierre');
  }
}
