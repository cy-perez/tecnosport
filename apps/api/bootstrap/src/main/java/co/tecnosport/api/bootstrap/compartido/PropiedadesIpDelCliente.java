package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * De qué valor de {@code X-Forwarded-For} fiarse, y la clave con la que el proxy de la web prueba
 * que la IP que manda la escribió él (ADR-0087, {@code FiltroIpDelCliente}).
 *
 * <p>{@code claveProxy} es opcional y no por descuido: el entrenamiento CDS del Dockerfile arranca
 * la API sin perfil ni secretos, y una propiedad obligatoria nueva rompería el despliegue y no el
 * PR. Vacía, la cabecera del proxy simplemente no se cree — el resultado seguro.
 *
 * <p>{@code saltosConfiables} es 1 mientras la API se publique directo en {@code run.app}: el único
 * valor que agrega la infraestructura es el del frente de Cloud Run. Pasa a 2 el día que haya un
 * balanceador delante, y ese día la API tiene que dejar de aceptar tráfico que no venga de él.
 */
@ConfigurationProperties(prefix = "tecnosport.ip-cliente")
public record PropiedadesIpDelCliente(String claveProxy, int saltosConfiables) {

  public PropiedadesIpDelCliente {
    if (saltosConfiables < 1) {
      throw new IllegalStateException(
          "tecnosport.ip-cliente.saltos-confiables debe ser al menos 1.");
    }
  }
}
