package co.tecnosport.api.application.envio;

import java.util.Objects;
import java.util.Optional;

/**
 * El webhook de Skydropx, por dentro: verifica la firma, lee el cuerpo y delega en {@link
 * AplicarEventoDeEnvio}, que es lo que comparte con la conciliación programada (adr/0022).
 *
 * <p><strong>La firma se verifica antes de tocar nada</strong>, exactamente como en el de Wompi. No
 * es una formalidad: este endpoint es público y sin él cualquiera podría marcar un pedido como
 * entregado —y con eso arrancar los plazos del retracto y la garantía— mandando un JSON.
 *
 * <p><strong>Hoy no aplica ningún evento, y es a propósito.</strong> Ni el algoritmo de la firma ni
 * la forma del cuerpo están confirmados contra la cuenta, así que sus dos puertos fallan cerrado:
 * la firma se rechaza y el cuerpo no se sabe leer. El endpoint responde 200 igual, registra el
 * motivo, y no mueve un solo pedido. Es la misma decisión que se tomó con el mapeo de la cotización
 * mientras faltaban datos: la máquina de alrededor se construye y se prueba, y lo que depende del
 * proveedor espera a poder medirse.
 */
public final class RecibirEventoDeEnvio {

  private final VerificadorFirmaEnvio verificadorFirma;
  private final LectorEventoDeEnvio lector;
  private final AplicarEventoDeEnvio aplicar;

  public RecibirEventoDeEnvio(
      VerificadorFirmaEnvio verificadorFirma,
      LectorEventoDeEnvio lector,
      AplicarEventoDeEnvio aplicar) {
    this.verificadorFirma = Objects.requireNonNull(verificadorFirma);
    this.lector = Objects.requireNonNull(lector);
    this.aplicar = Objects.requireNonNull(aplicar);
  }

  public ResultadoEventoDeEnvio ejecutar(String cuerpoCrudo, String firma) {
    if (cuerpoCrudo == null || !verificadorFirma.esValida(cuerpoCrudo, firma)) {
      return ResultadoEventoDeEnvio.FIRMA_INVALIDA;
    }
    Optional<AplicarEventoDeEnvioComando> comando = lector.leer(cuerpoCrudo);
    if (comando.isEmpty()) {
      return ResultadoEventoDeEnvio.NO_SE_PUDO_LEER;
    }
    return aplicar.ejecutar(comando.get());
  }
}
