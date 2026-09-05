package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.presentation.catalogo.dto.AgregarVariantePeticion;
import co.tecnosport.api.presentation.catalogo.dto.ValorAtributoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.VarianteRespuesta;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bajo {@code /api/v1/admin/**}, protegido por rol ADMIN en {@code ConfiguracionSeguridad}
 * (bootstrap) — mismo criterio que {@code AdminProductoControlador}. {@code
 * AgregarVariante.ejecutar} toca dos agregados (Producto/Variante e Inventario) en la misma
 * llamada, así que este controlador abre la transacción — ver el javadoc de {@code
 * RepositorioInventario}.
 */
@RestController
@RequestMapping("/api/v1/admin/variantes")
public class AdminVarianteControlador {

  private final AgregarVariante agregarVariante;
  private final MapeadorRespuestasCatalogo mapeador;
  private final TransactionTemplate transaccion;

  public AdminVarianteControlador(
      AgregarVariante agregarVariante,
      MapeadorRespuestasCatalogo mapeador,
      PlatformTransactionManager transactionManager) {
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VarianteRespuesta crear(@RequestBody AgregarVariantePeticion cuerpo) {
    AgregarVarianteComando comando = aComando(cuerpo);
    Variante variante = transaccion.execute(estado -> agregarVariante.ejecutar(comando));
    return mapeador.aRespuesta(variante);
  }

  private AgregarVarianteComando aComando(AgregarVariantePeticion cuerpo) {
    List<ValorAtributoComando> atributos =
        cuerpo.atributos() == null
            ? List.of()
            : cuerpo.atributos().stream().map(this::aComando).toList();
    return new AgregarVarianteComando(
        cuerpo.productoId(),
        cuerpo.sku(),
        cuerpo.precio(),
        cuerpo.tasaIva(),
        cuerpo.codigoBarras(),
        cuerpo.existenciaInicial(),
        atributos);
  }

  private ValorAtributoComando aComando(ValorAtributoPeticion peticion) {
    return new ValorAtributoComando(peticion.atributoId(), peticion.valor(), peticion.colorHex());
  }
}
