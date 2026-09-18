package co.tecnosport.api.infrastructure.envio.siembra;

import co.tecnosport.api.application.envio.ConsultorDeSobrecostos;
import co.tecnosport.api.application.envio.SobrecostoDeEnvio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El consultor de cobros extra bajo el perfil {@code e2e}, que no consulta nada. Existe por el
 * mismo motivo que {@link ConsultorDeSaldoSembrado}: bajo {@code e2e} no se registra el cliente de
 * la plataforma, y con él desaparecen todos sus puertos a la vez. El vigilante y su tarea piden
 * este puerto sin condición de perfil, así que sin este doble la aplicación no refresca el contexto
 * y el recorrido entero muere en el arranque, lejos de lo que estaba probando.
 *
 * <p><strong>Devuelve "no se pudo preguntar" y no una lista vacía</strong>, por lo mismo que aquél:
 * una lista vacía diría "no hay cobros extra", que es una afirmación sobre la cuenta, y aquí no hay
 * cuenta a la que preguntarle. El desenlace que la tarea registra sin escribirle a nadie es este.
 */
public final class ConsultorDeSobrecostosSembrado implements ConsultorDeSobrecostos {

  @Override
  public Optional<List<SobrecostoDeEnvio>> desde(Instant desde) {
    return Optional.empty();
  }
}
