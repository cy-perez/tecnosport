package co.tecnosport.api.presentation.pedido.dto;

/** Quien recibe el pedido. Nulo en los pedidos anteriores a que se pidiera (V36). */
public record ContactoRespuesta(String nombre, String telefono) {}
