package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.ExpirarDisponibilidadDeProductos;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Oculta cada cierto tiempo lo que el proveedor lleva demasiado sin anunciar.
 *
 * <p>Dentro de la aplicación y no en Cloud Scheduler, como las otras once tareas ({@code
 * docs/07-infra-gcp.md}, 23 de septiembre de 2026): en producción la API lleva la CPU siempre
 * asignada y eso es lo que hace fiable a {@code @Scheduled}. Con varias instancias corre en cada
 * una, y no pasa nada: el caso de uso es idempotente y ocultar dos veces es ocultar una.
 *
 * <p>El intervalo y la ventana son duraciones ISO-8601 ({@code PT24H}, {@code P3D}) y se acortan
 * por variable de entorno para probar con minutos. La bandera {@code habilitado} existe para
 * apagarla sin desplegar; por omisión está encendida.
 */
@Component
@ConditionalOnProperty(
    name = "tecnosport.proveedores.job-expiracion.habilitado",
    havingValue = "true",
    matchIfMissing = true)
public class TareaExpiracionDisponibilidad {

  private static final Logger log = LoggerFactory.getLogger(TareaExpiracionDisponibilidad.class);

  private final ExpirarDisponibilidadDeProductos expirar;
  private final EnTransaccionPropia enTransaccionPropia;

  public TareaExpiracionDisponibilidad(
      ExpirarDisponibilidadDeProductos expirar, EnTransaccionPropia enTransaccionPropia) {
    this.expirar = Objects.requireNonNull(expirar);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
  }

  @Scheduled(
      fixedDelayString = "${tecnosport.proveedores.job-expiracion.intervalo}",
      initialDelayString = "${tecnosport.proveedores.job-expiracion.retraso-inicial}")
  public void ocultarVencidos() {
    ExpirarDisponibilidadDeProductos.Resultado resultado =
        enTransaccionPropia.ejecutar(expirar::ejecutar);
    if (resultado.ocultados() > 0) {
      log.info(
          "Disponibilidad de proveedores: {} producto(s) ocultos por no verse desde antes de {}:"
              + " {}",
          resultado.ocultados(),
          resultado.limite(),
          resultado.porProveedor());
    } else {
      log.debug("Disponibilidad de proveedores: nada que ocultar antes de {}", resultado.limite());
    }
  }
}
