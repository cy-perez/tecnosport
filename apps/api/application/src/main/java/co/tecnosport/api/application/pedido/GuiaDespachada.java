package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Una guía del despacho: transportadora, número y lo que esa guía nos cuesta.
 *
 * <p>Van varias porque ninguna transportadora colombiana admite multipaquete y un pedido de dos
 * variantes son dos guías (adr/0031). {@code transportadora} y {@code guia} se validan en {@code
 * GuiaEnvio}, no aquí: no hace falta duplicar la regla.
 */
public record GuiaDespachada(String transportadora, String guia, Dinero costoEnvio) {}
