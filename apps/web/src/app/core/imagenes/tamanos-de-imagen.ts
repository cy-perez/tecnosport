/**
 * Lo que mide cada imagen en pantalla, para que el navegador elija el ancho antes de saber cuánto
 * ocupa la caja.
 *
 * **Estos son los únicos píxeles literales del frontend fuera de los puntos de quiebre, y por la
 * misma razón**: `sizes` lo lee el navegador antes de aplicar una sola hoja de estilos, así que no
 * puede referirse a una propiedad personalizada. Los valores son los de `--breakpoint-desde-movil`
 * y `--breakpoint-desde-tableta` en `tailwind.css`; si allá cambian, aquí también.
 *
 * Viven juntos en un archivo para que la tarjeta y la galería no describan distinto la misma
 * rejilla.
 */

/**
 * La rejilla de producto: dos columnas en móvil, tres desde 640 y cuatro desde 1024, dentro de un
 * contenedor con `max-w-contenido`. Los `vw` van por encima del ancho real —el contenedor topa y
 * hay `gap` entre columnas—, que es el lado correcto para equivocarse: de menos, el navegador
 * elegiría una variante demasiado pequeña y se vería borrosa.
 */
export const TAMANOS_TARJETA = '(min-width: 1024px) 25vw, (min-width: 640px) 34vw, 50vw';

/** La galería de la ficha: media pantalla desde tableta, porque la ficha es de dos columnas. */
export const TAMANOS_GALERIA = '(min-width: 1024px) 50vw, 100vw';

/**
 * La miniatura de la galería, que mide `size-64`. Pide el ancho más pequeño que exista en vez del
 * de la imagen grande que ya está arriba: son cuatro imágenes por ficha.
 */
export const TAMANOS_MINIATURA = '64px';
