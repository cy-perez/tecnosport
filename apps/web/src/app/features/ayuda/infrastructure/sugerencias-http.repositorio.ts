import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { DemasiadosIntentosError } from '../../../core/autenticacion/sesion.errores';
import { exigirExito } from '../../../core/http/respuesta-http';
import { NuevaSugerencia, RepositorioSugerencias } from '../domain/repositorio-sugerencias.puerto';

/**
 * El adaptador del buzón.
 *
 * <p>El 429 se traduce a {@link DemasiadosIntentosError} por lo mismo que en el registro y en la
 * recuperación de clave: la ruta lleva techo por IP (`ConfiguracionLimiteIntentos`) y es estrecho a
 * propósito —cinco por hora—, así que este caso se alcanza de verdad. Sin traducirlo, la pantalla
 * diría "no pudimos enviar tu mensaje, intenta de nuevo" y mandaría a reintentar exactamente lo que
 * el límite acaba de rechazar.
 *
 * <p>El resto sale como `ErrorHttp` vía `exigirExito`, no como un `Error` a secas: es lo que
 * distingue un fallo del servidor de uno atribuible a lo que se escribió, y ya hubo un caso en este
 * proyecto en el que lanzar `Error` dejó ramas enteras de 4xx como código muerto.
 *
 * <p><b>El correo vacío no viaja: se omite del cuerpo.</b> No es lo mismo que mandar una cadena
 * vacía —el servidor tiene que leer "no lo dejó", porque de eso depende si exige la autorización de
 * datos— y tampoco puede ser `null`, porque el contrato lo declara `correo?: string` y el cliente
 * generado no acepta nulos. Omitirlo es lo que el contrato sí dice, y del lado del servidor llega
 * como nulo: Jackson 3 deja en nulo un componente de tipo referencia que falte (`apps/api/CLAUDE.md`).
 *
 * <p>Lo que <b>nunca</b> se puede omitir es `autorizaDatos`, que es un `boolean` primitivo: ahí
 * Jackson 3 no cae en el valor por omisión del tipo, revienta la deserialización entera y la
 * petición muere en 422. Por eso va siempre, incluso en falso.
 */
@Injectable()
export class SugerenciasHttpRepositorio implements RepositorioSugerencias {
  private readonly cliente = crearClienteContratos(baseUrl());

  async enviar(sugerencia: NuevaSugerencia): Promise<void> {
    const correo = sugerencia.correo?.trim() ?? '';
    const resultado = await this.cliente.POST('/api/v1/sugerencias', {
      body: {
        mensaje: sugerencia.mensaje,
        autorizaDatos: sugerencia.autorizaDatos,
        ...(correo === '' ? {} : { correo }),
      },
    });
    if (resultado.response.status === 429) {
      throw new DemasiadosIntentosError();
    }
    exigirExito(resultado, 'no se pudo enviar la sugerencia');
  }
}
