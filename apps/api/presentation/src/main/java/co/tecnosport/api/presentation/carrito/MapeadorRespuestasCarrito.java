package co.tecnosport.api.presentation.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarrito;
import co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea;
import co.tecnosport.api.presentation.carrito.dto.CarritoRespuesta;
import co.tecnosport.api.presentation.carrito.dto.LineaCarritoRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasCarrito {

  private final TopeDeUnidadesPorLinea tope;

  public MapeadorRespuestasCarrito(TopeDeUnidadesPorLinea tope) {
    this.tope = Objects.requireNonNull(tope);
  }

  public CarritoRespuesta aRespuesta(Carrito carrito) {
    return new CarritoRespuesta(
        carrito.id(),
        carrito.usuarioId().orElse(null),
        carrito.lineas().stream().map(this::aRespuesta).toList(),
        carrito.creadoEn(),
        tope.maximo());
  }

  private LineaCarritoRespuesta aRespuesta(LineaCarrito linea) {
    return new LineaCarritoRespuesta(
        linea.id(), linea.varianteId(), linea.cantidad(), linea.enlaceCompra());
  }
}
