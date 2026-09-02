package co.tecnosport.api.presentation.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarrito;
import co.tecnosport.api.presentation.carrito.dto.CarritoRespuesta;
import co.tecnosport.api.presentation.carrito.dto.LineaCarritoRespuesta;
import org.springframework.stereotype.Component;

@Component
public class MapeadorRespuestasCarrito {

  public CarritoRespuesta aRespuesta(Carrito carrito) {
    return new CarritoRespuesta(
        carrito.id(),
        carrito.usuarioId().orElse(null),
        carrito.lineas().stream().map(this::aRespuesta).toList(),
        carrito.creadoEn());
  }

  private LineaCarritoRespuesta aRespuesta(LineaCarrito linea) {
    return new LineaCarritoRespuesta(linea.id(), linea.varianteId(), linea.cantidad());
  }
}
