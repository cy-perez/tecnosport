package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;

/**
 * Una guía del despacho, para el panel: lleva {@code costo}, que es lo que la transportadora nos
 * cobra por ese paquete. Por eso no la ve el comprador — para él está {@link GuiaPublicaRespuesta}.
 *
 * <p>{@code urlEtiqueta} es el rótulo que hay que imprimir y pegar a la caja, y viene vacío más a
 * menudo de lo que parece: las guías tecleadas a mano se imprimieron por fuera, y de las emitidas
 * por nosotros tampoco está garantizado —dos guías de Servientrega por el mismo camino, una lo
 * trajo y la otra no (docs/13-skydropx-capacidades.md §6.7)—. El panel tiene que saber vivir sin
 * él.
 */
public record GuiaRespuesta(
    String transportadora, String guia, DineroRespuesta costo, String urlEtiqueta) {}
