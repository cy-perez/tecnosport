package co.tecnosport.api.presentation.pedido.dto;

/**
 * Una cuenta a la que transferir, tal como se le enseña al comprador.
 *
 * <p>{@code entidad} y no {@code banco}: dos de las tres son billeteras —Nequi y Daviplata— y el
 * comprador las busca por ese nombre en su app, no por el del banco que hay detrás.
 */
public record CuentaDeTransferenciaRespuesta(
    String entidad, String tipoCuenta, String numeroCuenta, String titular) {}
