import type { components } from '@tecnosport/contratos';
import {
  CategoriaAdmin,
  EstadoProducto,
  ImagenAdmin,
  MarcaAdmin,
  ProductoAdmin,
  ProductosPaginadosAdmin,
} from '../domain/producto-admin.model';

type ProductoDto = components['schemas']['ProductoAdminRespuesta'];
type ProductosPaginadosDto = components['schemas']['ProductosAdminPaginadosRespuesta'];
type MarcaDto = components['schemas']['MarcaRespuesta'];
type CategoriaDto = components['schemas']['CategoriaRespuesta'];
type ImagenDto = components['schemas']['ImagenRespuesta'];

/**
 * DTO generado -> modelo propio del panel. `estado` llega como `string` en el contrato (springdoc
 * no expone el enum de Java como unión literal); el backend garantiza que el valor es exactamente
 * el nombre del enum, así que se afirma el tipo en vez de validarlo a mano — mismo criterio que
 * `admin/pedidos/infrastructure/mapeador-pedido-admin.ts`.
 */
export function aProductoAdmin(dto: ProductoDto): ProductoAdmin {
  return {
    id: dto.id ?? '',
    nombre: dto.nombre ?? '',
    descripcion: dto.descripcion ?? '',
    slug: dto.slug ?? '',
    estado: (dto.estado ?? 'BORRADOR') as EstadoProducto,
    marca: aMarca(dto.marca),
    categoria: aCategoria(dto.categoria),
    imagenPrincipalUrl: dto.imagenPrincipalUrl ?? null,
    totalVariantes: dto.totalVariantes ?? 0,
  };
}

export function aProductosPaginadosAdmin(dto: ProductosPaginadosDto): ProductosPaginadosAdmin {
  return {
    items: (dto.items ?? []).map(aProductoAdmin),
    pagina: dto.pagina ?? 0,
    totalPaginas: dto.totalPaginas ?? 0,
    totalProductos: dto.totalProductos ?? 0,
  };
}

function aMarca(dto?: MarcaDto): MarcaAdmin {
  return { id: dto?.id ?? '', nombre: dto?.nombre ?? '' };
}

function aCategoria(dto?: CategoriaDto): CategoriaAdmin {
  return { id: dto?.id ?? '', nombre: dto?.nombre ?? '', slug: dto?.slug ?? '', linea: dto?.linea ?? '' };
}

export function aImagenAdmin(dto: ImagenDto): ImagenAdmin {
  return {
    url: dto.url ?? '',
    urlWebp: dto.urlWebp ?? '',
    ancho: dto.ancho ?? 0,
    alto: dto.alto ?? 0,
    altEs: dto.altEs ?? '',
    altEn: dto.altEn ?? '',
  };
}
