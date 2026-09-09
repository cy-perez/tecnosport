package co.tecnosport.api.domain.retracto;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * La constancia de que el dinero salió: cuánto, por dónde, cuándo y quién lo registró.
 *
 * <p>Valor dentro de {@link SolicitudRetracto} y no agregado aparte, a diferencia de {@code Envio}
 * (ADR-0013): no tiene ciclo de vida propio —se registra una vez y no cambia— y vivir dentro hace
 * imposible un reembolso sin solicitud, que es el estado corrupto que habría que evitar a mano si
 * fueran dos tablas.
 *
 * <p>{@code comprobante} es el número de la transferencia, el id de la devolución en la pasarela o
 * lo que el negocio tenga a mano. Es opcional porque un reembolso en efectivo puede no tener
 * ninguno, y exigir un número inventado es peor que no tenerlo.
 */
public record Reembolso(
    Dinero monto,
    MedioReembolso medio,
    String comprobante,
    Instant registradoEn,
    String registradoPor) {

  public Reembolso {
    Objects.requireNonNull(monto, "El monto del reembolso no puede ser nulo.");
    Objects.requireNonNull(medio, "El medio del reembolso no puede ser nulo.");
    Objects.requireNonNull(registradoEn, "La fecha del reembolso no puede ser nula.");
    if (monto.valor().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ExcepcionDeDominio("Un reembolso de cero o menos no es un reembolso.");
    }
    if (registradoPor == null || registradoPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien registra un reembolso no puede quedar en blanco.");
    }
    comprobante = comprobante == null || comprobante.isBlank() ? null : comprobante;
  }

  public Optional<String> comprobanteOpcional() {
    return Optional.ofNullable(comprobante);
  }
}
