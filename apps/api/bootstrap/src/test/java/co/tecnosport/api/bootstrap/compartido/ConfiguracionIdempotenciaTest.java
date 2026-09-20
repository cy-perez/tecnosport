package co.tecnosport.api.bootstrap.compartido;

import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import co.tecnosport.api.presentation.compartido.FiltroIdempotencia;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Una prueba sobre una lista de cadenas, que parece poco y no lo es: esta lista es lo único que
 * hace que el {@code Idempotency-Key} sirva para algo, y estar fuera de ella no se nota. El cliente
 * manda la cabecera igual, el servidor responde 200 igual, y lo único distinto es que un doble
 * envío crea dos veces lo que no debería crearse dos veces.
 *
 * <p>Pasó con el endpoint de Sistecrédito ({@code adr/0048}): nació fuera de la lista, con su
 * cabecera puesta en el frontend y sin nadie mirándola. Un doble envío habría abierto dos
 * transacciones de crédito a nombre de la misma persona para el mismo pedido.
 */
class ConfiguracionIdempotenciaTest {

  private Collection<String> rutasProtegidas() {
    FilterRegistrationBean<FiltroIdempotencia> registro =
        new ConfiguracionIdempotencia()
            .filtroIdempotencia(new RepositorioIdempotenciaFalso(), () -> Instant.now());
    return registro.getUrlPatterns();
  }

  @Test
  void crearUnPedidoEstaProtegido() {
    assertTrue(rutasProtegidas().contains("/api/v1/pedidos"));
  }

  @Test
  void losDosEndpointsQueCreanUnIntentoDePagoEstanProtegidos() {
    Collection<String> rutas = rutasProtegidas();

    assertTrue(rutas.contains("/api/v1/pagos/intentos"), "falta el intento de Wompi");
    assertTrue(
        rutas.contains("/api/v1/pagos/sistecredito/intentos"),
        "falta el intento de Sistecrédito: un doble envío abriría dos créditos");
  }

  /** Lo mínimo para construir el filtro; esta prueba solo mira a qué rutas se ata. */
  private static final class RepositorioIdempotenciaFalso implements RepositorioIdempotencia {

    @Override
    public Optional<RespuestaIdempotente> buscarCompletada(String llave, Instant ahora) {
      return Optional.empty();
    }

    @Override
    public boolean reclamar(String llave, String metodo, String ruta, Instant ahora) {
      return true;
    }

    @Override
    public void completar(String llave, RespuestaIdempotente respuesta, Instant ahora) {
      // sin efecto
    }

    @Override
    public void liberar(String llave) {
      // sin efecto
    }
  }
}
