package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pedido.MetodoPago;

/**
 * Traduce el {@code payment_method_type} que trae Wompi (por webhook o por consulta directa) al
 * {@link MetodoPago} del dominio, igual que {@link EstadosWompi} hace con el {@code status}.
 *
 * <p><b>Por qué existe:</b> la URL del Web Checkout hospedado no le manda a Wompi el método que el
 * comprador eligió en nuestro checkout — Wompi pinta su propia lista y el comprador vuelve a
 * elegir. Así que lo que quedaba grabado en el pedido era una intención, no un hecho, y nada la
 * contrastaba nunca contra lo que se cobró.
 *
 * <p>Valores verificados contra la documentación pública de Wompi el 14 de septiembre de 2026
 * (regla dura #9), no de memoria. Los que Wompi documenta y aquí devuelven {@code null} son medios
 * que este sitio no ofrece —{@code PCOL} (Puntos Colombia), {@code DAVIPLATA}, {@code
 * BANCOLOMBIA_BNPL}, {@code SU_PLUS}— y {@code BANCOLOMBIA_COLLECT}, que es efectivo en un
 * corresponsal y no el botón de Bancolombia: parecerse al nombre no basta para darlo por
 * equivalente.
 *
 * <p>{@code null} significa "no sé traducir esto", que no es lo mismo que "esto no coincide". Quien
 * pregunte tiene que distinguir los dos casos: uno es un medio que no ofrecemos, el otro sería
 * afirmar una discrepancia sobre algo que no se entendió.
 */
final class MediosDeWompi {

  private MediosDeWompi() {}

  static MetodoPago aMetodoPago(String medioWompi) {
    if (medioWompi == null) {
      return null;
    }
    return switch (medioWompi.trim()) {
      case "CARD" -> MetodoPago.TARJETA;
      case "NEQUI" -> MetodoPago.NEQUI;
      case "PSE" -> MetodoPago.PSE;
      case "BANCOLOMBIA_TRANSFER", "BANCOLOMBIA_QR" -> MetodoPago.BANCOLOMBIA;
      default -> null;
    };
  }
}
