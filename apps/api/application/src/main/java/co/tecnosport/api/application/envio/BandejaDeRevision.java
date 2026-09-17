package co.tecnosport.api.application.envio;

import java.util.List;

/**
 * Lo que pide ojo humano, en una sola respuesta: guías que dejaron de moverse y emisiones con plata
 * comprometida.
 *
 * <p>Son dos listas y no una mezclada porque las dos cosas se atienden distinto —una guía retenida
 * se le pregunta a la transportadora; una emisión indeterminada se mira en el panel de Skydropx con
 * el identificador de tarifa— y juntarlas en una sola lista obligaría a la pantalla a desempaquetar
 * un tipo variante para saber qué está mirando.
 *
 * <p>Van juntas en la misma respuesta porque para quien atiende son una sola pregunta: qué paquete
 * necesita que alguien haga algo.
 */
public record BandejaDeRevision(List<GuiaEnRevision> guias, List<EmisionEnRevision> emisiones) {

  public BandejaDeRevision {
    guias = List.copyOf(guias);
    emisiones = List.copyOf(emisiones);
  }

  public boolean vacia() {
    return guias.isEmpty() && emisiones.isEmpty();
  }
}
