package co.tecnosport.api.domain.reintegro;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La constancia de que el dinero salió: cuánto, por dónde, cuándo, quién lo registró y —sobre todo—
 * <b>por qué</b>.
 *
 * <p>Nació como un valor dentro de {@code SolicitudRetracto}, con un argumento correcto para el
 * retracto: viviendo dentro, un reembolso sin solicitud era imposible de escribir. El problema
 * apareció al contar cuántos caminos de los términos publicados terminan en devolver dinero, que
 * son cinco y no uno. La decisión que protegía al primero dejaba a los otros cuatro sin dónde dejar
 * constancia, y habría acabado en cinco constancias distintas del mismo hecho — con lo cual "cuánto
 * devolvimos el mes pasado" no se puede responder sin sumar a mano.
 *
 * <p>Lo que se perdió al sacarlo, dicho sin adornos: ya no es estructuralmente imposible escribir
 * un reintegro huérfano. Lo que lo sustituye es {@link #origenId}, obligatorio, y la invariante del
 * lado contrario — una solicitud que se declara reembolsada exige el id de su reintegro—, de modo
 * que los dos extremos se apuntan y ninguno de los dos estados a medias se puede escribir.
 *
 * <p>No mueve un peso. Dos de los tres métodos de pago del sitio se devuelven por fuera del sistema
 * por definición, y para el tercero no está verificado que la pasarela exponga la devolución por
 * API: un agregado que pretendiera devolver automáticamente sería mentira en dos de cada tres
 * pedidos. Lo que la ley pide demostrar es cuándo salió, por dónde y cuánto, y eso es esto.
 */
public final class Reintegro {

  private final UUID id;
  private final UUID pedidoId;
  private final MotivoReintegro motivo;
  private final UUID origenId;
  private final Dinero monto;
  private final MedioReintegro medio;
  private final String comprobante;
  private final Instant registradoEn;
  private final String registradoPor;

  public Reintegro(
      UUID id,
      UUID pedidoId,
      MotivoReintegro motivo,
      UUID origenId,
      Dinero monto,
      MedioReintegro medio,
      String comprobante,
      Instant registradoEn,
      String registradoPor) {
    this.id = Objects.requireNonNull(id, "El id del reintegro no puede ser nulo.");
    this.pedidoId = Objects.requireNonNull(pedidoId, "El pedido del reintegro no puede ser nulo.");
    this.motivo = Objects.requireNonNull(motivo, "Un reintegro sin motivo no se puede registrar.");
    this.origenId =
        Objects.requireNonNull(
            origenId, "Un reintegro necesita la solicitud o el hecho que lo justifica.");
    this.monto = Objects.requireNonNull(monto, "El monto del reintegro no puede ser nulo.");
    this.medio = Objects.requireNonNull(medio, "El medio del reintegro no puede ser nulo.");
    if (monto.valor().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ExcepcionDeDominio("Un reintegro de cero o menos no es un reintegro.");
    }
    this.registradoEn =
        Objects.requireNonNull(registradoEn, "La fecha del reintegro no puede ser nula.");
    if (registradoPor == null || registradoPor.isBlank()) {
      throw new ExcepcionDeDominio("Quien registra un reintegro no puede quedar en blanco.");
    }
    this.registradoPor = registradoPor;
    this.comprobante = comprobante == null || comprobante.isBlank() ? null : comprobante;
  }

  /**
   * {@code origenId} es la solicitud que lo justifica —de retracto, de garantía, de reversión— o,
   * en los caminos que no nacen de una solicitud del comprador, el propio pedido cuya cancelación
   * lo obliga. Nunca es nulo: un reintegro sin nada detrás es plata que salió sin explicación.
   */
  public static Reintegro registrar(
      UUID pedidoId,
      MotivoReintegro motivo,
      UUID origenId,
      Dinero monto,
      MedioReintegro medio,
      String comprobante,
      Instant ahora,
      String actor) {
    return new Reintegro(
        GeneradorIdentificador.nuevo(),
        pedidoId,
        motivo,
        origenId,
        monto,
        medio,
        comprobante,
        ahora,
        actor);
  }

  public UUID id() {
    return id;
  }

  public UUID pedidoId() {
    return pedidoId;
  }

  public MotivoReintegro motivo() {
    return motivo;
  }

  public UUID origenId() {
    return origenId;
  }

  public Dinero monto() {
    return monto;
  }

  public MedioReintegro medio() {
    return medio;
  }

  /**
   * El número de la transferencia, el id de la devolución en la pasarela o lo que el negocio tenga
   * a mano. Opcional porque un reintegro en efectivo puede no tener ninguno, y exigir un número
   * inventado es peor que no tenerlo.
   */
  public Optional<String> comprobante() {
    return Optional.ofNullable(comprobante);
  }

  public Instant registradoEn() {
    return registradoEn;
  }

  public String registradoPor() {
    return registradoPor;
  }
}
