package co.tecnosport.api.bootstrap.correo;

import co.tecnosport.api.application.compartido.DrenarBandejaDeSalida;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RepositorioCorreosPendientes;
import co.tecnosport.api.application.compartido.TransporteDeCorreo;
import co.tecnosport.api.infrastructure.correo.CorreoPendienteJpaRepository;
import co.tecnosport.api.infrastructure.correo.EnviadorDeCorreoBandejaDeSalida;
import co.tecnosport.api.infrastructure.correo.PropiedadesCorreo;
import co.tecnosport.api.infrastructure.correo.TransporteDeCorreoSpringMail;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mismo patrón que {@code ConfiguracionPedido}/{@code ConfiguracionCarrito}. {@code JavaMailSender}
 * lo autoconfigura Spring Boot solo, desde {@code spring.mail.*} en {@code application.yml}.
 *
 * <p><b>Aquí se ve de un vistazo la forma del mecanismo</b> (adr/0045), y por eso conviene leerlo
 * entero: lo que reciben los casos de uso, {@link EnviadorDeCorreo}, encola; lo que manda de
 * verdad, {@link TransporteDeCorreo}, solo lo recibe el drenaje. Son dos tipos con la misma firma a
 * propósito — así nadie se salta la bandeja sin escribir el nombre del otro puerto.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesCorreo.class, PropiedadesBandejaDeSalida.class})
public class ConfiguracionCorreo {

  /** Lo que llaman los trece casos de uso: escribe la fila y se va. */
  @Bean
  public EnviadorDeCorreo enviadorDeCorreo(CorreoPendienteJpaRepository correos, Reloj reloj) {
    return new EnviadorDeCorreoBandejaDeSalida(correos, reloj);
  }

  /** El envío de verdad, contra SMTP. Solo lo usa {@link #drenarBandejaDeSalida}. */
  @Bean
  public TransporteDeCorreo transporteDeCorreo(
      JavaMailSender mailSender, PropiedadesCorreo propiedades) {
    return new TransporteDeCorreoSpringMail(mailSender, propiedades);
  }

  @Bean
  public DrenarBandejaDeSalida drenarBandejaDeSalida(
      RepositorioCorreosPendientes repositorio,
      TransporteDeCorreo transporte,
      Reloj reloj,
      PropiedadesBandejaDeSalida propiedades) {
    return new DrenarBandejaDeSalida(
        repositorio,
        transporte,
        reloj,
        Duration.ofDays(propiedades.diasRetencion()),
        propiedades.tamanoDelLote());
  }
}
