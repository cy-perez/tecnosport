import type { components } from '@tecnosport/contratos';
import { Categoria, Imagen, Marca, Producto, Rotacion, ValorAtributo, Variante } from '../domain/producto.model';

type ProductoDto = components['schemas']['ProductoRespuesta'];
type ImagenDto = components['schemas']['ImagenRespuesta'];
type MarcaDto = components['schemas']['MarcaRespuesta'];
type CategoriaDto = components['schemas']['CategoriaRespuesta'];
type VarianteDto = components['schemas']['VarianteRespuesta'];
type ValorAtributoDto = components['schemas']['AtributoValorRespuesta'];
type RotacionDto = components['schemas']['RotacionRespuesta'];

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
  return { nombre: dto?.nombre ?? '', slug: dto?.slug ?? '', linea: dto?.linea ?? '' };
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

function aRotacion(dto: RotacionDto): Rotacion {
  return {
    fotogramas: dto.fotogramas ?? 0,
    imagenes: (dto.imagenes ?? []).map((fotograma) => ({
      orden: fotograma.orden ?? 0,
      url: fotograma.url ?? '',
      urlWebp: fotograma.urlWebp ?? '',
      ancho: fotograma.ancho ?? 0,
      alto: fotograma.alto ?? 0,
    })),
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
