package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.proveedores.ExpirarDisponibilidadDeProductos;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Oculta cada cierto tiempo lo que el proveedor lleva demasiado sin anunciar.
 *
 * <p>La corre el tick, como a las otras dieciséis ({@code CatalogoDeTareasProgramadas}, ADR-0104),
 * y con varias instancias corre en una sola: la bitácora la reserva. Aunque no la reservara daría
 * igual, porque el caso de uso es idempotente y ocultar dos veces es ocultar una.
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
