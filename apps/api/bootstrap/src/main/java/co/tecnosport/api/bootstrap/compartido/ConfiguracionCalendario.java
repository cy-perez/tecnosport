package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.domain.compartido.CalendarioHabil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El calendario de días hábiles, uno solo para todo el sistema.
 *
 * <p>Vivía en {@code ConfiguracionRetracto} porque el retracto fue el primero que necesitó contar
 * días hábiles, y de ahí se lo prestaban la atención de PQR y la reversión — con un comentario en
 * cada una explicando de dónde salía. Tres consumidores y ninguno es el dueño: el sitio del {@code
 * bean} es el paquete compartido.
 *
 * <p>Es único a propósito. Tres calendarios serían tres listas de festivos que algún día divergen,
 * y el día que divergieran, dos plazos legales del mismo pedido se contarían distinto.
 */
@Configuration
public class ConfiguracionCalendario {

  @Bean
  public CalendarioHabil calendarioHabil() {
    return CalendarioHabil.calculado();
  }
}
