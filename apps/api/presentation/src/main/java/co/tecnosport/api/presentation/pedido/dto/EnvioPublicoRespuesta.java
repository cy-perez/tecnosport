package co.tecnosport.api.presentation.pedido.dto;

import java.time.Instant;
import java.util.List;

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
 *
 * <p>Desde adr/0031 son varias guias, y el comprador las ve todas: si su pedido sale en dos
 * paquetes tiene derecho a saberlo, porque recibir uno de dos no es recibir el pedido.
 */
public record EnvioPublicoRespuesta(List<GuiaPublicaRespuesta> guias, Instant despachadoEn) {}
