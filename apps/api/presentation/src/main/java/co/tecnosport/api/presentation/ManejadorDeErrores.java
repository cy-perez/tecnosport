package co.tecnosport.api.presentation;

import co.tecnosport.api.application.carrito.CarritoNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoException;
import co.tecnosport.api.domain.carrito.LineaCarritoNoEncontradaException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
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
