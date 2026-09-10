package co.tecnosport.api.presentation.pedido.dto;

import java.time.Instant;

/**
 * Lo que un comprador necesita para seguir su paquete, y nada mas.
 *
 * <p>Existe porque {@code EnvioRespuesta} lleva {@code costoEnvio} —lo que la transportadora nos
 * cobra— y {@code comisionRecaudo}, y el seguimiento publico lo devolvia tal cual: cualquiera con
 * un id de pedido y el correo correcto veia el margen del negocio en ese envio. Es el hallazgo 3 de
 * docs/12-legales-de-envio.md, y era un defecto en produccion, no del cambio.
 *
 * <p>La leccion de fondo, que conviene no perder: el mismo record servia a la respuesta del panel y
 * a la publica, y por eso la fuga fue invisible. Dos audiencias distintas, dos tipos distintos.
 */
public record EnvioPublicoRespuesta(String transportadora, String guia, Instant despachadoEn) {}
