package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;

/**
 * Qué dice la caja que lleva dentro. Es el {@code package_content} que exige Skydropx al emitir la
 * guía, uno por bulto (docs/13-skydropx-capacidades.md, sección 6).
 *
 * <p>Este campo decide dos cosas a la vez y tiran en sentidos contrarios: <b>lo declarado tiene que
 * coincidir con lo real</b>, porque una reclamación por pérdida se cae si no, y <b>la etiqueta la
 * lee cualquiera que cargue la caja</b>, así que escribir la marca y el modelo del celular ahí es
 * anunciar lo que hay dentro. Por eso es un genérico por línea de catálogo: el nombre del producto
 * se descartó por lo segundo, y un texto fijo para todo el catálogo por lo primero. Quien responde
 * por el valor es {@code valorDeclarado} del bulto, que sí va exacto.
 *
 * <p><b>Esta decisión vivió en un documento y envejeció en un día.</b> Se tomó el 14 de septiembre
 * de 2026 mapeando {@code CELULARES} a "Equipo de telefonía móvil", y esa misma fecha {@code V38}
 * renombró la línea a {@code TECNOLOGIA} y le colgó diez categorías más: relojes, audífonos,
 * cargadores, cables, power banks, consolas, parlantes, computadores, tablets y proyectores.
 * Aplicada tal cual, un proyector habría viajado declarado como telefonía móvil. De ahí que el
 * {@code switch} sea <b>exhaustivo y sin {@code default}</b>: una línea nueva no compila hasta que
 * alguien decida qué dice su etiqueta, que es la pregunta que el documento no supo hacer.
 */
public final class ContenidoDeclarado {

  private ContenidoDeclarado() {}

  /**
   * "Electrónica y accesorios" y no "Equipo electrónico": las categorías de la línea tienen que
   * caber en la misma frase sin que ninguna quede declarada de menos.
   *
   * <p>La frase se eligió cuando la línea tenía once categorías y tres de ellas —cables, cargadores
   * y power banks— no eran equipo sino accesorio. {@code V62} las quitó del catálogo el 24 de
   * septiembre de 2026 y las ocho que quedan sí son equipo, así que "accesorios" ya no está
   * cubriendo a nadie. <b>Se deja igual a propósito.</b> Lo declarado de más no tumba una
   * reclamación por pérdida; lo declarado de menos, sí, y estrechar la frase solo la vuelve a dejar
   * corta el día que entre la primera funda o el primer soporte.
   */
  public static String de(LineaCatalogo linea) {
    return switch (linea) {
      case ROPA_Y_CALZADO -> "Ropa y calzado deportivo";
      case BOLSOS -> "Bolsos y morrales";
      case TECNOLOGIA -> "Electrónica y accesorios";
    };
  }
}
