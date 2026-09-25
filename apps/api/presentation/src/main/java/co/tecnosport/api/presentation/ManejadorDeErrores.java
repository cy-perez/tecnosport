package co.tecnosport.api.presentation;

import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.carrito.CarritoNoEncontradoException;
import co.tecnosport.api.application.catalogo.AtributoNoEncontradoException;
import co.tecnosport.api.application.catalogo.CategoriaConHijasException;
import co.tecnosport.api.application.catalogo.CategoriaConProductosException;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.CategoriaSlugYaExisteException;
import co.tecnosport.api.application.catalogo.CicloDeCategoriasException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.MarcaYaExisteException;
import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoConVentasException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.ProductoPublicadoException;
import co.tecnosport.api.application.catalogo.ProfundidadDeCategoriaExcedidaException;
import co.tecnosport.api.application.catalogo.SetRotacionNoEncontradoException;
import co.tecnosport.api.application.catalogo.SetRotacionPublicadoExistenteException;
import co.tecnosport.api.application.catalogo.SkuYaEnUsoException;
import co.tecnosport.api.application.catalogo.TasaIvaNoPermitidaException;
import co.tecnosport.api.application.catalogo.VarianteNoEncontradaPorIdException;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.envio.AcuseNoAplicableException;
import co.tecnosport.api.application.envio.ArticuloNoAsegurableException;
import co.tecnosport.api.application.envio.ArticuloSinMedidasException;
import co.tecnosport.api.application.envio.CotizacionNoDisponibleException;
import co.tecnosport.api.application.envio.CotizacionRechazadaException;
import co.tecnosport.api.application.envio.EmisionNoAplicableException;
import co.tecnosport.api.application.envio.EmisionNoEncontradaException;
import co.tecnosport.api.application.envio.EmisionRechazadaException;
import co.tecnosport.api.application.envio.EmisionYaEnCursoException;
import co.tecnosport.api.application.envio.EnvioSinCoberturaException;
import co.tecnosport.api.application.envio.GuiaNoEncontradaException;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.garantia.LineaNoEsDelPedidoException;
import co.tecnosport.api.application.garantia.ReclamacionGarantiaNoEncontradaException;
import co.tecnosport.api.application.pago.MetodoDePagoNoEsDeSistecreditoException;
import co.tecnosport.api.application.pago.MetodoDePagoNoSoportadoPorWompiException;
import co.tecnosport.api.application.pago.PagoNoEncontradoException;
import co.tecnosport.api.application.pago.PedidoNoEstaEnPagoPendienteException;
import co.tecnosport.api.application.pago.ReferenciaDePagoYaExisteException;
import co.tecnosport.api.application.pago.SistecreditoNoEntregoLaUrlDePagoException;
import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
import co.tecnosport.api.application.pedido.ContraentregaNoDisponibleException;
import co.tecnosport.api.application.pedido.MetodoDePagoNoEsTransferenciaManualException;
import co.tecnosport.api.application.pedido.MetodoDePagoNoHabilitadoException;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.SistecreditoNoDisponibleException;
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
import co.tecnosport.api.domain.catalogo.GaleriaLlenaException;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaDuplicadaException;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaNoEncontradaException;
import co.tecnosport.api.domain.catalogo.ProductoSinImagenPrincipalException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.usuario.CorreoSinVerificarException;
import co.tecnosport.api.domain.usuario.CorreoYaRegistradoException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  @ExceptionHandler(VarianteNoEncontradaPorIdException.class)
  public ProblemDetail varianteNoEncontradaPorId(VarianteNoEncontradaPorIdException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Variante no encontrada", excepcion);
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

  // Sin esto, intentar publicar un producto sin imagen saldria como 500: la invariante existe en el
  // dominio desde la Fase 1 y nunca tuvo traduccion HTTP, porque nada la podia disparar.
  @ExceptionHandler(ProductoSinImagenPrincipalException.class)
  public ProblemDetail productoSinImagenPrincipal(ProductoSinImagenPrincipalException excepcion) {
    return problema(HttpStatus.CONFLICT, "El producto no tiene imagen principal", excepcion);
  }

  // 409 por el mismo criterio que el set publicado: la petición está bien formada y el producto
  // existe; lo que pasa es que ya no cabe otra, y eso se arregla quitando una.
  @ExceptionHandler(GaleriaLlenaException.class)
  public ProblemDetail galeriaLlena(GaleriaLlenaException excepcion) {
    return problema(HttpStatus.CONFLICT, "La galería está llena", excepcion);
  }

  // 409 y no 422: subir dos veces la misma foto no es un cuerpo mal formado, es un conflicto con
  // lo que ya hay. Y es accionable — dice exactamente qué pasó.
  @ExceptionHandler(ImagenDeGaleriaDuplicadaException.class)
  public ProblemDetail imagenDeGaleriaDuplicada(ImagenDeGaleriaDuplicadaException excepcion) {
    return problema(HttpStatus.CONFLICT, "Esa imagen ya está en la galería", excepcion);
  }

  @ExceptionHandler(ImagenDeGaleriaNoEncontradaException.class)
  public ProblemDetail imagenDeGaleriaNoEncontrada(ImagenDeGaleriaNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Imagen no encontrada en la galería", excepcion);
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

  /**
   * {@code 409} y no {@code 422}: el nombre que mandaron es perfectamente válido, lo que pasa es
   * que el estado del catálogo lo rechaza. Mismo criterio que el SKU ya en uso, justo debajo.
   */
  @ExceptionHandler(MarcaYaExisteException.class)
  public ProblemDetail marcaYaExiste(MarcaYaExisteException excepcion) {
    return problema(HttpStatus.CONFLICT, "Marca ya existe", excepcion);
  }

  @ExceptionHandler(SkuYaEnUsoException.class)
  public ProblemDetail skuYaEnUso(SkuYaEnUsoException excepcion) {
    return problema(HttpStatus.CONFLICT, "SKU ya en uso", excepcion);
  }

  @ExceptionHandler(CategoriaSlugYaExisteException.class)
  public ProblemDetail categoriaSlugYaExiste(CategoriaSlugYaExisteException excepcion) {
    return problema(HttpStatus.CONFLICT, "Slug de categoría ya en uso", excepcion);
  }

  /**
   * Los tres rechazos del árbol de categorías son {@code 409} y no {@code 422} por el mismo motivo
   * que la marca repetida: lo que mandaron es válido —un nombre, un padre que existe—, y lo que lo
   * rechaza es el estado del catálogo. Cambiar ese estado, moviendo los productos o borrando las
   * subcategorías, hace que la misma petición pase.
   */
  @ExceptionHandler(CategoriaConHijasException.class)
  public ProblemDetail categoriaConHijas(CategoriaConHijasException excepcion) {
    return problema(HttpStatus.CONFLICT, "La categoría tiene subcategorías", excepcion);
  }

  @ExceptionHandler(CategoriaNoEsHojaException.class)
  public ProblemDetail categoriaNoEsHoja(CategoriaNoEsHojaException excepcion) {
    return problema(HttpStatus.CONFLICT, "La categoría no es una hoja", excepcion);
  }

  @ExceptionHandler(CategoriaConProductosException.class)
  public ProblemDetail categoriaConProductos(CategoriaConProductosException excepcion) {
    return problema(HttpStatus.CONFLICT, "La categoría tiene productos", excepcion);
  }

  /**
   * Los dos rechazos del borrado de un producto, {@code 409} por el mismo motivo que los del árbol
   * de categorías: el id que mandaron es válido y lo que lo rechaza es el estado del catálogo. El
   * primero se arregla retirando el producto y la misma petición pasa; el segundo no se arregla
   * nunca, y ahí el {@code detail} es lo que explica que la salida es retirar, no borrar.
   */
  @ExceptionHandler(ProductoPublicadoException.class)
  public ProblemDetail productoPublicado(ProductoPublicadoException excepcion) {
    return problema(HttpStatus.CONFLICT, "El producto está publicado", excepcion);
  }

  @ExceptionHandler(ProductoConVentasException.class)
  public ProblemDetail productoConVentas(ProductoConVentasException excepcion) {
    return problema(HttpStatus.CONFLICT, "El producto tiene ventas", excepcion);
  }

  @ExceptionHandler(ProfundidadDeCategoriaExcedidaException.class)
  public ProblemDetail profundidadDeCategoria(ProfundidadDeCategoriaExcedidaException excepcion) {
    return problema(HttpStatus.CONFLICT, "Profundidad de categoría excedida", excepcion);
  }

  /**
   * {@code 422} y no {@code 409}, a diferencia de sus tres vecinas de arriba: colgar una categoría
   * de sí misma no lo arregla ningún cambio del catálogo. La petición está mal, no a destiempo.
   */
  @ExceptionHandler(CicloDeCategoriasException.class)
  public ProblemDetail cicloDeCategorias(CicloDeCategoriasException excepcion) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Ciclo de categorías", excepcion);
  }

  @ExceptionHandler(TasaIvaNoPermitidaException.class)
  public ProblemDetail tasaIvaNoPermitida(TasaIvaNoPermitidaException excepcion) {
    return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Tasa de IVA no permitida", excepcion);
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
  // Ninguna transportadora cotiza ese destino. Es un caso de negocio, no una falla: un 502
  // echaría la culpa al proveedor cuando lo que pasa es que esa ciudad hoy no se despacha. El
  // checkout lo traduce a "solo recogida en el punto" (docs/03-api.md).
  @ExceptionHandler(EnvioSinCoberturaException.class)
  public ProblemDetail envioSinCobertura(EnvioSinCoberturaException excepcion) {
    return problema(HttpStatus.CONFLICT, "Envío sin cobertura", excepcion);
  }

  // Un artículo vale más de lo que la transportadora asegura (adr/0036). 409 y no 422 por el mismo
  // criterio que el de arriba: la solicitud está bien formada y el conflicto es con el estado del
  // negocio. Se diferencia de ENVIO_SIN_COBERTURA en algo que al checkout le importa: aquel se
  // arregla cambiando la dirección y este no se arregla de ninguna manera, así que el texto que ve
  // el comprador no puede ser el mismo.
  //
  // Los artículos culpables van en una propiedad aparte y no solo dentro del mensaje: el cliente
  // tiene que poder nombrarlos sin leerle la prosa a un `detail`.
  @ExceptionHandler(ArticuloNoAsegurableException.class)
  public ProblemDetail articuloNoAsegurable(ArticuloNoAsegurableException excepcion) {
    ProblemDetail problema =
        problema(HttpStatus.CONFLICT, "Artículo no asegurable para envío", excepcion);
    problema.setProperty(
        "articulos",
        excepcion.articulos().stream()
            .map(
                articulo ->
                    Map.of("varianteId", articulo.varianteId(), "nombre", articulo.nombre()))
            .toList());
    return problema;
  }

  // La hermana de la de arriba, con la misma forma porque para quien compra son el mismo hecho:
  // esto no se puede enviar, se recoge en el punto. Ver ArticuloSinMedidasException.
  @ExceptionHandler(ArticuloSinMedidasException.class)
  public ProblemDetail articuloSinMedidas(ArticuloSinMedidasException excepcion) {
    ProblemDetail problema =
        problema(HttpStatus.CONFLICT, "Artículo sin medidas para envío", excepcion);
    problema.setProperty(
        "articulos",
        excepcion.articulos().stream()
            .map(
                articulo ->
                    Map.of("varianteId", articulo.varianteId(), "nombre", articulo.nombre()))
            .toList());
    return problema;
  }

  // No se pudo cotizar, que no es lo mismo que no haber cobertura: uno le pide al comprador
  // cambiar la dirección y el otro volver a intentar. 503 y no 409 porque no es un conflicto con
  // el estado del negocio — es un servicio del que dependemos que no respondió, y reintentar sirve
  // (docs/13 §6.9, docs/03-api.md).
  @ExceptionHandler(CotizacionNoDisponibleException.class)
  public ProblemDetail cotizacionNoDisponible(CotizacionNoDisponibleException excepcion) {
    return problema(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo cotizar el envío", excepcion);
  }

  // Y el otro lado de esa moneda: el proveedor sí respondió, y rechazó nuestro cuerpo. 409 y no el
  // 503 de arriba porque reintentar no lo arregla —Skydropx deduplica las cotizaciones por
  // contenido, así que la misma pregunta trae el mismo rechazo— y prometerle al cliente que
  // insistir
  // sirve es peor que decirle que no hay domicilio. Mismo criterio que ENVIO_SIN_COBERTURA y
  // ARTICULO_NO_ASEGURABLE: el checkout lo traduce a recogida en el punto (docs/03-api.md).
  @ExceptionHandler(CotizacionRechazadaException.class)
  public ProblemDetail cotizacionRechazada(CotizacionRechazadaException excepcion) {
    return problema(HttpStatus.CONFLICT, "El proveedor rechazó la cotización", excepcion);
  }

  // El pedido no admite que se le emita una guia ahora: retiro en punto, o un estado que no es
  // EN_PREPARACION. 409 porque es un conflicto con el estado del negocio y reintentar no lo
  // arregla.
  @ExceptionHandler(EmisionNoAplicableException.class)
  public ProblemDetail emisionNoAplicable(EmisionNoAplicableException excepcion) {
    return problema(HttpStatus.CONFLICT, "No se puede emitir la guía", excepcion);
  }

  // Se acuso algo que no estaba pidiendo revision: una emision sana, por ejemplo. 409 porque es un
  // conflicto con el estado y reintentar no lo arregla. Se rechaza en vez de guardarlo igual porque
  // un acuse sobre algo sano deja escrito que ahi hubo un problema que nunca existio.
  @ExceptionHandler(AcuseNoAplicableException.class)
  public ProblemDetail acuseNoAplicable(AcuseNoAplicableException excepcion) {
    return problema(HttpStatus.CONFLICT, "No hay nada que revisar", excepcion);
  }

  @ExceptionHandler(GuiaNoEncontradaException.class)
  public ProblemDetail guiaNoEncontrada(GuiaNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Guía no encontrada", excepcion);
  }

  @ExceptionHandler(EmisionNoEncontradaException.class)
  public ProblemDetail emisionNoEncontrada(EmisionNoEncontradaException excepcion) {
    return problema(HttpStatus.NOT_FOUND, "Emisión no encontrada", excepcion);
  }

  // Ya hay una emision abierta para este pedido. Es la puerta que cuesta plata: la plataforma cobra
  // al crear, asi que dos solicitudes son dos cobros por lo mismo. 409 y el mensaje dice que hay
  // que
  // esperar, no reintentar.
  @ExceptionHandler(EmisionYaEnCursoException.class)
  public ProblemDetail emisionYaEnCurso(EmisionYaEnCursoException excepcion) {
    return problema(HttpStatus.CONFLICT, "La emisión ya está en curso", excepcion);
  }

  // La plataforma rechazo la emision. 502 y no 503: la peticion llego y la contestaron diciendo que
  // no, que es distinto de un proveedor que no responde. El detalle trae el cuerpo del proveedor,
  // que es donde de verdad esta el motivo (docs/13 §6.10).
  @ExceptionHandler(EmisionRechazadaException.class)
  public ProblemDetail emisionRechazada(EmisionRechazadaException excepcion) {
    // Sin credenciales no es culpa de la transportadora: es un despliegue mal configurado, y
    // decirle a quien despacha que la transportadora dijo que no lo manda a llamar a Skydropx a
    // preguntar por algo nuestro. Los cuatro motivos existen porque piden cosas distintas de quien
    // opera; agruparlos todos en un 502 tiraba esa distinción justo donde se nota.
    if (excepcion.motivo() == ResultadoEmision.Motivo.SIN_CREDENCIALES) {
      return problema(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "La integración de envíos no está configurada",
          excepcion);
    }
    return problema(HttpStatus.BAD_GATEWAY, "La transportadora rechazó la emisión", excepcion);
  }

  // Sistecrédito no está disponible para ESTE pedido: hoy, porque el carrito no llega al monto
  // mínimo del crédito. Mismo 409 y mismo motivo que contraentrega — la petición está bien formada
  // y el cliente pudo haber consultado /metodos-de-pago-disponibles con otro carrito.
  @ExceptionHandler(SistecreditoNoDisponibleException.class)
  public ProblemDetail sistecreditoNoDisponible(SistecreditoNoDisponibleException excepcion) {
    return problema(HttpStatus.CONFLICT, "Sistecrédito no disponible", excepcion);
  }

  @ExceptionHandler(MetodoDePagoNoEsDeSistecreditoException.class)
  public ProblemDetail metodoDePagoNoEsDeSistecredito(
      MetodoDePagoNoEsDeSistecreditoException excepcion) {
    return problema(HttpStatus.CONFLICT, "Método de pago no es de Sistecrédito", excepcion);
  }

  /**
   * La transacción se creó y el medio de pago no entregó URL. Viaja <b>el código</b> y no el texto
   * del proveedor, y la diferencia importa: al comprador se le cuentan cosas distintas según cuál
   * sea —{@code 801} es "ya tienes una solicitud en curso"; {@code 802} es "el monto no alcanza"— y
   * con el código el frontend elige su propio mensaje traducido.
   *
   * <p><b>El texto crudo de Sistecrédito no sale.</b> El del {@code 801} dice que esa persona ya
   * tiene una solicitud de crédito en curso, y devolverlo convertiría este endpoint en un oráculo
   * público sobre el estado crediticio de cualquier cédula que alguien quisiera probar — Ley 1266
   * además de la 1581, y justo lo contrario de lo que pide docs/08-seguridad-legal.md. Queda en los
   * registros del servidor, que es donde sirve para diagnosticar.
   */
  @ExceptionHandler(SistecreditoNoEntregoLaUrlDePagoException.class)
  public ProblemDetail sistecreditoNoEntregoLaUrl(
      SistecreditoNoEntregoLaUrlDePagoException excepcion) {
    ProblemDetail detalle =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Sistecrédito no entregó una URL de pago para este pedido.");
    detalle.setTitle("Sistecrédito no entregó la URL de pago");
    detalle.setProperty("codigoSistecredito", excepcion.codigo());
    detalle.setProperty("estadoSistecredito", excepcion.estado());
    return detalle;
  }

  /**
   * Dos peticiones de intento de pago sobre el mismo pedido calcularon el mismo número —sale de
   * contar los pagos del pedido— y la segunda chocó contra el {@code unique} de {@code
   * pago.referencia}. 409 y no 500: la petición es válida, lo que pasa es que llegó tarde, y quien
   * la mandó puede volver a pedir el intento y obtener el número siguiente.
   *
   * <p>Antes este choque no llegaba aquí: {@code RepositorioPagosJpa} lo traducía a "evento ya
   * registrado", que por el camino del webhook se contesta con un 200.
   */
  @ExceptionHandler(ReferenciaDePagoYaExisteException.class)
  public ProblemDetail referenciaDePagoYaExiste(ReferenciaDePagoYaExisteException excepcion) {
    return problema(HttpStatus.CONFLICT, "Referencia de pago ya existe", excepcion);
  }

  /**
   * La pasarela no contestó. 503 y no 409: no es una respuesta de negocio, es una caída, y el
   * comprador puede reintentar o elegir otro medio.
   *
   * <p><b>Aquí tampoco sale el texto crudo de Sistecrédito, y esa es la corrección.</b> El mensaje
   * de esta excepción lleva concatenado el {@code message} del proveedor —{@code
   * SistecreditoClient} lo compone así para que el registro del servidor sirva para diagnosticar— y
   * {@code problema(...)} publicaba {@code getMessage()} tal cual en el {@code detail}. O sea que
   * la misma fuga que el manejador de arriba bloquea a conciencia, citando la Ley 1266, salía
   * entera por el manejador de al lado. Se queda en el registro, que es donde sirve; el frontend
   * elige su texto por el {@code codigo}, como todos los demás.
   */
  @ExceptionHandler(SistecreditoNoRespondeException.class)
  public ProblemDetail sistecreditoNoResponde(SistecreditoNoRespondeException excepcion) {
    log.error("Sistecrédito no respondió.", excepcion);
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "No pudimos comunicarnos con Sistecrédito en este momento.");
    problema.setTitle("Sistecrédito no responde");
    problema.setProperty("codigo", "SISTECREDITO_NO_RESPONDE");
    problema.setType(URI.create("https://tecnosport.co/errores/sistecredito-no-responde"));
    return problema;
  }

  // contraentrega ya no es elegible para este pedido (cobertura, monto, categoría o rechazo
  // previo) — el cliente pudo haber consultado /metodos-de-pago-disponibles hace un rato.
  @ExceptionHandler(ContraentregaNoDisponibleException.class)
  public ProblemDetail contraentregaNoDisponible(ContraentregaNoDisponibleException excepcion) {
    return problema(HttpStatus.CONFLICT, "Contraentrega no disponible", excepcion);
  }

  // El método no lo ofrece el negocio hoy (la cuenta de la pasarela no lo tiene activado). 409 y no
  // 400: la petición está bien formada y el método existe; lo que cambió es qué se acepta, y el
  // cliente pudo haber consultado /metodos-de-pago-disponibles antes de ese cambio.
  @ExceptionHandler(MetodoDePagoNoHabilitadoException.class)
  public ProblemDetail metodoDePagoNoHabilitado(MetodoDePagoNoHabilitadoException excepcion) {
    return problema(HttpStatus.CONFLICT, "Método de pago no habilitado", excepcion);
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

  /**
   * <b>Una ruta que no existe es un 404, no un error nuestro.</b> Sin esto caía en el manejador de
   * abajo: 500, con traza en el registro y {@code ERROR_INTERNO} en el cuerpo. Medido contra dev el
   * 23 de septiembre de 2026, y el daño no es cosmético en ninguno de sus tres lados. Una pasarela
   * que reintenta ante 5xx —Sistecrédito y Wompi lo hacen— convierte una URL mal configurada en un
   * bucle de reintentos en vez de en un fallo claro; las alertas de 5xx que {@code docs/07} promete
   * para producción se llenan de ruido que tapa los errores de verdad; y cualquiera que teclee mal
   * una dirección deja una traza en los registros.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail rutaNoEncontrada(NoResourceFoundException excepcion) {
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Esta dirección no existe.");
    problema.setTitle("Ruta no encontrada");
    problema.setProperty("codigo", "RUTA_NO_ENCONTRADA");
    problema.setType(URI.create("https://tecnosport.co/errores/ruta-no-encontrada"));
    return problema;
  }

  /**
   * {@code MissingServletRequestParameterException} entró en esta lista el 23 de septiembre de
   * 2026: un parámetro de consulta obligatorio que falta salía como 500. Se encontró pidiendo
   * {@code GET /pedidos/{id}/seguimiento} sin {@code correo} — con el parámetro puesto y un pedido
   * inexistente la respuesta ya era el 404 correcto, así que lo que fallaba era solo esto. Va con
   * los demás 422 y no con un 400 por lo mismo que el tipo que no convierte: la petición se
   * entiende, lo que no se puede es procesarla.
   */
  @ExceptionHandler({ExcepcionDeDominio.class, IllegalArgumentException.class})
  public ProblemDetail solicitudInvalida(Exception excepcion) {
    ProblemDetail problema =
        problema(HttpStatus.UNPROCESSABLE_CONTENT, "Solicitud inválida", excepcion);
    problema.setProperty("campos", List.of());
    return problema;
  }

  /**
   * Las dos de arriba son nuestras y su mensaje está escrito para que alguien lo lea; estas tres
   * las escribe el framework y su mensaje describe <b>nuestras clases</b>: el nombre con paquete
   * completo del DTO, la cadena de referencia que Jackson recorrió, la posición del parser. Iban en
   * la misma lista, así que cualquiera que mandara un JSON mal formado recibía de vuelta la
   * estructura interna del servidor.
   *
   * <p>El {@code codigo} no cambia —{@code HTTP_MESSAGE_NOT_READABLE}, {@code
   * METHOD_ARGUMENT_TYPE_MISMATCH}, {@code MISSING_SERVLET_REQUEST_PARAMETER}— y es lo único que el
   * frontend usa: {@code mensaje-de-error.ts} traduce por código y nunca por {@code detail},
   * precisamente porque la frase de Java viene en un solo idioma y fuera de Transloco. Así que
   * tapar el detalle no le quita nada a nadie salvo a quien estaba sondeando.
   */
  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ProblemDetail solicitudMalFormada(Exception excepcion) {
    log.warn("Solicitud mal formada: {}", excepcion.toString());
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "La solicitud no se pudo leer: revisa el cuerpo y los parámetros.");
    problema.setTitle("Solicitud inválida");
    String codigo = codigoDesde(excepcion);
    problema.setProperty("codigo", codigo);
    problema.setProperty("campos", List.of());
    problema.setType(
        URI.create(
            "https://tecnosport.co/errores/" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
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
    return codigoDesde(excepcion.getClass());
  }

  /**
   * El código de cable que se deriva de una clase de excepción.
   *
   * <p><b>Visible para pruebas, y hace falta que lo sea.</b> El frontend cablea estos códigos como
   * literales —{@code envio-http.repositorio.ts} decide con ellos si ofrece la recogida en el
   * punto, y los JSON del panel tienen una clave de traducción por código—, pero del lado del
   * servidor no los escribe nadie: salen del <b>nombre de la clase</b>. O sea que renombrar una
   * excepción cambia el contrato publicado sin tocar una sola cadena, y la suite entera se queda en
   * verde mientras el comprador empieza a ver un fallo genérico. {@code CodigosDeCableTest} los
   * fija uno a uno.
   */
  static String codigoDesde(Class<? extends Exception> clase) {
    String nombre = clase.getSimpleName().replace("Exception", "");
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
