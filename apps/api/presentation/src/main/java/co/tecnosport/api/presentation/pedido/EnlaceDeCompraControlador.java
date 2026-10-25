package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.ConsultarEnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.presentation.pedido.dto.EnlaceDeCompraPublicoRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El enlace de compra que llega en la URL de la ficha (ADR-0086). Sin sesión, como el carrito: el
 * enlace es el permiso. Uno que no sirve —vencido, usado, anulado o inventado— responde lo mismo.
 */
@RestController
@RequestMapping("/api/v1/enlaces-de-compra")
public class EnlaceDeCompraControlador {

  private final ConsultarEnlaceDeCompra consultar;

  public EnlaceDeCompraControlador(ConsultarEnlaceDeCompra consultar) {
    this.consultar = Objects.requireNonNull(consultar);
  }

  @GetMapping("/{token}")
  public EnlaceDeCompraPublicoRespuesta consultar(@PathVariable String token) {
    EnlaceDeCompra enlace = consultar.ejecutar(token);
    return new EnlaceDeCompraPublicoRespuesta(enlace.varianteId(), enlace.expiraEn());
  }
}
