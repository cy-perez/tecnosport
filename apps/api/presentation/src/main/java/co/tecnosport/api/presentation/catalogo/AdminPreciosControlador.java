package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.PrecioPorRecalcular;
import co.tecnosport.api.application.catalogo.PrecioRevisado;
import co.tecnosport.api.application.catalogo.RecalcularPreciosPublicados;
import co.tecnosport.api.application.catalogo.RecalculoAplicado;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.catalogo.dto.AplicarRecalculoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.PrecioPorRecalcularRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.PrecioRevisadoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.PreciosPorRecalcularRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.RecalculoAplicadoRespuesta;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El recálculo de los precios publicados cuando cambian las tarifas de la pasarela (ADR-0095). Un
 * {@code GET} que enseña lo que cambiaría y un {@code POST} que lo aplica: un precio anunciado no
 * cambia sin que una persona lo vea.
 *
 * <p>El {@code POST} recibe <b>la lista que vio</b> quien lo confirma y graba solo las filas que
 * siguen como se vieron: si entre los dos alguien cambió un precio desde la ficha, o cambió la
 * tarifa, esa fila se omite y se cuenta, y no se pisa. Con transacción propia, para que un fallo a
 * mitad no deje el catálogo a medias.
 */
@RestController
@RequestMapping("/api/v1/admin/precios/recalculo")
public class AdminPreciosControlador {

  private static final Logger log = LoggerFactory.getLogger(AdminPreciosControlador.class);

  private final RecalcularPreciosPublicados recalcular;
  private final TransactionTemplate transaccion;

  public AdminPreciosControlador(
      RecalcularPreciosPublicados recalcular, PlatformTransactionManager transactionManager) {
    this.recalcular = Objects.requireNonNull(recalcular);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @GetMapping
  public PreciosPorRecalcularRespuesta previsualizar() {
    return new PreciosPorRecalcularRespuesta(
        recalcular.previsualizar().stream().map(AdminPreciosControlador::aRespuesta).toList());
  }

  /** Cada cambio queda en el registro: es lo único que permite saber qué se anunciaba antes. */
  @PostMapping
  public RecalculoAplicadoRespuesta aplicar(@RequestBody AplicarRecalculoPeticion cuerpo) {
    List<PrecioRevisado> revisadas =
        cuerpo.variantes().stream().map(AdminPreciosControlador::aRevisado).toList();
    RecalculoAplicado resultado = transaccion.execute(estado -> recalcular.aplicar(revisadas));
    for (PrecioRevisado aplicado : resultado.recalculadas()) {
      log.info(
          "Precio publicado de la variante {} recalculado de {} a {}; de contado sigue en {}.",
          aplicado.varianteId(),
          aplicado.publicadoActual().valor().toPlainString(),
          aplicado.publicadoNuevo().valor().toPlainString(),
          aplicado.contado().valor().toPlainString());
    }
    if (!resultado.omitidas().isEmpty()) {
      log.info(
          "Recálculo de precios publicados: {} variante(s) omitida(s) porque cambiaron entre la"
              + " revisión y la aplicación.",
          resultado.omitidas().size());
    }
    return new RecalculoAplicadoRespuesta(
        resultado.recalculadas().size(), resultado.omitidas().size());
  }

  private static PrecioRevisado aRevisado(PrecioRevisadoPeticion fila) {
    return new PrecioRevisado(
        fila.varianteId(),
        Dinero.deCop(fila.precioContado()),
        Dinero.deCop(fila.publicadoActual()),
        Dinero.deCop(fila.publicadoNuevo()));
  }

  private static PrecioPorRecalcularRespuesta aRespuesta(PrecioPorRecalcular precio) {
    return new PrecioPorRecalcularRespuesta(
        precio.varianteId(),
        precio.sku(),
        precio.nombreProducto(),
        precio.contado().valor().longValueExact(),
        precio.publicadoActual().valor().longValueExact(),
        precio.publicadoNuevo().valor().longValueExact());
  }
}
