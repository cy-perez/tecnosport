import { Injectable, inject } from '@angular/core';
import { Categoria } from '../../../catalogo/domain/producto.model';
import { RepositorioCategorias } from '../../../catalogo/domain/repositorio-categorias.puerto';
import { aCategoria } from '../../../catalogo/infrastructure/mapeador-productos';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';

/**
 * Mismo motivo que `MarcasAdminHttpRepositorio`, y aquí pesa más: la migración `V38` dejó la línea de
 * tecnología con once categorías y casi todas siguen sin un solo producto. Por el endpoint público
 * no sale ninguna de ellas, que es lo correcto para la vitrina y lo inservible para el panel.
 *
 * Con cliente autenticado desde el 19 de septiembre de 2026: `/api/v1/admin/**` exige rol ADMIN en
 * `ConfiguracionSeguridad`, y con el cliente sin token esta consulta respondía 403 y dejaba el
 * desplegable de categorías vacío. Comprobado con `curl` contra el backend real.
 */
@Injectable()
export class CategoriasAdminHttpRepositorio implements RepositorioCategorias {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async listarTodas(): Promise<Categoria[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/categorias');
    const datos = desempaquetar(respuesta, 'no se pudieron cargar las categorías');

    return (datos.items ?? []).map(aCategoria);
  }
}
