import type { components } from '@tecnosport/contratos';
import {
  Atributo,
  Categoria,
  Imagen,
  Marca,
  Producto,
  Rotacion,
  TipoAtributo,
  ValorAtributo,
  Variante,
} from '../domain/producto.model';

type ProductoDto = components['schemas']['ProductoRespuesta'];
type ImagenDto = components['schemas']['ImagenRespuesta'];
type MarcaDto = components['schemas']['MarcaRespuesta'];
type CategoriaDto = components['schemas']['CategoriaRespuesta'];
type VarianteDto = components['schemas']['VarianteRespuesta'];
type ValorAtributoDto = components['schemas']['AtributoValorRespuesta'];
type RotacionDto = components['schemas']['RotacionRespuesta'];
type AtributoDto = components['schemas']['AtributoRespuesta'];

/** DTO generado -> modelo propio del front. Ningún componente ve la forma de la respuesta HTTP. */
export function aProducto(dto: ProductoDto): Producto {
  return {
    slug: dto.slug ?? '',
    nombre: dto.nombre ?? '',
    descripcion: dto.descripcion ?? '',
    marca: aMarca(dto.marca),
    categoria: aCategoria(dto.categoria),
    imagenPrincipal: dto.imagenPrincipal ? aImagen(dto.imagenPrincipal) : null,
    galeria: (dto.galeria ?? []).map(aImagen),
    rotacion: dto.rotacion ? aRotacion(dto.rotacion) : null,
    variantes: (dto.variantes ?? []).map(aVariante),
  };
}

export function aMarca(dto?: MarcaDto): Marca {
  return { id: dto?.id ?? '', nombre: dto?.nombre ?? '' };
}

export function aCategoria(dto?: CategoriaDto): Categoria {
  return { id: dto?.id ?? '', nombre: dto?.nombre ?? '', slug: dto?.slug ?? '', linea: dto?.linea ?? '' };
}

function aImagen(dto: ImagenDto): Imagen {
  return {
    url: dto.url ?? '',
    urlWebp: dto.urlWebp ?? '',
    ancho: dto.ancho ?? 0,
    alto: dto.alto ?? 0,
    altEs: dto.altEs ?? '',
    altEn: dto.altEn ?? '',
  };
}

/**
 * El orden de los fotogramas es la rotación: uno fuera de sitio se ve como un salto al girar. Se
 * garantiza aquí, en la frontera donde el DTO se vuelve modelo, y no en cada pantalla que lo
 * consuma — el visor recibe el arreglo ya ordenado y no tiene que volver a saberlo.
 */
function aRotacion(dto: RotacionDto): Rotacion {
  return {
    fotogramas: dto.fotogramas ?? 0,
    imagenes: (dto.imagenes ?? [])
      .map((fotograma) => ({
        orden: fotograma.orden ?? 0,
        url: fotograma.url ?? '',
        urlWebp: fotograma.urlWebp ?? '',
        ancho: fotograma.ancho ?? 0,
        alto: fotograma.alto ?? 0,
      }))
      .sort((uno, otro) => uno.orden - otro.orden),
  };
}

function aVariante(dto: VarianteDto): Variante {
  return {
    id: dto.id ?? '',
    sku: dto.sku ?? '',
    precio: { valor: dto.precio?.valor ?? 0, moneda: dto.precio?.moneda ?? 'COP' },
    existencia: dto.existencia ?? 0,
    atributos: (dto.atributos ?? []).map(aValorAtributo),
  };
}

function aValorAtributo(dto: ValorAtributoDto): ValorAtributo {
  return { nombre: dto.nombre ?? '', valor: dto.valor ?? '', colorHex: dto.colorHex ?? null };
}

/** El contrato expone `tipo` como `string` (springdoc no emite el enum de Java como unión literal);
 * el backend garantiza que el valor es exactamente el nombre del enum, así que se afirma el tipo en
 * vez de validarlo — mismo criterio que `admin/productos/infrastructure/mapeador-producto-admin.ts`. */
export function aAtributo(dto: AtributoDto): Atributo {
  return {
    id: dto.id ?? '',
    nombre: dto.nombre ?? '',
    tipo: (dto.tipo ?? 'TEXTO') as TipoAtributo,
    valoresPermitidos: dto.valoresPermitidos ?? [],
  };
}
