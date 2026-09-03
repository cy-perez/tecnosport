package co.tecnosport.api.presentation;

import co.tecnosport.api.application.carrito.CarritoNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoException;
import co.tecnosport.api.application.pago.MetodoDePagoNoSoportadoPorWompiException;
import co.tecnosport.api.application.pago.PagoNoEncontradoException;
import co.tecnosport.api.application.pago.PedidoNoEstaEnPagoPendienteException;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.domain.carrito.LineaCarritoNoEncontradaException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Un solo formato de error, {@code application/problem+json} (RFC 9457), ver docs/03-api.md. {@code
 * codigo} se deriva del nombre de la excepción (SlugInvalidoException -&gt; SLUG_INVALIDO) para no
 * mantener un catálogo aparte por cada regla nueva.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

  @ExceptionHandler(ProductoNoEncontradoException.class)
  public ProblemDetail productoNoEncontrado(ProductoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Producto no encontrado", excepcion);
  }

  @ExceptionHandler(CarritoNoEncontradoException.class)
  public ProblemDetail carritoNoEncontrado(CarritoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Carrito no encontrado", excepcion);
  }

  // Extiende ExcepcionDeDominio (cae a 422 por defecto más abajo), pero "no existe esa línea" es
  // un 404, no una solicitud malformada — Spring resuelve por el handler más específico.
  @ExceptionHandler(LineaCarritoNoEncontradaException.class)
  public ProblemDetail lineaCarritoNoEncontrada(LineaCarritoNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Línea de carrito no encontrada", excepcion);
  }

  @ExceptionHandler(VarianteNoEncontradaException.class)
  public ProblemDetail varianteNoEncontrada(VarianteNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Variante no encontrada", excepcion);
  }

  @ExceptionHandler(PedidoNoEncontradoException.class)
  public ProblemDetail pedidoNoEncontrado(PedidoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Pedido no encontrado", excepcion);
  }

  @ExceptionHandler(PagoNoEncontradoException.class)
  public ProblemDetail pagoNoEncontrado(PagoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Pago no encontrado", excepcion);
  }

  // 409, no 422: la solicitud está bien formada, pero el estado del inventario cambió entre que
  // el cliente vio el producto y creó el pedido — ejemplo textual en docs/03-api.md.
  @ExceptionHandler(ExistenciaInsuficienteException.class)
  public ProblemDetail existenciaInsuficiente(ExistenciaInsuficienteException excepcion) {
    return problema(HttpStatus.CONFLICT, "Existencia insuficiente", excepcion);
  }

  // Mismo criterio que ExistenciaInsuficienteException: la solicitud está bien formada, pero el
  // pedido no admite un intento de pago en su estado o método de pago actual.
  @ExceptionHandler(PedidoNoEstaEnPagoPendienteException.class)
  public ProblemDetail pedidoNoEstaEnPagoPendiente(PedidoNoEstaEnPagoPendienteException excepcion) {
    return problema(HttpStatus.CONFLICT, "Pedido no admite un intento de pago", excepcion);
  }

  @ExceptionHandler(MetodoDePagoNoSoportadoPorWompiException.class)
  public ProblemDetail metodoDePagoNoSoportadoPorWompi(
      MetodoDePagoNoSoportadoPorWompiException excepcion) {
    return problema(HttpStatus.CONFLICT, "Método de pago no soportado por Wompi", excepcion);
  }

  @ExceptionHandler({
    ExcepcionDeDominio.class,
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ProblemDetail solicitudInvalida(Exception excepcion) {
    ProblemDetail problema =
        problema(HttpStatus.UNPROCESSABLE_CONTENT, "Solicitud inválida", excepcion);
    problema.setProperty("campos", List.of());
    return problema;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail errorInterno(Exception excepcion) {
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
    problema.setTitle("Error interno");
    problema.setProperty("codigo", "ERROR_INTERNO");
    problema.setType(URI.create("https://tecnosport.co/errores/error-interno"));
    return problema;
  }

  private ProblemDetail problema(HttpStatus estado, String titulo, Exception excepcion) {
    String codigo = codigoDesde(excepcion);
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, excepcion.getMessage());
    problema.setTitle(titulo);
    problema.setProperty("codigo", codigo);
    problema.setType(
        URI.create(
            "https://tecnosport.co/errores/" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
    return problema;
  }

  private String codigoDesde(Exception excepcion) {
    String nombre = excepcion.getClass().getSimpleName().replace("Exception", "");
    StringBuilder codigo = new StringBuilder();
    for (int i = 0; i < nombre.length(); i++) {
      char letra = nombre.charAt(i);
      if (Character.isUpperCase(letra) && i > 0) {
        codigo.append('_');
      }
      codigo.append(Character.toUpperCase(letra));
    }
    return codigo.toString();
  }
}
