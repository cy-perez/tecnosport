import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { DemasiadosIntentosError } from '../../../core/autenticacion/sesion.errores';
import { ErrorHttp, exigirExito } from '../../../core/http/respuesta-http';
import { CorreoYaRegistradoError } from '../domain/cuenta.errores';
import { RepositorioCuenta } from '../domain/repositorio-cuenta.puerto';

/**
 * Los fallos salen de aquí como `ErrorHttp` —vía `exigirExito`— y no como un `Error` a secas, y esa
 * distinción no es de estilo: las pantallas deciden a quién atribuir el fallo con
 * `esFalloDelServidor`, que cuenta como fallo del servidor **todo lo que no sea un `ErrorHttp` de
 * 4xx**. Mientras este adaptador lanzó `Error`, esa función respondía `true` siempre y las ramas de
 * 4xx de `RestablecerClavePage` y `VerificarCorreoPage` eran **código muerto**: un enlace vencido
 * —el fallo más común de las dos— se anunciaba como "no pudimos conectarnos con el servidor", que
 * manda a reintentar lo que nunca va a funcionar en vez de a pedir un enlace nuevo.
 *
 * Las pruebas no lo vieron porque **sus dobles sí lanzaban `ErrorHttp`**: el doble era más correcto
 * que el código real, así que la prueba de "con un token que el servidor rechaza" pasaba en verde
 * ejercitando una rama que en producción nadie alcanzaba. Por eso existe ahora
 * `cuenta-http.repositorio.spec.ts`, que prueba este archivo contra `fetch` y no contra un doble.
 *
 * El 429 se traduce a {@link DemasiadosIntentosError} por lo mismo que en `SesionHttpRepositorio`:
 * las cuatro rutas de aquí llevan techo por IP (`ConfiguracionLimiteIntentos`), y sin traducirlo la
 * pantalla lee "demasiados intentos" como "tu enlace no sirve" y manda a pedir otro que tampoco va
 * a poder usar.
 */
@Injectable()
export class CuentaHttpRepositorio implements RepositorioCuenta {
  private readonly cliente = crearClienteContratos(baseUrl());

  async registrar(correo: string, clave: string, autorizaDatos: boolean): Promise<void> {
    const resultado = await this.cliente.POST('/api/v1/auth/registro', {
      body: { correo, clave, autorizaDatos },
    });
    if (resultado.response.status === 409) {
      throw new CorreoYaRegistradoError();
    }
    if (resultado.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    exigirExito(resultado, 'no se pudo crear la cuenta');
  }

  async verificarCorreo(token: string): Promise<void> {
    const resultado = await this.cliente.POST('/api/v1/auth/verificacion', {
      body: { token },
    });
    if (resultado.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    exigirExito(resultado, 'no se pudo verificar el correo');
  }

  async reenviarVerificacion(correo: string): Promise<void> {
    const resultado = await this.cliente.POST('/api/v1/auth/verificacion/reenviar', {
      body: { correo },
    });
    if (resultado.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    // El 204 no distingue entre los tres desenlaces, y eso es el contrato. Lo que sí hay que
    // propagar es un servidor caído: quien pide el enlace tiene que saber que no se pidió.
    exigirExito(resultado, 'no se pudo reenviar el correo de verificación');
  }

  /**
   * El 204 se resuelve sin mirar nada, exista o no una cuenta con ese correo: distinguirlos diría
   * desde fuera qué correos tienen cuenta aquí, y ese es el contrato del backend.
   *
   * **Pero el 429 y el 5xx sí se propagan**, y hasta el 22 de septiembre de 2026 no: se tragaban
   * como todo lo demás, así que quien pedía el enlace se quedaba mirando el buzón de un correo que
   * nunca salió. El 204-siempre protege contra revelar la existencia de una cuenta; un límite de
   * intentos por IP y una caída del servidor **no dicen nada de ninguna cuenta**, así que callarlos
   * no protege nada y sí engaña.
   */
  async solicitarRecuperacion(correo: string): Promise<void> {
    const { response } = await this.cliente.POST('/api/v1/auth/recuperacion', { body: { correo } });
    if (response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    if (response.status >= 500) {
      throw new ErrorHttp(response.status, 'no se pudo pedir el enlace de recuperación');
    }
  }

  async restablecerClave(token: string, claveNueva: string): Promise<void> {
    const resultado = await this.cliente.POST('/api/v1/auth/recuperacion/confirmar', {
      body: { token, claveNueva },
    });
    if (resultado.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    exigirExito(resultado, 'no se pudo restablecer la clave');
  }
}
