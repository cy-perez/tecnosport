import type { components } from '@tecnosport/contratos';
import {
  CategoriaAdmin,
  EstadoProducto,
  ImagenAdmin,
  ImagenDeGaleriaAdmin,
  ExistenciaAjustada,
  ExistenciaDeVariante,
  ExistenciasDelCatalogo,
  InventarioSinMedir,
  MedidaDeVariante,
  MedidasDelCatalogo,
  MarcaAdmin,
  ProductoAdmin,
  ProductoAdminDetalle,
  ProductosPaginadosAdmin,
  VarianteMedida,
  VarianteSinMedir,
} from '../domain/producto-admin.model';

type ProductoDto = components['schemas']['ProductoAdminRespuesta'];
type ProductosPaginadosDto = components['schemas']['ProductosAdminPaginadosRespuesta'];
type MarcaDto = components['schemas']['MarcaRespuesta'];
type CategoriaDto = components['schemas']['CategoriaRespuesta'];
type ImagenDto = components['schemas']['ImagenRespuesta'];
type ProductoDetalleDto = components['schemas']['ProductoAdminDetalleRespuesta'];
type ImagenDeGaleriaDto = components['schemas']['ImagenDeGaleriaRespuesta'];
type SinMedirDto = components['schemas']['VariantesSinMedirRespuesta'];
type VarianteSinMedirDto = components['schemas']['VarianteSinMedirRespuesta'];
type MedidasDto = components['schemas']['MedidasRespuesta'];
type MedidaDeVarianteDto = components['schemas']['MedidaDeVarianteRespuesta'];
type ExistenciasDto = components['schemas']['ExistenciasRespuesta'];
type ExistenciaDeVarianteDto = components['schemas']['ExistenciaDeVarianteRespuesta'];
type ExistenciaAjustadaDto = components['schemas']['ExistenciaAjustadaRespuesta'];
type VarianteMedidaDto = components['schemas']['VarianteMedidaRespuesta'];

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

export function aProductoAdminDetalle(dto: ProductoDetalleDto): ProductoAdminDetalle {
  return {
    ...aProductoAdmin(dto),
    galeria: (dto.galeria ?? []).map(aImagenDeGaleriaAdmin),
  };
}

export function aImagenDeGaleriaAdmin(dto: ImagenDeGaleriaDto): ImagenDeGaleriaAdmin {
  return {
    id: dto.id ?? '',
    url: dto.url ?? '',
    ancho: dto.ancho ?? 0,
    alto: dto.alto ?? 0,
    orden: dto.orden ?? 0,
    altEs: dto.altEs ?? '',
    altEn: dto.altEn ?? '',
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
  return {
    id: dto?.id ?? '',
    nombre: dto?.nombre ?? '',
    slug: dto?.slug ?? '',
    linea: dto?.linea ?? '',
  };
}

export function aImagenAdmin(dto: ImagenDto): ImagenAdmin {
  return {
    url: dto.url ?? '',
    ancho: dto.ancho ?? 0,
    alto: dto.alto ?? 0,
    altEs: dto.altEs ?? '',
    altEn: dto.altEn ?? '',
  };
}

/**
 * Los conteos se leen del DTO y no se calculan sobre `items`: son la misma cifra medida en el
 * servidor, y derivarla aquí crearía una segunda definición capaz de divergir.
 */
export function aInventarioSinMedir(dto: SinMedirDto): InventarioSinMedir {
  return {
    total: dto.total ?? 0,
    totalEnPublicados: dto.totalEnPublicados ?? 0,
    items: (dto.items ?? []).map(aVarianteSinMedir),
  };
}

export function aMedidasDelCatalogo(dto: MedidasDto): MedidasDelCatalogo {
  return {
    total: dto.total ?? 0,
    totalSinMedir: dto.totalSinMedir ?? 0,
    totalSinMedirEnPublicados: dto.totalSinMedirEnPublicados ?? 0,
    items: (dto.items ?? []).map(aMedidaDeVariante),
  };
}

/**
 * Las cuatro cifras se mapean a `null` y no a `0` cuando faltan: un cero es una medida inválida
 * —el dominio exige mayor que cero— y pintarlo diría que la variante mide cero en vez de que
 * nadie la ha medido.
 */
function aMedidaDeVariante(dto: MedidaDeVarianteDto): MedidaDeVariante {
  return {
    varianteId: dto.varianteId ?? '',
    productoId: dto.productoId ?? '',
    nombreProducto: dto.nombreProducto ?? '',
    sku: dto.sku ?? '',
    estadoProducto: (dto.estadoProducto ?? 'BORRADOR') as EstadoProducto,
    pesoGramos: dto.pesoGramos ?? null,
    largoCm: dto.largoCm ?? null,
    anchoCm: dto.anchoCm ?? null,
    altoCm: dto.altoCm ?? null,
    sinMedir: dto.sinMedir ?? true,
  };
}

function aVarianteSinMedir(dto: VarianteSinMedirDto): VarianteSinMedir {
  return {
    varianteId: dto.varianteId ?? '',
    productoId: dto.productoId ?? '',
    nombreProducto: dto.nombreProducto ?? '',
    sku: dto.sku ?? '',
    estadoProducto: (dto.estadoProducto ?? 'BORRADOR') as EstadoProducto,
  };
}

export function aVarianteMedida(dto: VarianteMedidaDto): VarianteMedida {
  return {
    varianteId: dto.varianteId ?? '',
    sku: dto.sku ?? '',
    pesoGramos: dto.pesoGramos ?? 0,
    largoCm: dto.largoCm ?? 0,
    anchoCm: dto.anchoCm ?? 0,
    altoCm: dto.altoCm ?? 0,
    correccion: dto.correccion ?? false,
  };
}

/** Mismo criterio que `aInventarioSinMedir`: los conteos se leen, no se derivan de `items`. */
export function aExistenciasDelCatalogo(dto: ExistenciasDto): ExistenciasDelCatalogo {
  return {
    total: dto.total ?? 0,
    totalSinExistencia: dto.totalSinExistencia ?? 0,
    totalSinExistenciaEnPublicados: dto.totalSinExistenciaEnPublicados ?? 0,
    items: (dto.items ?? []).map(aExistenciaDeVariante),
  };
}

function aExistenciaDeVariante(dto: ExistenciaDeVarianteDto): ExistenciaDeVariante {
  return {
    varianteId: dto.varianteId ?? '',
    productoId: dto.productoId ?? '',
    nombreProducto: dto.nombreProducto ?? '',
    sku: dto.sku ?? '',
    estadoProducto: (dto.estadoProducto ?? 'BORRADOR') as EstadoProducto,
    saldoTotal: dto.saldoTotal ?? 0,
    disponible: dto.disponible ?? 0,
    reservadas: dto.reservadas ?? 0,
  };
}

export function aExistenciaAjustada(dto: ExistenciaAjustadaDto): ExistenciaAjustada {
  return {
    varianteId: dto.varianteId ?? '',
    sku: dto.sku ?? '',
    nombreProducto: dto.nombreProducto ?? '',
    saldoAnterior: dto.saldoAnterior ?? 0,
    saldoNuevo: dto.saldoNuevo ?? 0,
    diferencia: dto.diferencia ?? 0,
    unidadesReservadas: dto.unidadesReservadas ?? 0,
    sinCambios: dto.sinCambios ?? false,
    dejaReservasSinRespaldo: dto.dejaReservasSinRespaldo ?? false,
  };
}
