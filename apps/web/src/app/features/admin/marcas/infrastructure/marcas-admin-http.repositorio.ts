import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { Marca } from '../../../catalogo/domain/producto.model';
import { RepositorioMarcas } from '../../../catalogo/domain/repositorio-marcas.puerto';
import { aMarca } from '../../../catalogo/infrastructure/mapeador-productos';
import {
  RepositorioMarcasAdmin,
  ResultadoCrearMarca,
} from '../domain/repositorio-marcas-admin.puerto';

/** El `codigo` del ProblemDetail que manda el backend cuando el nombre ya está tomado. */
const MARCA_YA_EXISTE = 'MARCA_YA_EXISTE';

/**
 * Las marcas del panel salen de `/api/v1/admin/marcas` y no del endpoint público, que desde el
 * 19 de septiembre de 2026 solo devuelve las que tienen algún producto publicado — es el filtro de
 * la vitrina. Si el panel usara aquel, la marca recién creada no aparecería en el desplegable y no
 * habría forma de cargarle su primer producto.
 *
 * Cumple los dos puertos: el de la vitrina (`listarTodas`, que es lo que el formulario de producto
 * necesita) y el del panel, que además crea. Se provee bajo los dos tokens.
 *
 * Con cliente **autenticado**, y esto era un defecto real hasta hoy: `/api/v1/admin/**` exige rol
 * ADMIN y este adaptador usaba el cliente sin token, así que el desplegable de marcas del
 * formulario de producto recibía un 403 y se quedaba vacío.
 */
@Injectable()
export class MarcasAdminHttpRepositorio implements RepositorioMarcas, RepositorioMarcasAdmin {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarTodas(): Promise<Marca[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/marcas');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las marcas');

    return (datos.items ?? []).map(aMarca);
  }

  async crear(nombre: string): Promise<ResultadoCrearMarca> {
    const respuesta = await this.cliente.POST('/api/v1/admin/marcas', { body: { nombre } });

    if (!respuesta.response.ok && codigoDe(respuesta.error) === MARCA_YA_EXISTE) {
      return { tipo: 'YA_EXISTE' };
    }

    return { tipo: 'CREADA', marca: aMarca(desempaquetar(respuesta, 'no se pudo crear la marca')) };
  }
}

/**
 * El `codigo` del cuerpo de error, si vino. Repetido de `respuesta-http`, que lo tiene privado:
 * `openapi-fetch` solo rellena `error` cuando el fallo trae JSON, así que aquí no se da nada por
 * hecho.
 */
function codigoDe(cuerpo: unknown): string | undefined {
  if (cuerpo === null || typeof cuerpo !== 'object' || !('codigo' in cuerpo)) {
    return undefined;
  }
  const codigo = (cuerpo as { codigo?: unknown }).codigo;
  return typeof codigo === 'string' && codigo !== '' ? codigo : undefined;
}
