package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Optional;

/**
 * Qué se vio al mirar el saldo. Tres desenlaces y ninguno sobra: el registro de la tarea tiene que
 * poder distinguir "hay plata" de "no se pudo preguntar", porque lo segundo repetido durante horas
 * significa que la vigilancia no está vigilando nada.
 *
 * @param saldo vacío solo cuando la plataforma no contestó
 * @param avisado si salió el correo
 */
public record ResultadoVigilanciaSaldo(Optional<Dinero> saldo, boolean avisado) {

  public static ResultadoVigilanciaSaldo noSeSabe() {
    return new ResultadoVigilanciaSaldo(Optional.empty(), false);
  }

  public static ResultadoVigilanciaSaldo suficiente(Dinero saldo) {
    return new ResultadoVigilanciaSaldo(Optional.of(saldo), false);
  }

  public static ResultadoVigilanciaSaldo avisado(Dinero saldo) {
    return new ResultadoVigilanciaSaldo(Optional.of(saldo), true);
  }
}
