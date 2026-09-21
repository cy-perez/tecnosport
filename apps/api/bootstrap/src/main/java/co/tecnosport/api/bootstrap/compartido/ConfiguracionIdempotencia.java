package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.presentation.compartido.FiltroIdempotencia;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code FiltroIdempotencia} no es {@code @Component}: si lo fuera, Spring Boot lo registraría para
 * {@code /*} automáticamente. Aquí se ata a las rutas concretas que mueven dinero o inventario
 * (docs/03-api.md): crear pedido, crear un intento de pago contra Wompi y crear uno contra
 * Sistecrédito.
 *
 * <p><b>Esta lista es fácil de olvidar y cara de olvidar</b>, y ya se olvidó dos veces. El endpoint
 * de Sistecrédito nació fuera de ella: el frontend mandaba su {@code Idempotency-Key} y el filtro
 * no la miraba, así que un doble envío —el comprador que pulsa dos veces, un reintento del
 * navegador— habría abierto <b>dos transacciones de crédito</b> para el mismo pedido. La pasarela
 * probablemente habría rechazado la segunda con su código {@code 801} ("ya hay una solicitud en
 * proceso para esta persona"), pero depender del error de un proveedor no es un diseño. Un endpoint
 * nuevo que cree algo se agrega aquí el mismo día.
 */
@Configuration
public class ConfiguracionIdempotencia {

  @Bean
  public FilterRegistrationBean<FiltroIdempotencia> filtroIdempotencia(
      RepositorioIdempotencia repositorioIdempotencia, Reloj reloj) {
    FilterRegistrationBean<FiltroIdempotencia> registro =
        new FilterRegistrationBean<>(new FiltroIdempotencia(repositorioIdempotencia, reloj));
    registro.addUrlPatterns(
        "/api/v1/pedidos",
        "/api/v1/pagos/intentos",
        "/api/v1/pagos/sistecredito/intentos",
        // El ajuste por conteo escribe un movimiento de inventario desde el panel, así que entra
        // por la regla de apps/api/CLAUDE.md aunque su daño sea menor que el de los otros tres:
        // el comando es un conteo absoluto, no un delta, y dos envíos de "hay 3" dan el mismo
        // saldo. Que el diseño lo salve no es razón para dejarlo fuera de la lista — hoy lo salva
        // porque es absoluto, y quien lo cambie a un delta no va a acordarse de venir aquí.
        "/api/v1/admin/variantes/*/existencia");
    return registro;
  }
}
