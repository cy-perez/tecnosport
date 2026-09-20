import { Injectable } from '@angular/core';
import { crearClienteContratos } from '@tecnosport/contratos';
import { baseUrl } from '../../../core/http/base-url';
import { desempaquetar, ErrorHttp } from '../../../core/http/respuesta-http';
import {
  ArticuloNoAsegurable,
  CotizarEnvioComando,
  ResultadoCotizacion,
} from '../domain/envio.model';
import { RepositorioEnvios } from '../domain/repositorio-envios.puerto';
import { aCotizacionEnvio } from './mapeador-envio';

/** El 409 que el backend usa para "no hay transportadora para este destino". */
const SIN_COBERTURA = 'ENVIO_SIN_COBERTURA';

/** Y el que usa para "esto vale más de lo que la transportadora asegura" (`ADR-0036`). */
const ARTICULO_NO_ASEGURABLE = 'ARTICULO_NO_ASEGURABLE';
const ARTICULO_SIN_MEDIDAS = 'ARTICULO_SIN_MEDIDAS';

/**
 * Y el tercero: la plataforma rechazó los datos de este envío. Va con 409 y no con el 503 de "no se
 * pudo cotizar" precisamente para que se pueda distinguir aquí — reintentar el 503 sirve, y
 * reintentar esto no, porque Skydropx deduplica las cotizaciones por contenido.
 */
const COTIZACION_RECHAZADA = 'COTIZACION_RECHAZADA';

/**
 * Los artículos culpables que viajan en el `ProblemDetail`, leídos con la misma desconfianza que
 * `codigoDe`: el cuerpo de un error no está tipado por el contrato, así que aquí no se da nada por
 * hecho. Si llegara vacío o con otra forma, la pantalla se queda sin nombres pero no se rompe — y
 * el comprador ve la frase, que es lo que no puede faltar.
 */
function articulosDe(cuerpo: unknown): readonly ArticuloNoAsegurable[] {
  if (cuerpo === null || typeof cuerpo !== 'object' || !('articulos' in cuerpo)) {
    return [];
  }
  const articulos = (cuerpo as { articulos?: unknown }).articulos;
  if (!Array.isArray(articulos)) {
    return [];
  }
  return articulos.flatMap((articulo: unknown) => {
    if (articulo === null || typeof articulo !== 'object') {
      return [];
    }
    const { varianteId, nombre } = articulo as { varianteId?: unknown; nombre?: unknown };
    return typeof varianteId === 'string' && typeof nombre === 'string' && nombre !== ''
      ? [{ varianteId, nombre }]
      : [];
  });
}

@Injectable()
export class EnvioHttpRepositorio implements RepositorioEnvios {
  private readonly cliente = crearClienteContratos(baseUrl());

  /**
   * `POST` y no `GET` aunque no cree nada: el cuerpo lleva la dirección de
   * entrega, y una dirección no va en una URL que queda escrita en los
   * registros del balanceador (`docs/03-api.md`).
   *
   * Los tres 409 de negocio se traducen a un resultado y no se propagan como
   * error: para quien compra no son fallas. Uno dice que a esa dirección hoy no
   * llega nadie; otro, que algo del carrito vale más de lo que la transportadora
   * asegura (`ADR-0036`); el tercero, que la plataforma rechazó los datos del
   * envío. Los tres terminan en la recogida y se dicen distinto, porque solo el
   * primero se arregla cambiando la dirección.
   * Cualquier otro fallo sí se lanza — confundir "se cayó algo" con "no hay
   * cobertura" manda al comprador a cambiar una dirección que estaba bien.
   */
  async cotizar(comando: CotizarEnvioComando): Promise<ResultadoCotizacion> {
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
      return {
        tipo: 'TARIFA',
        cotizacion: aCotizacionEnvio(desempaquetar(respuesta, 'no se pudo cotizar el envío')),
      };
    } catch (error) {
      if (error instanceof ErrorHttp && error.codigo === SIN_COBERTURA) {
        return { tipo: 'SIN_COBERTURA' };
      }
      if (error instanceof ErrorHttp && error.codigo === ARTICULO_NO_ASEGURABLE) {
        return { tipo: 'ARTICULO_NO_ASEGURABLE', articulos: articulosDe(respuesta.error) };
      }
      if (error instanceof ErrorHttp && error.codigo === ARTICULO_SIN_MEDIDAS) {
        return { tipo: 'ARTICULO_SIN_MEDIDAS', articulos: articulosDe(respuesta.error) };
      }
      if (error instanceof ErrorHttp && error.codigo === COTIZACION_RECHAZADA) {
        return { tipo: 'COTIZACION_RECHAZADA' };
      }
      throw error;
    }
  }
}
