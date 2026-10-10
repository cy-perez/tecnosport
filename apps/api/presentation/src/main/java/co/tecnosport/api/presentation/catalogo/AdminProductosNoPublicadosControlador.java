package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.EliminarProductosNoPublicados;
import co.tecnosport.api.application.catalogo.ProductosEliminados;
import co.tecnosport.api.presentation.catalogo.dto.ProductosEliminadosRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductosNoPublicadosRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El borrado en bloque de los productos no publicados. Aparte de {@code AdminProductoControlador}
 * porque es una sola pantalla con su propio ritmo —contar, confirmar, pedir tandas—, y aquel ya
 * reparte veinte casos de uso. La ruta literal gana a {@code /{id}} del vecino.
 */
@RestController
@RequestMapping("/api/v1/admin/productos/no-publicados")
public class AdminProductosNoPublicadosControlador {

  private static final Logger log =
      LoggerFactory.getLogger(AdminProductosNoPublicadosControlador.class);

  /**
   * Cuántos productos borra una petición. Pocos a propósito: el bucket se borra objeto por objeto,
   * y un producto con visor 360 y tres resoluciones por foto pasa de cien objetos.
   */
  static final int TANDA_DE_BORRADO = 10;

  private final EliminarProductosNoPublicados eliminarNoPublicados;
  private final TransactionTemplate transaccion;

  public AdminProductosNoPublicadosControlador(
      EliminarProductosNoPublicados eliminarNoPublicados,
      PlatformTransactionManager transactionManager) {
    this.eliminarNoPublicados = Objects.requireNonNull(eliminarNoPublicados);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public ProductosNoPublicadosRespuesta contarNoPublicados() {
    return new ProductosNoPublicadosRespuesta(eliminarNoPublicados.contar());
  }

  /**
   * Una tanda. {@code warn} con lo borrado, como el borrado de uno: es la única huella que queda.
   */
  @DeleteMapping
  public ProductosEliminadosRespuesta eliminarNoPublicados(
      @RequestParam(required = false) UUID desde, @RequestParam(required = false) UUID hasta) {
    ProductosEliminados tanda =
        transaccion.execute(
            estado -> eliminarNoPublicados.ejecutar(desde, hasta, TANDA_DE_BORRADO));
    log.warn(
        "Productos no publicados eliminados: {} ({} conservados por ventas, {} por existencias,"
            + " {} objetos del bucket)",
        tanda.eliminados(),
        tanda.conservadosPorVentas(),
        tanda.conservadosPorExistencias(),
        tanda.objetosBorrados());
    return ProductosEliminadosRespuesta.de(tanda);
  }
}
