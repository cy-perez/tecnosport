import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar, ErrorHttp } from '../../../core/http/respuesta-http';
import { CotizacionEnvio, CotizarEnvioComando } from '../domain/envio.model';
import { RepositorioEnvios } from '../domain/repositorio-envios.puerto';
import { aCotizacionEnvio } from './mapeador-envio';

/** El 409 que el backend usa para "no hay transportadora para este destino". */
const SIN_COBERTURA = 'ENVIO_SIN_COBERTURA';

@Injectable()
export class EnvioHttpRepositorio implements RepositorioEnvios {
  private readonly cliente = crearClienteContratos(baseUrl());

  /**
   * `POST` y no `GET` aunque no cree nada: el cuerpo lleva la dirección de
   * entrega, y una dirección no va en una URL que queda escrita en los
   * registros del balanceador (`docs/03-api.md`).
   *
   * El 409 con `ENVIO_SIN_COBERTURA` se traduce a `null` y no se propaga como
   * error: para quien compra no es una falla, es que a esa dirección hoy no
   * llega nadie, y la pantalla tiene que decirle eso y ofrecerle la recogida.
   * Cualquier otro fallo sí se lanza — confundir "se cayó algo" con "no hay
   * cobertura" manda al comprador a cambiar una dirección que estaba bien.
   */
  async cotizar(comando: CotizarEnvioComando): Promise<CotizacionEnvio | null> {
    const respuesta = await this.cliente.POST('/api/v1/envios/cotizacion', {
      body: {
        lineas: comando.lineas.map((linea) => ({
          varianteId: linea.varianteId,
          cantidad: linea.cantidad,
        })),
        direccion: {
          codigoDaneDepartamento: comando.direccion.codigoDaneDepartamento,
          departamento: comando.direccion.departamento,
          codigoDaneCiudad: comando.direccion.codigoDaneCiudad,
          ciudad: comando.direccion.ciudad,
          direccion: comando.direccion.direccion,
          indicaciones: comando.direccion.indicaciones ?? undefined,
          barrio: comando.direccion.barrio ?? undefined,
        },
      },
    });

    try {
      return aCotizacionEnvio(desempaquetar(respuesta, 'no se pudo cotizar el envío'));
    } catch (error) {
      if (error instanceof ErrorHttp && error.codigo === SIN_COBERTURA) {
        return null;
      }
      throw error;
    }
  }
}
