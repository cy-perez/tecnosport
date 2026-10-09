import type { components } from '@tecnosport/contratos';
import {
  BorradorTecnologia,
  ConfiguracionTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
} from '../domain/borrador-tecnologia.model';

type BorradorDto = components['schemas']['BorradorTecnologiaRespuesta'];
type ConfiguracionDto = components['schemas']['ConfiguracionRespuesta'];
type ElegirPeticionDto = components['schemas']['ElegirConfiguracionesPeticion'];

function aConfiguracion(dto: ConfiguracionDto): ConfiguracionTecnologia {
  return {
    sku: dto.sku,
    titulo: dto.titulo,
    ram: dto.ram ?? null,
    almacenamiento: dto.almacenamiento ?? null,
    sim: dto.sim ?? null,
    costoProveedor: dto.costoProveedor,
    precioMercado: dto.precioMercado ?? null,
    coloresSugeridos: dto.coloresSugeridos,
    coloresElegidos: dto.coloresElegidos,
    precioVenta: dto.precioVenta ?? null,
  };
}

/** DTO generado -> modelo del panel. `estado` llega como `string`: springdoc no publica el enum. */
export function aBorradorTecnologia(dto: BorradorDto): BorradorTecnologia {
  return {
    id: dto.id,
    proveedorId: dto.proveedorId,
    idModelo: dto.idModelo,
    titulo: dto.titulo,
    marcaSugerida: dto.marcaSugerida ?? null,
    categoriaSugerida: dto.categoriaSugerida ?? null,
    descripcion: dto.descripcion,
    paleta: dto.paleta,
    configuraciones: dto.configuraciones.map(aConfiguracion),
    productoId: dto.productoId ?? null,
    estado: dto.estado as EstadoBorradorTecnologia,
    motivoRechazo: dto.motivoRechazo ?? null,
    vistoEn: dto.vistoEn,
    creadoEn: dto.creadoEn,
  };
}

export function aElegirPeticion(elecciones: readonly EleccionDeConfiguracion[]): ElegirPeticionDto {
  return {
    configuraciones: elecciones.map((e) => ({
      sku: e.sku,
      colores: [...e.colores],
      ...(e.precioVenta === null ? {} : { precioVenta: e.precioVenta }),
    })),
  };
}
