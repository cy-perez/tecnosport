import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import { Categoria } from '../../../catalogo/domain/producto.model';
import { RepositorioCategorias } from '../../../catalogo/domain/repositorio-categorias.puerto';
import { aCategoria } from '../../../catalogo/infrastructure/mapeador-productos';
import {
  CambioDeCategoria,
  NuevaCategoria,
  RepositorioCategoriasAdmin,
  ResultadoEscritura,
} from '../domain/repositorio-categorias-admin.puerto';

/**
 * Los `codigo` del ProblemDetail que el backend manda al rechazar una escritura del árbol. Salen
 * del nombre de la excepción Java (`apps/api/CLAUDE.md`), así que renombrarla allá obliga a
 * cambiarlos aquí — lo fija `CodigosDeCableTest`.
 */
const CODIGOS: Readonly<Record<string, ResultadoEscritura['tipo']>> = {
  CATEGORIA_SLUG_YA_EXISTE: 'SLUG_REPETIDO',
  CATEGORIA_CON_PRODUCTOS: 'TIENE_PRODUCTOS',
  CATEGORIA_NO_ES_HOJA: 'TIENE_SUBCATEGORIAS',
  CATEGORIA_CON_HIJAS: 'TIENE_SUBCATEGORIAS',
  PROFUNDIDAD_DE_CATEGORIA_EXCEDIDA: 'DEMASIADO_PROFUNDA',
  CICLO_DE_CATEGORIAS: 'CICLO',
};

/**
 * El árbol de categorías del panel. Cumple los dos puertos —el de la vitrina (`listarTodas`, que es
 * lo que el formulario de producto necesita) y el del panel, que además escribe— y se provee bajo
 * los dos tokens, igual que `MarcasAdminHttpRepositorio`.
 *
 * Vivía en `admin/productos/infrastructure` cuando lo único que hacía era llenar un desplegable.
 * Se mudó aquí el 24 de septiembre de 2026 al ganar las tres escrituras: un adaptador que escribe
 * el catálogo entero no es un detalle del formulario de producto.
 *
 * Con cliente **autenticado**: `/api/v1/admin/**` exige rol ADMIN en `ConfiguracionSeguridad`, y
 * con el cliente sin token la consulta respondía 403 y dejaba el desplegable vacío.
 */
@Injectable()
export class CategoriasAdminHttpRepositorio
  implements RepositorioCategorias, RepositorioCategoriasAdmin
{
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarTodas(): Promise<Categoria[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/categorias');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las categorías');

    return (datos.items ?? []).map(aCategoria);
  }

  async crear(nueva: NuevaCategoria): Promise<ResultadoEscritura> {
    const respuesta = await this.cliente.POST('/api/v1/admin/categorias', {
      body: {
        nombre: nueva.nombre,
        slug: nueva.slug,
        linea: nueva.linea,
        padreId: nueva.padreId,
      },
    });

    const rechazo = rechazoDe(respuesta);
    if (rechazo) {
      return rechazo;
    }
    return {
      tipo: 'OK',
      categoria: aCategoria(desempaquetar(respuesta, 'no se pudo crear la categoría')),
    };
  }

  async editar(cambio: CambioDeCategoria): Promise<ResultadoEscritura> {
    const respuesta = await this.cliente.PUT('/api/v1/admin/categorias/{id}', {
      params: { path: { id: cambio.id } },
      body: {
        nombre: cambio.nombre,
        slug: cambio.slug,
        linea: cambio.linea,
        padreId: cambio.padreId,
      },
    });

    const rechazo = rechazoDe(respuesta);
    if (rechazo) {
      return rechazo;
    }
    return {
      tipo: 'OK',
      categoria: aCategoria(desempaquetar(respuesta, 'no se pudo editar la categoría')),
    };
  }

  async eliminar(id: string): Promise<ResultadoEscritura> {
    const respuesta = await this.cliente.DELETE('/api/v1/admin/categorias/{id}', {
      params: { path: { id } },
    });

    const rechazo = rechazoDe(respuesta);
    if (rechazo) {
      return rechazo;
    }
    // 204: no hay cuerpo que mapear, y `desempaquetar` sigue haciendo falta para que un fallo que
    // no sea uno de los rechazos conocidos no pase por bueno.
    desempaquetar(respuesta, 'no se pudo borrar la categoría');
    return { tipo: 'OK', categoria: null };
  }
}

/** El rechazo conocido que trae la respuesta, o `undefined` si no es uno de ellos. */
function rechazoDe(respuesta: {
  response: { ok: boolean };
  error?: unknown;
}): ResultadoEscritura | undefined {
  if (respuesta.response.ok) {
    return undefined;
  }
  const codigo = codigoDe(respuesta.error);
  const tipo = codigo === undefined ? undefined : CODIGOS[codigo];
  return tipo === undefined ? undefined : ({ tipo } as ResultadoEscritura);
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
