package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.InventarioSinMedir;
import co.tecnosport.api.application.catalogo.ListarVariantesSinMedir;
import co.tecnosport.api.application.catalogo.MedirVariante;
import co.tecnosport.api.application.catalogo.MedirVarianteComando;
import co.tecnosport.api.application.catalogo.ResultadoDeMedicion;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.presentation.catalogo.dto.AgregarVariantePeticion;
import co.tecnosport.api.presentation.catalogo.dto.MedirVariantePeticion;
import co.tecnosport.api.presentation.catalogo.dto.ValorAtributoPeticion;
import co.tecnosport.api.presentation.catalogo.dto.VarianteMedidaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VarianteRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VariantesSinMedirRespuesta;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  private static final Logger log = LoggerFactory.getLogger(AdminVarianteControlador.class);

  private final AgregarVariante agregarVariante;
  private final ListarVariantesSinMedir listarVariantesSinMedir;
  private final MedirVariante medirVariante;
  private final MapeadorRespuestasCatalogo mapeador;
  private final MapeadorVariantesSinMedir mapeadorSinMedir;
  private final TransactionTemplate transaccion;

  public AdminVarianteControlador(
      AgregarVariante agregarVariante,
      ListarVariantesSinMedir listarVariantesSinMedir,
      MedirVariante medirVariante,
      MapeadorRespuestasCatalogo mapeador,
      MapeadorVariantesSinMedir mapeadorSinMedir,
      PlatformTransactionManager transactionManager) {
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
    this.listarVariantesSinMedir = Objects.requireNonNull(listarVariantesSinMedir);
    this.medirVariante = Objects.requireNonNull(medirVariante);
    this.mapeador = Objects.requireNonNull(mapeador);
    this.mapeadorSinMedir = Objects.requireNonNull(mapeadorSinMedir);
    this.transaccion = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  /**
   * El vigilante de lo que falta por medir. Sin paginar y sin filtros: lo que devuelve es una lista
   * de tareas pendientes que tiene que llegar a cero, no un listado del catálogo.
   */
  @GetMapping("/sin-medir")
  public VariantesSinMedirRespuesta sinMedir() {
    InventarioSinMedir inventario = listarVariantesSinMedir.ejecutar();
    return mapeadorSinMedir.aRespuesta(inventario);
  }

  /**
   * {@code PATCH} y no {@code PUT}: se toca el paquete de la variante, no la variante entera —el
   * precio, el SKU y los atributos no viajan aquí y no se pueden cambiar por esta puerta.
   *
   * <p>Transacción propia aunque la escritura sea una sola: {@code MedirVariante} lee el producto
   * entero para comprobar que la variante es suya y escribe después, y las dos cosas tienen que ver
   * la misma foto.
   */
  @PatchMapping("/{id}/paquete")
  public VarianteMedidaRespuesta medir(
      @PathVariable("id") UUID id, @RequestBody MedirVariantePeticion cuerpo) {
    MedirVarianteComando comando =
        new MedirVarianteComando(
            id, cuerpo.pesoGramos(), cuerpo.largoCm(), cuerpo.anchoCm(), cuerpo.altoCm());
    ResultadoDeMedicion resultado = transaccion.execute(estado -> medirVariante.ejecutar(comando));
    if (resultado.correccion()) {
      log.warn(
          "Variante {} ({}) remedida: el paquete anterior se reemplazó por {} g y {}x{}x{} cm. Los"
              + " pedidos ya cotizados llevan el flete calculado con las medidas viejas.",
          id,
          resultado.variante().sku().valor(),
          cuerpo.pesoGramos(),
          cuerpo.largoCm(),
          cuerpo.anchoCm(),
          cuerpo.altoCm());
    } else {
      log.info(
          "Variante {} ({}) medida por primera vez: {} g y {}x{}x{} cm. Ya se puede cotizar su"
              + " envío a domicilio.",
          id,
          resultado.variante().sku().valor(),
          cuerpo.pesoGramos(),
          cuerpo.largoCm(),
          cuerpo.anchoCm(),
          cuerpo.altoCm());
    }
    return mapeadorSinMedir.aRespuesta(resultado);
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
        cuerpo.pesoGramos(),
        cuerpo.largoCm(),
        cuerpo.anchoCm(),
        cuerpo.altoCm(),
        atributos);
  }

  private ValorAtributoComando aComando(ValorAtributoPeticion peticion) {
    return new ValorAtributoComando(peticion.atributoId(), peticion.valor(), peticion.colorHex());
  }
}
