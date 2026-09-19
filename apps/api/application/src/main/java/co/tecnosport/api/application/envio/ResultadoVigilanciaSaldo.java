package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Optional;

/**
 * Qué se vio al mirar el saldo. Cuatro desenlaces y ninguno sobra: el registro de la tarea tiene
 * que poder distinguir "hay plata" de "no se pudo preguntar", porque lo segundo repetido durante
 * horas significa que la vigilancia no está vigilando nada.
 *
 * <p><b>El cuarto llegó tarde, y su ausencia era una mentira pequeña.</b> {@code avisado} está
 * documentado como "si salió el correo", y mientras el adaptador se tragaba los fallos de SMTP
 * devolvía {@code true} tanto si salió como si no. Ahora el caso de uso se entera, y hay un
 * desenlace para decirlo: el saldo está bajo <b>y</b> el aviso no salió, que es el peor de los
 * cuatro — el despacho está a punto de detenerse y nadie va a leerlo en su bandeja.
 *
 * <p><b>Y desde {@code adr/0045}, "salió" es "quedó encolado".</b> El cuarto desenlace no sobra por
 * eso: sigue existiendo el caso de no poder ni encolar. Lo que ya no puede pasar es que un SMTP
 * caído se lleve el aviso en silencio — de eso se encarga la bandeja, reintentando. Este vigilante,
 * que no lleva memoria de lo ya avisado, tampoco necesita saberlo.
 *
 * @param saldo vacío solo cuando la plataforma no contestó
 * @param avisado si el correo quedó encolado
 * @param avisoFallido si había que avisar y el correo no se pudo ni encolar
 */
public record ResultadoVigilanciaSaldo(
    Optional<Dinero> saldo, boolean avisado, boolean avisoFallido) {

  public static ResultadoVigilanciaSaldo noSeSabe() {
    return new ResultadoVigilanciaSaldo(Optional.empty(), false, false);
  }

  public static ResultadoVigilanciaSaldo suficiente(Dinero saldo) {
    return new ResultadoVigilanciaSaldo(Optional.of(saldo), false, false);
  }

  public static ResultadoVigilanciaSaldo avisado(Dinero saldo) {
    return new ResultadoVigilanciaSaldo(Optional.of(saldo), true, false);
  }

  public static ResultadoVigilanciaSaldo avisoFallido(Dinero saldo) {
    return new ResultadoVigilanciaSaldo(Optional.of(saldo), false, true);
  }
}
