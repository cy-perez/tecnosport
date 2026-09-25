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

/**
 * Cuándo el carrusel de portada cambia de pieza: por debajo del primer punto de quiebre va la
 * tarjeta cuadrada, y desde ahí la pieza ancha de 1440 x 592.
 *
 * <p>Aquí vivía `TAMANOS_HERO` —`(min-width: 1200px) 544px, …`— porque la fotografía de portada
 * ocupaba media rejilla de `--ancho-max`. Ya no hace falta: el carrusel sangra de borde a borde,
 * así que su `sizes` es `100vw`, que es un porcentaje de la ventana y no un píxel suelto. Lo que sí
 * hace falta es este `media`, y está en el mismo caso que los `sizes` de arriba: lo lee el
 * navegador **antes** de aplicar una sola hoja de estilos, así que no puede referirse a
 * `--breakpoint-desde-movil`. Es el mismo 640 de `tailwind.css` menos la hendidura que evita que
 * los dos tramos se solapen en un punto; si allá cambia, aquí también.
 */
export const MEDIA_HERO_TARJETA = '(max-width: 639.98px)';
