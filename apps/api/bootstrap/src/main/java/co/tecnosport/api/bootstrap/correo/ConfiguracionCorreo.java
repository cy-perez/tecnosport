package co.tecnosport.api.bootstrap.correo;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.infrastructure.correo.EnviadorDeCorreoSpringMail;
import co.tecnosport.api.infrastructure.correo.PropiedadesCorreo;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mismo patrón que {@code ConfiguracionPedido}/{@code ConfiguracionCarrito}. {@code JavaMailSender}
 * lo autoconfigura Spring Boot solo, desde {@code spring.mail.*} en {@code application.yml}.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesCorreo.class)
public class ConfiguracionCorreo {

  @Bean
  public EnviadorDeCorreo enviadorDeCorreo(
      JavaMailSender mailSender, PropiedadesCorreo propiedades) {
    return new EnviadorDeCorreoSpringMail(mailSender, propiedades);
  }
}
