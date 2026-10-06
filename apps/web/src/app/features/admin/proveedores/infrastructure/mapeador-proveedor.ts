import type { components } from '@tecnosport/contratos';
import {
  DatosProveedor,
  LineaProveedor,
  OrdenDePublicacion,
  Proveedor,
} from '../domain/proveedor.model';

type ProveedorDto = components['schemas']['ProveedorRespuesta'];
type ProveedorPeticionDto = components['schemas']['ProveedorPeticion'];

/**
 * DTO generado -> modelo del panel. `linea` y `ordenDePublicacion` llegan como `string` (springdoc no publica el enum
 * como unión); el backend garantiza el nombre exacto, así que se afirma — mismo criterio que
 * `admin/productos/infrastructure/mapeador-producto-admin.ts`.
 */
export function aProveedor(dto: ProveedorDto): Proveedor {
  return {
    id: dto.id ?? '',
    nombre: dto.nombre ?? '',
    nombreEnExportacion: dto.nombreEnExportacion ?? '',
    telefonoWhatsApp: dto.telefonoWhatsApp ?? '',
    linea: (dto.linea ?? 'BOLSOS') as LineaProveedor,
    factorDeMargen: dto.factorDeMargen ?? 1,
    ordenDePublicacion: (dto.ordenDePublicacion ?? 'FOTOS_PRIMERO') as OrdenDePublicacion,
    publicacionAutomatica: dto.publicacionAutomatica ?? false,
    activo: dto.activo ?? true,
  };
}

export function aProveedorPeticion(datos: DatosProveedor): ProveedorPeticionDto {
  return {
    nombre: datos.nombre,
    nombreEnExportacion: datos.nombreEnExportacion,
    telefonoWhatsApp: datos.telefonoWhatsApp,
    linea: datos.linea,
    factorDeMargen: datos.factorDeMargen,
    ordenDePublicacion: datos.ordenDePublicacion,
    publicacionAutomatica: datos.publicacionAutomatica,
    activo: datos.activo,
  };
}
