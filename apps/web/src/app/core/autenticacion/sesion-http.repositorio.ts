import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../http/base-url';
import { desempaquetar, ErrorHttp } from '../http/respuesta-http';
import { aSesion } from './mapeador-sesion';
import { RepositorioSesion } from './repositorio-sesion.puerto';
import {
  ClaveActualIncorrectaError,
  CorreoSinVerificarError,
  DemasiadosIntentosError,
  SesionExpiradaError,
} from './sesion.errores';
import { Sesion } from './sesion.model';

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

  async iniciarSesion(correo: string, clave: string): Promise<Sesion> {
    const respuesta = await this.cliente.POST('/api/v1/auth/sesion', {
      body: { correo, clave },
    });
    if (respuesta.response.status === 403) {
      throw new CorreoSinVerificarError();
    }
    return aSesion(desempaquetar(respuesta, 'no se pudo iniciar sesión'));
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
