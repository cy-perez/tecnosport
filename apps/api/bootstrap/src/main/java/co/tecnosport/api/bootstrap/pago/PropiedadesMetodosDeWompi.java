package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code WOMPI_METODOS_HABILITADOS} de docs/07-infra-gcp.md: los métodos que la cuenta de Wompi
 * tiene activados hoy, no los que el código sabe procesar. Son dos cosas distintas y hasta ahora se
 * confundían — {@code MetodosDePagoDisponibles} ofrecía el enum entero, así que el checkout
 * prometía todo lo que Wompi soporta en abstracto.
 *
 * <p><b>Addi arranca fuera</b> (decisión del 14 de septiembre de 2026): activarlo exige que el
 * sitio ya esté en línea, y hasta entonces ofrecerlo sería prometer un medio de pago que no se
 * puede honrar. El día que Wompi lo active, entra por variable de entorno y sin tocar código — pero
 * <b>hay que devolver también la frase de los términos</b>, que se quitó junto con esto
 * (docs/11-pagos-y-envios.md).
 *
 * <p>Solo admite métodos que <b>cobre Wompi</b>: contraentrega y transferencia manual no las
 * habilita ni las apaga Wompi, y aceptarlas aquí daría un segundo interruptor para algo que ya
 * tiene el suyo ({@code CONTRAENTREGA_HABILITADA}). Desde {@code adr/0048} tampoco admite {@code
 * SISTECREDITO}, que es de otra pasarela y tiene su propio interruptor ({@code
 * SISTECREDITO_HABILITADO}): sin esta comprobación, escribirlo aquí lo habría dejado ofrecido en el
 * checkout y enrutado a Wompi. Un valor que no cobre Wompi impide arrancar: un despliegue con la
 * lista mal escrita tiene que fallar al arrancar y no al primer checkout.
 */
@ConfigurationProperties(prefix = "tecnosport.wompi.metodos")
public record PropiedadesMetodosDeWompi(List<String> habilitados) {

  public PropiedadesMetodosDeWompi {
    habilitados = habilitados == null ? List.of() : List.copyOf(habilitados);
  }

  /**
   * La lista vacía es legítima: significa que la pasarela no acepta nada hoy y el checkout se queda
   * con transferencia manual y contraentrega. Lo que no es legítimo es un nombre que no exista.
   */
  public Set<MetodoPago> comoMetodosDePago() {
    Set<MetodoPago> metodos = EnumSet.noneOf(MetodoPago.class);
    for (String nombre : habilitados) {
      String limpio = nombre.trim().toUpperCase(Locale.ROOT);
      if (limpio.isEmpty()) {
        continue;
      }
      MetodoPago metodo = interpretar(limpio);
      if (metodo.pasarela() != ProveedorDePago.WOMPI) {
        throw new IllegalStateException(
            "tecnosport.wompi.metodos.habilitados no admite "
                + limpio
                + ": ese método no lo cobra Wompi y tiene su propia configuración.");
      }
      metodos.add(metodo);
    }
    return metodos;
  }

  private static MetodoPago interpretar(String nombre) {
    try {
      return MetodoPago.valueOf(nombre);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "tecnosport.wompi.metodos.habilitados tiene un método desconocido: " + nombre + ".", e);
    }
  }
}
