import type { components } from '@tecnosport/contratos';
import {
  AlertaBorrador,
  AprobarBorrador,
  Borrador,
  BorradorDetalle,
  BorradoresPaginados,
  EditarBorrador,
  EstadoBorrador,
  FotoBorrador,
  Tallas,
  TipoDeTalla,
  TipoProductoProveedor,
} from '../domain/borrador.model';

type BorradorDto = components['schemas']['BorradorRespuesta'];
type BorradorDetalleDto = components['schemas']['BorradorDetalleRespuesta'];
type BorradoresPaginadosDto = components['schemas']['BorradoresPaginadosRespuesta'];
type TallasDto = components['schemas']['TallasRespuesta'];
type TallasPeticionDto = components['schemas']['TallasPeticion'];
type FotoDto = components['schemas']['FotoRespuesta'];
type EditarPeticionDto = components['schemas']['EditarBorradorPeticion'];
type AprobarPeticionDto = components['schemas']['AprobarBorradorPeticion'];

function aTallas(dto: TallasDto | undefined): Tallas {
  return {
    tipo: (dto?.tipo ?? 'DESCONOCIDA') as TipoDeTalla,
    sirveHasta: dto?.sirveHasta ?? null,
    valores: dto?.valores ?? [],
  };
}

function aTallasPeticion(tallas: Tallas): TallasPeticionDto {
  return {
    tipo: tallas.tipo,
    ...(tallas.sirveHasta ? { sirveHasta: tallas.sirveHasta } : {}),
    valores: [...tallas.valores],
  };
}

/**
 * DTO generado -> modelo del panel. Los enums llegan como `string` y se afirman: el backend
 * publica el nombre exacto (`mapeador-producto-admin.ts` hace lo mismo). Los precios son enteros
 * en pesos, sin decimales, como en todo el sitio.
 */
export function aBorrador(dto: BorradorDto): Borrador {
  return {
    id: dto.id ?? '',
    proveedorId: dto.proveedorId ?? '',
    publicacionId: dto.publicacionId ?? '',
    estado: (dto.estado ?? 'EN_REVISION') as EstadoBorrador,
    titulo: dto.titulo ?? '',
    tipo: (dto.tipo ?? 'OTRO') as TipoProductoProveedor,
    linea: dto.linea ?? null,
    precioProveedor: dto.precioProveedor ?? null,
    precioVentaSugerido: dto.precioVentaSugerido ?? null,
    tallas: aTallas(dto.tallas),
    cantidadTonos: dto.cantidadTonos ?? 0,
    tonosNombrados: dto.tonosNombrados ?? [],
    material: dto.material ?? null,
    descripcion: dto.descripcion ?? null,
    altEn: dto.altEn ?? null,
    alertas: (dto.alertas ?? []) as AlertaBorrador[],
    motivoRechazo: dto.motivoRechazo ?? null,
    productoId: dto.productoId ?? null,
    creadoEn: dto.creadoEn ?? '',
    tallasPorTono: (dto.tallasPorTono ?? []).map((t) => ({ tono: t.tono, tallas: t.tallas })),
    preciosAdicionales: (dto.preciosAdicionales ?? []).map((p) => ({
      concepto: p.concepto,
      precio: p.precio,
    })),
  };
}

export function aFoto(dto: FotoDto): FotoBorrador {
  return {
    mensajeId: dto.mensajeId ?? '',
    url: dto.url ?? '',
    pieDeFoto: dto.pieDeFoto ?? null,
    origen: dto.origen,
    tonoSugerido: dto.tonoSugerido ?? null,
  };
}

export function aBorradorDetalle(dto: BorradorDetalleDto): BorradorDetalle {
  return {
    borrador: aBorrador(dto.borrador ?? {}),
    fotos: (dto.fotos ?? []).map(aFoto),
    textos: dto.textos ?? [],
  };
}

export function aBorradoresPaginados(dto: BorradoresPaginadosDto): BorradoresPaginados {
  return {
    items: (dto.items ?? []).map(aBorrador),
    pagina: dto.pagina ?? 0,
    totalPaginas: dto.totalPaginas ?? 1,
    totalBorradores: dto.totalBorradores ?? 0,
  };
}

/** Solo lo que vino: un campo ausente es «no lo toques», no «bórralo». */
export function aEditarPeticion(cambios: EditarBorrador): EditarPeticionDto {
  return {
    ...(cambios.titulo !== undefined ? { titulo: cambios.titulo } : {}),
    ...(cambios.tipo !== undefined ? { tipo: cambios.tipo } : {}),
    ...(cambios.precioVentaSugerido !== undefined
      ? { precioVentaSugerido: cambios.precioVentaSugerido }
      : {}),
    ...(cambios.tallas !== undefined ? { tallas: aTallasPeticion(cambios.tallas) } : {}),
    ...(cambios.cantidadTonos !== undefined ? { cantidadTonos: cambios.cantidadTonos } : {}),
    ...(cambios.tonosNombrados !== undefined
      ? { tonosNombrados: [...cambios.tonosNombrados] }
      : {}),
    ...(cambios.material !== undefined ? { material: cambios.material } : {}),
    ...(cambios.descripcion !== undefined ? { descripcion: cambios.descripcion } : {}),
    ...(cambios.altEn !== undefined ? { altEn: cambios.altEn } : {}),
  };
}

export function aAprobarPeticion(aprobacion: AprobarBorrador): AprobarPeticionDto {
  return {
    ...(aprobacion.titulo !== undefined ? { titulo: aprobacion.titulo } : {}),
    ...(aprobacion.descripcion !== undefined ? { descripcion: aprobacion.descripcion } : {}),
    marcaId: aprobacion.marcaId,
    categoriaId: aprobacion.categoriaId,
    precioVenta: aprobacion.precioVenta,
    ...(aprobacion.tallas !== undefined ? { tallas: aTallasPeticion(aprobacion.tallas) } : {}),
    fotos: aprobacion.fotos.map((foto) => ({
      mensajeId: foto.mensajeId,
      ...(foto.tono ? { tono: foto.tono } : {}),
      ...(foto.colorHex ? { colorHex: foto.colorHex } : {}),
      ...(foto.prenda ? { prenda: foto.prenda } : {}),
    })),
    altEs: aprobacion.altEs,
    altEn: aprobacion.altEn,
    existenciaInicial: aprobacion.existenciaInicial,
  };
}
