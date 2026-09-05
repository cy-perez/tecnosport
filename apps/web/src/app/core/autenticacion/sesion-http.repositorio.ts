import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../http/base-url';
import { aSesion } from './mapeador-sesion';
import { RepositorioSesion } from './repositorio-sesion.puerto';
import { CorreoSinVerificarError } from './sesion.errores';
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
    const { data, error, response } = await this.cliente.POST('/api/v1/auth/sesion', {
      body: { correo, clave },
    });
    if (response.status === 403) {
      throw new CorreoSinVerificarError();
    }
    if (error) {
      throw new Error('Correo o clave incorrectos.');
    }
    return aSesion(data);
  }

  async refrescar(): Promise<Sesion | null> {
    const { data, error, response } = await this.cliente.POST('/api/v1/auth/refresco');
    if (response.status === 401) {
      return null;
    }
    if (error) {
      throw new Error('No se pudo refrescar la sesión.');
    }
    return aSesion(data);
  }

  async cerrarSesion(): Promise<void> {
    await this.cliente.POST('/api/v1/auth/cierre');
  }
}
