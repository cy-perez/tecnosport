package co.tecnosport.api.infrastructure.envio.siembra;

import co.tecnosport.api.application.envio.ConsultorDeSaldo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Optional;

/**
 * El consultor de saldo bajo el perfil {@code e2e}, que no consulta nada. Existe por el mismo
 * motivo que {@link EmisorDeGuiasSembrado}: bajo {@code e2e} no se registra el cliente de la
 * plataforma, y con él desaparecen todos sus puertos a la vez. El vigilante del saldo y su tarea
 * piden este puerto sin condición de perfil, así que sin este doble la aplicación no refresca el
 * contexto y el recorrido entero muere en el arranque, lejos de lo que estaba probando.
 *
 * <p><strong>Devuelve "no se sabe" y no una cifra cómoda</strong>, y la diferencia importa: una
 * cifra alta haría pasar en silencio a un recorrido que llegara hasta aquí, y una baja mandaría un
 * correo de alarma en cada corrida de integración continua. "No se pudo preguntar" es además lo
 * cierto — no hay credenciales del proveedor— y es el único desenlace que la tarea registra sin
 * escribirle a nadie.
 */
public final class ConsultorDeSaldoSembrado implements ConsultorDeSaldo {

  @Override
  public Optional<Dinero> saldo() {
    return Optional.empty();
  }
}
