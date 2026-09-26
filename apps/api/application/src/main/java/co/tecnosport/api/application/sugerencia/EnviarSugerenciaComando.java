package co.tecnosport.api.application.sugerencia;

/**
 * Lo que llega del navegador al buzón.
 *
 * <p>{@code correo} en blanco o nulo significa «prefiero no decirlo», que es un caso válido y no un
 * dato faltante: ver {@code Sugerencia}. {@code autorizaDatos} solo se mira cuando hay correo —sin
 * correo no hay dato personal que autorizar— y por eso un {@code false} con el correo vacío no es
 * un error.
 *
 * <p>{@code direccionIp} <b>no la manda el cliente</b>: la resuelve {@code IpDelCliente} en la capa
 * de presentación, como en el registro y en el checkout. Es metadato de la constancia de
 * autorización, y una IP que el navegador pudiera elegir no probaría nada.
 */
public record EnviarSugerenciaComando(
    String mensaje, String correo, boolean autorizaDatos, String direccionIp) {}
