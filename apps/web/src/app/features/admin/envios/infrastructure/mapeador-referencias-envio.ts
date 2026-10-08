import type { components } from '@tecnosport/contratos';
import {
  CategoriaConPeso,
  LineaConPromedio,
  MedidasDeReferencia,
  ReferenciasDeEnvio,
} from '../domain/referencias-envio.model';

type ReferenciasDto = components['schemas']['ReferenciasDeEnvioRespuesta'];
type CategoriaDto = components['schemas']['CategoriaConPesoRespuesta'];
type MedidasDto = components['schemas']['MedidasDeReferenciaRespuesta'];

/**
 * DTO generado -> modelo propio, mismo criterio que `mapeador-revision-envio.ts`: springdoc declara
 * todo opcional, y la línea llega como `string` aunque el backend solo mande una de las tres.
 *
 * Unas medidas a las que les falta una cifra se tratan como ausentes y no como un cero: un cero se
 * pintaría en el formulario como si alguien lo hubiera fijado así.
 */
export function aReferenciasDeEnvio(dto: ReferenciasDto): ReferenciasDeEnvio {
  return {
    medidas: dto.medidas ? aMedidasOpcionales(dto.medidas) : null,
    categorias: (dto.categorias ?? []).flatMap((categoria) => {
      const fila = aCategoriaConPeso(categoria);
      return fila ? [fila] : [];
    }),
  };
}

export function aMedidasDeReferencia(dto: MedidasDto): MedidasDeReferencia {
  return {
    largoCm: dto.largoCm ?? 0,
    anchoCm: dto.anchoCm ?? 0,
    altoCm: dto.altoCm ?? 0,
  };
}

function aMedidasOpcionales(dto: MedidasDto): MedidasDeReferencia | null {
  if (dto.largoCm == null || dto.anchoCm == null || dto.altoCm == null) {
    return null;
  }
  return aMedidasDeReferencia(dto);
}

const LINEAS: readonly LineaConPromedio[] = ['ROPA', 'CALZADO', 'BOLSOS'];

/**
 * Una fila sin id o de una línea que no se promedia se descarta, no se rellena: con el id vacío el
 * botón haría un `PUT .../pesos/` sin categoría, y una línea inventada pintaría "Ropa" donde no lo
 * es. El backend no las manda; el mapeador existe para no tener que creerle a ciegas.
 */
function aCategoriaConPeso(dto: CategoriaDto): CategoriaConPeso | null {
  const linea = LINEAS.find((candidata) => candidata === dto.linea);
  if (!dto.categoriaId || !linea) {
    return null;
  }
  return {
    categoriaId: dto.categoriaId,
    nombre: dto.nombre ?? '',
    rama: dto.rama ?? null,
    linea,
    pesoGramos: dto.pesoGramos ?? null,
  };
}
