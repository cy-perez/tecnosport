package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.BitacoraDeTareas;
import co.tecnosport.api.application.compartido.EjecutarTareasVencidas;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TareaProgramada;
import co.tecnosport.api.application.compartido.VerificadorDeTareasProgramadas;
import co.tecnosport.api.infrastructure.proveedores.tareas.VerificadorDeTareasDeIngestaGoogleJwt;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El tick de las tareas programadas y quién puede dispararlo (ADR-0104). */
@Configuration
@EnableConfigurationProperties(PropiedadesTareasProgramadas.class)
public class ConfiguracionTareasProgramadas {

  /** Las de {@link CatalogoDeTareasProgramadas}, en el orden de sus {@code @Order}. */
  @Bean
  public EjecutarTareasVencidas ejecutarTareasVencidas(
      List<TareaProgramada> tareas,
      BitacoraDeTareas bitacora,
      Reloj reloj,
      PropiedadesTareasProgramadas propiedades) {
    return new EjecutarTareasVencidas(tareas, bitacora, reloj, propiedades.arriendo());
  }

  /**
   * El mismo verificador de tokens OIDC que usa la ingesta, con otra cuenta: lo que comprueba
   * —firma de Google, audiencia, vigencia, la cuenta y su correo verificado— es lo mismo. Con el
   * disparador interno no acepta ningún token: el tick no lo llama nadie de fuera.
   */
  @Bean
  public VerificadorDeTareasProgramadas verificadorDeTareasProgramadas(
      PropiedadesTareasProgramadas propiedades) {
    if (!propiedades.externo()) {
      return token -> false;
    }
    return VerificadorDeTareasDeIngestaGoogleJwt.contraLlavesDeGoogle(
            propiedades.urlLlaves(), propiedades.audiencia(), propiedades.cuentaDeServicio())
        ::esDeLaCola;
  }
}
