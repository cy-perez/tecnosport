package co.tecnosport.api.application.pedido;

/**
 * Lo que una persona tiene en la mano cuando quiere saber de su pedido: el número que le llegó al
 * correo y el correo mismo. Ninguno de los dos es el {@code id}, que es un UUID y no aparece en
 * nada que una persona lea.
 */
public record ConsultarSeguimientoPorNumeroComando(String numeroPedido, String correo) {}
