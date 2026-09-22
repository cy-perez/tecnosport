import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../http/base-url';
import { desempaquetar } from '../http/respuesta-http';
import { aSesion } from './mapeador-sesion';
import { RepositorioSesion } from './repositorio-sesion.puerto';
import {
  ClaveActualIncorrectaError,
  CorreoSinVerificarError,
  DemasiadosIntentosError,
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
   * `crearClienteAutenticado`. El 401 se traduce a {@link ClaveActualIncorrectaError} y no a un
   * `ErrorHttp` pelado porque en esta petición solo significa una cosa —la clave actual está
   * mal—: el token viaja recién sacado de la sesión viva, y si estuviera vencido la pantalla ni
   * se habría podido abrir.
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
    if (respuesta.response.status === 401) {
      throw new ClaveActualIncorrectaError();
    }
    if (respuesta.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    return aSesion(desempaquetar(respuesta, 'no se pudo cambiar la clave'));
  }

  async cerrarSesion(): Promise<void> {
    await this.cliente.POST('/api/v1/auth/cierre');
  }
}
