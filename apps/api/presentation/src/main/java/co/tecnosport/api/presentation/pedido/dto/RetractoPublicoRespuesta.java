package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;

/**
 * El retracto visto por quien lo ejercio.
 *
 * <p>Deja fuera dos cosas a proposito. {@code radicadaPor} es el identificador del administrador
 * que atendio, igual que el {@code actor} del historial que ya se anula aqui. Y el veredicto de
 * plazo tampoco viaja: es un dato interno para que una persona decida, puede valer {@code
 * INDETERMINADO}, y mostrarle a un comprador "fuera de plazo" seria una afirmacion juridica que el
 * sistema no siempre puede sostener.
 *
 * <p>{@code limiteDeReintegro} si viaja, y es el que mas le importa: es la fecha en que el negocio
 * se comprometio a tener el dinero de vuelta.
 */
public record RetractoPublicoRespuesta(
    String estado,
    Instant radicadaEn,
    String motivo,
    Instant productoRecibidoEn,
    Instant limiteDeReintegro,
    DineroRespuesta montoReembolsado,
    Instant reembolsadoEn) {}
