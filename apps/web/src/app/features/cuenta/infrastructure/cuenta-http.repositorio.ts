import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { CorreoYaRegistradoError } from '../domain/cuenta.errores';
import { RepositorioCuenta } from '../domain/repositorio-cuenta.puerto';

@Injectable()
export class CuentaHttpRepositorio implements RepositorioCuenta {
  private readonly cliente = crearClienteContratos(baseUrl());

  async registrar(correo: string, clave: string): Promise<void> {
    const { response } = await this.cliente.POST('/api/v1/auth/registro', {
      body: { correo, clave },
    });
    if (response.status === 409) {
      throw new CorreoYaRegistradoError();
    }
    if (!response.ok) {
      throw new Error('No se pudo crear la cuenta.');
    }
  }

  async verificarCorreo(token: string): Promise<void> {
    const { response } = await this.cliente.POST('/api/v1/auth/verificacion', {
      body: { token },
    });
    if (!response.ok) {
      throw new Error('No se pudo verificar el correo.');
    }
  }

  async solicitarRecuperacion(correo: string): Promise<void> {
    // Siempre resuelve — el propio backend responde 204 exista o no una cuenta con ese correo, así
    // que no hay nada que distinguir ni propagar aquí.
    await this.cliente.POST('/api/v1/auth/recuperacion', { body: { correo } });
  }

  async restablecerClave(token: string, claveNueva: string): Promise<void> {
    const { response } = await this.cliente.POST('/api/v1/auth/recuperacion/confirmar', {
      body: { token, claveNueva },
    });
    if (!response.ok) {
      throw new Error('No se pudo restablecer la clave.');
    }
  }
}
