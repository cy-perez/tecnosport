import type { components } from '@tecnosport/contratos';
import { EstadoLote, LoteIngesta, LotesPaginados, ResumenIngesta } from '../domain/ingesta.model';

type LoteDto = components['schemas']['LoteIngestaRespuesta'];
type LotesPaginadosDto = components['schemas']['LotesPaginadosRespuesta'];
type ResumenDto = components['schemas']['ResumenIngestaRespuesta'];

/** Un lote recién recibido todavía no tiene cifras: todas en cero y la pantalla lo pinta igual. */
function aResumen(dto: ResumenDto | undefined): ResumenIngesta {
  return {
    mensajesLeidos: dto?.mensajesLeidos ?? 0,
    mensajesNuevos: dto?.mensajesNuevos ?? 0,
    mensajesIgnorados: dto?.mensajesIgnorados ?? 0,
    publicaciones: dto?.publicaciones ?? 0,
    borradoresNuevos: dto?.borradoresNuevos ?? 0,
    renovaciones: dto?.renovaciones ?? 0,
    agotados: dto?.agotados ?? 0,
    descartes: dto?.descartes ?? 0,
    alertas: dto?.alertas ?? 0,
  };
}

export function aLoteIngesta(dto: LoteDto): LoteIngesta {
  return {
    id: dto.id ?? '',
    proveedorId: dto.proveedorId ?? '',
    estado: (dto.estado ?? 'RECIBIDO') as EstadoLote,
    creadoEn: dto.creadoEn ?? '',
    iniciadoEn: dto.iniciadoEn ?? null,
    terminadoEn: dto.terminadoEn ?? null,
    resumen: aResumen(dto.resumen),
    detalleError: dto.detalleError ?? null,
  };
}

export function aLotesPaginados(dto: LotesPaginadosDto): LotesPaginados {
  return {
    items: (dto.items ?? []).map(aLoteIngesta),
    pagina: dto.pagina ?? 0,
    totalPaginas: dto.totalPaginas ?? 1,
    totalLotes: dto.totalLotes ?? 0,
  };
}
