package co.tecnosport.api.presentation;

import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.carrito.CarritoNoEncontradoException;
import co.tecnosport.api.application.catalogo.AtributoNoEncontradoException;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.SetRotacionNoEncontradoException;
import co.tecnosport.api.application.catalogo.SetRotacionPublicadoExistenteException;
import co.tecnosport.api.application.catalogo.SkuYaEnUsoException;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.garantia.LineaNoEsDelPedidoException;
import co.tecnosport.api.application.garantia.ReclamacionGarantiaNoEncontradaException;
import co.tecnosport.api.application.pago.MetodoDePagoNoSoportadoPorWompiException;
import co.tecnosport.api.application.pago.PagoNoEncontradoException;
import co.tecnosport.api.application.pago.PedidoNoEstaEnPagoPendienteException;
import co.tecnosport.api.application.pedido.ContraentregaNoDisponibleException;
import co.tecnosport.api.application.pedido.MetodoDePagoNoEsTransferenciaManualException;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.VarianteNoEncontradaException;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.retracto.PedidoSinEntregarException;
import co.tecnosport.api.application.retracto.RetractoYaRadicadoException;
import co.tecnosport.api.application.retracto.SolicitudRetractoNoEncontradaException;
import co.tecnosport.api.application.reversion.SolicitudReversionNoEncontradaException;
import co.tecnosport.api.application.usuario.CredencialesInvalidasException;
import co.tecnosport.api.application.usuario.SesionDeRefrescoComprometidaException;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.domain.carrito.LineaCarritoNoEncontradaException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.usuario.CorreoSinVerificarException;
import co.tecnosport.api.domain.usuario.CorreoYaRegistradoException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

  @ExceptionHandler(ProductoNoEncontradoException.class)
  public ProblemDetail productoNoEncontrado(ProductoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Producto no encontrado", excepcion);
  }

  @ExceptionHandler(ProductoNoEncontradoPorIdException.class)
  public ProblemDetail productoNoEncontradoPorId(ProductoNoEncontradoPorIdException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Producto no encontrado", excepcion);
  }

  @ExceptionHandler(CarritoNoEncontradoException.class)
  public ProblemDetail carritoNoEncontrado(CarritoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Carrito no encontrado", excepcion);
  }

  @ExceptionHandler(MarcaNoEncontradaException.class)
  public ProblemDetail marcaNoEncontrada(MarcaNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Marca no encontrada", excepcion);
  }

  @ExceptionHandler(CategoriaNoEncontradaException.class)
  public ProblemDetail categoriaNoEncontrada(CategoriaNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Categoría no encontrada", excepcion);
  }

  @ExceptionHandler(AtributoNoEncontradoException.class)
  public ProblemDetail atributoNoEncontrado(AtributoNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Atributo no encontrado", excepcion);
  }

  // El navegador nunca terminó el PUT a Cloud Storage, o lo hizo contra un objectKey distinto al
  // que se firmó — ConfirmarImagenPrincipal lo verifica contra el almacén real, no confía en el
  // cliente.
  @ExceptionHandler(ObjetoDeImagenNoEncontradoException.class)
  public ProblemDetail objetoDeImagenNoEncontrado(ObjetoDeImagenNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Imagen no encontrada", excepcion);
  }

  // 409, mismo criterio que CorreoYaRegistradoException: la solicitud está bien formada, el
  // conflicto es que el SKU ya está en uso en otro producto.
  @ExceptionHandler(SetRotacionNoEncontradoException.class)
  public ProblemDetail setRotacionNoEncontrado(SetRotacionNoEncontradoException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Set de rotación no encontrado", excepcion);
  }

  @ExceptionHandler(SetRotacionPublicadoExistenteException.class)
  public ProblemDetail setRotacionPublicadoExistente(
      SetRotacionPublicadoExistenteException excepcion) {
    return problema(HttpStatus.CONFLICT, "El producto ya tiene un set publicado", excepcion);
  }

  @ExceptionHandler(SolicitudRetractoNoEncontradaException.class)
  public ProblemDetail solicitudRetractoNoEncontrada(
      SolicitudRetractoNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Solicitud de retracto no encontrada", excepcion);
  }

  // 409 y no 422: el pedido existe y la peticion esta bien formada. Lo que pasa es que todavia no
  // se puede, y eso puede cambiar solo — con la entrega.
  @ExceptionHandler(PedidoSinEntregarException.class)
  public ProblemDetail pedidoSinEntregar(PedidoSinEntregarException excepcion) {
    return problema(HttpStatus.CONFLICT, "El pedido todavia no se ha entregado", excepcion);
  }

  @ExceptionHandler(RetractoYaRadicadoException.class)
  public ProblemDetail retractoYaRadicado(RetractoYaRadicadoException excepcion) {
    return problema(HttpStatus.CONFLICT, "Retracto ya radicado", excepcion);
  }

  @ExceptionHandler(SolicitudAtencionNoEncontradaException.class)
  public ProblemDetail solicitudAtencionNoEncontrada(
      SolicitudAtencionNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Solicitud de atencion no encontrada", excepcion);
  }

  @ExceptionHandler(ReclamacionGarantiaNoEncontradaException.class)
  public ProblemDetail reclamacionGarantiaNoEncontrada(
      ReclamacionGarantiaNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Reclamacion de garantia no encontrada", excepcion);
  }

  @ExceptionHandler(LineaNoEsDelPedidoException.class)
  public ProblemDetail lineaNoEsDelPedido(LineaNoEsDelPedidoException excepcion) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "La linea no es de ese pedido", excepcion);
  }

  @ExceptionHandler(SolicitudReversionNoEncontradaException.class)
  public ProblemDetail solicitudReversionNoEncontrada(
      SolicitudReversionNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Solicitud de reversion no encontrada", excepcion);
  }

  @ExceptionHandler(ReintegroRequeridoException.class)
  public ProblemDetail reintegroRequerido(ReintegroRequeridoException excepcion) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Falta el reintegro", excepcion);
  }

  @ExceptionHandler(MontoDeReintegroInvalidoException.class)
  public ProblemDetail montoDeReintegroInvalido(MontoDeReintegroInvalidoException excepcion) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Monto de reintegro invalido", excepcion);
  }

  @ExceptionHandler(SkuYaEnUsoException.class)
  public ProblemDetail skuYaEnUso(SkuYaEnUsoException excepcion) {
    return problema(HttpStatus.CONFLICT, "SKU ya en uso", excepcion);
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

  // Mismo criterio que ExistenciaInsuficienteException: la solicitud está bien formada, pero
  // contraentrega ya no es elegible para este pedido (cobertura, monto, categoría o rechazo
  // previo) — el cliente pudo haber consultado /metodos-de-pago-disponibles hace un rato.
  @ExceptionHandler(ContraentregaNoDisponibleException.class)
  public ProblemDetail contraentregaNoDisponible(ContraentregaNoDisponibleException excepcion) {
    return problema(HttpStatus.CONFLICT, "Contraentrega no disponible", excepcion);
  }

  @ExceptionHandler(MetodoDePagoNoSoportadoPorWompiException.class)
  public ProblemDetail metodoDePagoNoSoportadoPorWompi(
      MetodoDePagoNoSoportadoPorWompiException excepcion) {
    return problema(HttpStatus.CONFLICT, "Método de pago no soportado por Wompi", excepcion);
  }

  @ExceptionHandler(MetodoDePagoNoEsTransferenciaManualException.class)
  public ProblemDetail metodoDePagoNoEsTransferenciaManual(
      MetodoDePagoNoEsTransferenciaManualException excepcion) {
    return problema(HttpStatus.CONFLICT, "Método de pago no es transferencia manual", excepcion);
  }

  @ExceptionHandler(CredencialesInvalidasException.class)
  public ProblemDetail credencialesInvalidas(CredencialesInvalidasException excepcion) {
    return problema(HttpStatus.UNAUTHORIZED, "Credenciales inválidas", excepcion);
  }

  // 409, mismo criterio que ExistenciaInsuficienteException: la solicitud está bien formada, el
  // conflicto es que ya existe una cuenta con ese correo.
  @ExceptionHandler(CorreoYaRegistradoException.class)
  public ProblemDetail correoYaRegistrado(CorreoYaRegistradoException excepcion) {
    return problema(HttpStatus.CONFLICT, "Correo ya registrado", excepcion);
  }

  // 403, no 401: las credenciales sí son correctas, pero la cuenta no puede iniciar sesión hasta
  // verificar el correo — a diferencia de CredencialesInvalidasException, este mensaje sí se
  // puede revelar tal cual (no es información sensible, es accionable).
  @ExceptionHandler(CorreoSinVerificarException.class)
  public ProblemDetail correoSinVerificar(CorreoSinVerificarException excepcion) {
    return problema(HttpStatus.FORBIDDEN, "Correo sin verificar", excepcion);
  }

  // 429, mismo código que ya escribe a mano FiltroLimiteIntentos para el límite por IP — el
  // codigoDesde(...) genérico ya produce "LIMITE_DE_INTENTOS_EXCEDIDO" para los dos casos.
  @ExceptionHandler(LimiteDeIntentosExcedidoException.class)
  public ProblemDetail limiteDeIntentosExcedido(LimiteDeIntentosExcedidoException excepcion) {
    return problema(HttpStatus.TOO_MANY_REQUESTS, "Límite de intentos excedido", excepcion);
  }

  @ExceptionHandler(SesionDeRefrescoInvalidaException.class)
  public ProblemDetail sesionDeRefrescoInvalida(SesionDeRefrescoInvalidaException excepcion) {
    return problema(HttpStatus.UNAUTHORIZED, "Sesión inválida", excepcion);
  }

  // Señal de robo del token (docs/08-seguridad-legal.md): RefrescarToken ya revocó toda la
  // familia antes de que esta excepción llegue aquí — se registra para poder monitorearlo, no
  // solo para responderle al cliente.
  @ExceptionHandler(SesionDeRefrescoComprometidaException.class)
  public ProblemDetail sesionDeRefrescoComprometida(
      SesionDeRefrescoComprometidaException excepcion) {
    log.warn("Sesión de refresco reutilizada: posible robo de token, familia revocada.");
    return problema(HttpStatus.UNAUTHORIZED, "Sesión comprometida", excepcion);
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
    log.error("Error inesperado sin manejar", excepcion);
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
