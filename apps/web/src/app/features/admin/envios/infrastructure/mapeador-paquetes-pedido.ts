import type { components } from '@tecnosport/contratos';
import { PaqueteParaGuia, PaquetesDePedido } from '../domain/paquetes-de-pedido.model';

type PaquetesDto = components['schemas']['PaquetesDePedidoRespuesta'];
type PaqueteDto = components['schemas']['PaqueteRespuesta'];

/** DTO generado -> modelo propio. Todos los campos vienen marcados como obligatorios. */
export function aPaquetesDePedido(dto: PaquetesDto): PaquetesDePedido {
  return {
    paquetes: dto.paquetes.map(aPaqueteParaGuia),
    conRecaudo: dto.conRecaudo,
  };
}

function aPaqueteParaGuia(dto: PaqueteDto): PaqueteParaGuia {
  return {
    pesoKg: dto.pesoKg,
    largoCm: dto.largoCm,
    anchoCm: dto.anchoCm,
    altoCm: dto.altoCm,
    valorDeclarado: { valor: dto.valorDeclarado.valor, moneda: dto.valorDeclarado.moneda },
    contenido: dto.contenido,
  };
}
