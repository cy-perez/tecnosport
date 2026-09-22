import { ImageLoader, ImageLoaderConfig } from '@angular/common';

import { VarianteDeImagen } from '../../features/catalogo/domain/producto.model';

/**
 * Resuelve la URL de cada ancho del `srcset` **buscándola entre las variantes de esa imagen**, que
 * llegan por `loaderParams`.
 *
 * Sin un loader, `NgOptimizedImage` no emite `srcset`: `shouldGenerateAutomaticSrcset()` devuelve
 * `false` cuando el loader es el de por omisión, y un `ngSrcset` sin loader dispara el aviso 2963
 * —"resultaría en la misma imagen para todos los tamaños"— porque cada descriptor pasa igual por
 * `callImageLoader({src, width})`. Comprobado en el código de `@angular/common` 22.1.4 instalado,
 * no de memoria.
 *
 * **No deduce la URL de la forma de la key.** Podría: las variantes de una imagen se distinguen
 * solo por el objeto del bucket, y bastaría un patrón. Pero entonces el patrón viviría en dos
 * repositorios que nadie mantiene sincronizados, que es exactamente el acoplamiento silencioso que
 * produjo `url_webp`. Las URL viajan como dato desde la API hasta aquí.
 *
 * Sin `loaderParams` devuelve el `src` tal cual, y esas imágenes llevan `disableOptimizedSrcset`
 * en su plantilla para que Angular no anuncie un 2x que es el mismo archivo.
 *
 * **`loaderParams` no es exclusivo de las imágenes de producto**, y darlo por supuesto costó una
 * medición: el hero de la portada estaba en el párrafo de arriba como ejemplo de imagen sin
 * variantes, y con eso se dio por hecho que darle `srcset` exigía cambiar este archivo. No exigía
 * nada: sus tres anchos son archivos del repositorio y su plantilla los pasa por aquí como dato,
 * igual que la API pasa los de un producto.
 */
export const cargadorDeImagenes: ImageLoader = (config: ImageLoaderConfig): string => {
  const variantes = config.loaderParams?.['variantes'] as readonly VarianteDeImagen[] | undefined;
  if (!variantes?.length || !config.width) {
    return config.src;
  }
  const exacta = variantes.find((variante) => variante.ancho === config.width);
  return exacta ? exacta.url : config.src;
};
