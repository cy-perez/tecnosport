package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.AnularEnlaceDeCompra;
import co.tecnosport.api.application.pedido.GenerarEnlaceDeCompra;
import co.tecnosport.api.application.pedido.ListarEnlacesDeCompra;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.presentation.pedido.dto.EnlaceDeCompraRespuesta;
import co.tecnosport.api.presentation.pedido.dto.GenerarEnlaceDeCompraRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los enlaces de compra de colores de tecnología confirmados con el proveedor (ADR-0086): se
 * generan cuando el proveedor dice que tiene el color, se mandan por WhatsApp y se anulan si quien
 * compra desiste.
 */
@RestController
@RequestMapping("/api/v1/admin/enlaces-de-compra")
public class AdminEnlacesDeCompraControlador {

  private final GenerarEnlaceDeCompra generar;
  private final AnularEnlaceDeCompra anular;
  private final ListarEnlacesDeCompra listar;
  private final TransactionTemplate transaccion;

  public AdminEnlacesDeCompraControlador(
      GenerarEnlaceDeCompra generar,
      AnularEnlaceDeCompra anular,
      ListarEnlacesDeCompra listar,
      PlatformTransactionManager transactionManager) {
    this.generar = Objects.requireNonNull(generar);
    this.anular = Objects.requireNonNull(anular);
    this.listar = Objects.requireNonNull(listar);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EnlaceDeCompraRespuesta generar(@RequestBody GenerarEnlaceDeCompraRequest cuerpo) {
    GenerarEnlaceDeCompra.Resultado resultado =
        transaccion.execute(estado -> generar.ejecutar(cuerpo.varianteId()));
    return aRespuesta(
        resultado.enlace(),
        resultado.producto(),
        resultado.variante(),
        ListarEnlacesDeCompra.Estado.VIGENTE);
  }

  @GetMapping
  public List<EnlaceDeCompraRespuesta> listar() {
    return listar.ejecutar().stream()
        .map(f -> aRespuesta(f.enlace(), f.producto(), f.variante(), f.estado()))
        .toList();
  }

  @PostMapping("/{id}/anular")
  public EnlaceDeCompraRespuesta anular(@PathVariable UUID id) {
    EnlaceDeCompra enlace = transaccion.execute(estado -> anular.ejecutar(id));
    ListarEnlacesDeCompra.Fila fila = listar.fila(enlace);
    return aRespuesta(fila.enlace(), fila.producto(), fila.variante(), fila.estado());
  }

  private static EnlaceDeCompraRespuesta aRespuesta(
      EnlaceDeCompra enlace, Producto producto, Variante variante, ListarEnlacesDeCompra.Estado e) {
    return new EnlaceDeCompraRespuesta(
        enlace.id(),
        enlace.token(),
        enlace.varianteId(),
        producto == null ? null : producto.nombre(),
        producto == null ? null : producto.slug().valor(),
        variante == null ? null : detalleDe(variante),
        e.name(),
        enlace.creadoEn(),
        enlace.expiraEn(),
        enlace.pedidoId().orElse(null));
  }

  /** «Morado · 256 GB», con el mismo orden que el pedido: por el nombre del atributo. */
  static String detalleDe(Variante variante) {
    return variante.atributos().stream()
        .sorted(Comparator.comparing(valor -> valor.atributo().nombre()))
        .map(
            valor ->
                valor.atributo().unidad().map(u -> valor.valor() + " " + u).orElse(valor.valor()))
        .collect(Collectors.joining(" · "));
  }
}
