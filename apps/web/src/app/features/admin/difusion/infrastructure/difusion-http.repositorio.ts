import { Injectable, inject } from '@angular/core';
import { SesionStore } from '../../../../core/autenticacion/sesion.store';
import { baseUrl } from '../../../../core/http/base-url';
import { crearClienteAutenticado } from '../../../../core/http/cliente-autenticado';
import { desempaquetar } from '../../../../core/http/respuesta-http';
import {
  EstadoPublicacion,
  OrdenDeDifusion,
  PublicacionEnRed,
  RedSocial,
} from '../domain/difusion.model';
import { RepositorioDifusion, ResultadoDifusion } from '../domain/repositorio-difusion.puerto';

/**
 * Los `codigo` del ProblemDetail que el backend manda al rechazar una difusión. Salen del nombre de
 * la excepción Java (`apps/api/CLAUDE.md`), así que renombrarla allá obliga a cambiarlos aquí — lo
 * fija `CodigosDeCableTest`.
 */
const CODIGOS = {
  PRODUCTO_NO_DIFUNDIBLE: 'NO_DIFUNDIBLE',
  DIFUSION_REPETIDA: 'YA_EN_MARCHA',
} as const;

@Injectable()
export class DifusionHttpRepositorio implements RepositorioDifusion {
  private readonly cliente = crearClienteAutenticado(baseUrl(), inject(SesionStore));

  async difundir(orden: OrdenDeDifusion): Promise<ResultadoDifusion> {
    const respuesta = await this.cliente.POST('/api/v1/admin/productos/{id}/difusion', {
      params: { path: { id: orden.productoId } },
      body: {
        redes: [...orden.redes],
        // Ausente significa "arma el propuesto"; presente se usa tal cual. Mandar la cadena vacía
        // sería lo mismo que no mandarlo, pero decirlo explícito ahorra el viaje mental.
        pieDeFoto: orden.pieDeFoto === '' ? undefined : orden.pieDeFoto,
      },
    });

    if (!respuesta.response.ok) {
      const codigo = codigoDe(respuesta.error);
      if (codigo === 'DIFUSION_REPETIDA') {
        return { tipo: 'YA_EN_MARCHA' };
      }
      if (codigo === 'PRODUCTO_NO_DIFUNDIBLE') {
        // El mensaje del backend dice cuál de las tres cosas falta —imagen, precio o publicar—, y
        // eso no se puede reconstruir desde un código: se pasa entero a la pantalla.
        return { tipo: 'NO_DIFUNDIBLE', motivo: detalleDe(respuesta.error) };
      }
    }

    const datos = desempaquetar(respuesta, 'no se pudo difundir el producto');
    return { tipo: 'OK', publicaciones: (datos ?? []).map(aPublicacion) };
  }

  async historial(productoId: string): Promise<PublicacionEnRed[]> {
    const respuesta = await this.cliente.GET('/api/v1/admin/productos/{id}/difusion', {
      params: { path: { id: productoId } },
    });
    return (desempaquetar(respuesta, 'no se pudo cargar el historial de difusión') ?? []).map(
      aPublicacion,
    );
  }

  async proponerPie(productoId: string, red: RedSocial): Promise<string> {
    const respuesta = await this.cliente.GET('/api/v1/admin/productos/{id}/difusion/propuesta', {
      params: { path: { id: productoId }, query: { red } },
    });
    return desempaquetar(respuesta, 'no se pudo armar el pie de foto').pieDeFoto ?? '';
  }
}

function aPublicacion(dto: {
  id?: string;
  red?: string;
  estado?: string;
  idPublicacionExterna?: string | null;
  pieDeFoto?: string;
  urlImagen?: string;
  solicitadaEn?: string;
  publicadaEn?: string | null;
  detalleDelFallo?: string | null;
}): PublicacionEnRed {
  return {
    id: dto.id ?? '',
    red: (dto.red ?? 'FACEBOOK') as RedSocial,
    estado: (dto.estado ?? 'PENDIENTE') as EstadoPublicacion,
    // `?? null` y no `?? ''`: una publicación sin identificador externo es una que no salió, y la
    // cadena vacía sería un id que no lleva a ninguna parte.
    idPublicacionExterna: dto.idPublicacionExterna ?? null,
    pieDeFoto: dto.pieDeFoto ?? '',
    urlImagen: dto.urlImagen ?? '',
    solicitadaEn: dto.solicitadaEn ?? '',
    publicadaEn: dto.publicadaEn ?? null,
    detalleDelFallo: dto.detalleDelFallo ?? null,
  };
}

function codigoDe(cuerpo: unknown): keyof typeof CODIGOS | undefined {
  if (cuerpo === null || typeof cuerpo !== 'object' || !('codigo' in cuerpo)) {
    return undefined;
  }
  const codigo = (cuerpo as { codigo?: unknown }).codigo;
  return typeof codigo === 'string' && codigo in CODIGOS
    ? (codigo as keyof typeof CODIGOS)
    : undefined;
}

/** El `detail` del ProblemDetail, que es donde el backend escribe qué le falta al producto. */
function detalleDe(cuerpo: unknown): string {
  if (cuerpo === null || typeof cuerpo !== 'object' || !('detail' in cuerpo)) {
    return '';
  }
  const detalle = (cuerpo as { detail?: unknown }).detail;
  return typeof detalle === 'string' ? detalle : '';
}
