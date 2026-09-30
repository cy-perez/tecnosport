package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.BorradoresPaginados;
import co.tecnosport.api.application.proveedores.HuellaVisual;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioBorradoresJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private RepositorioMensajesProveedorJpa mensajes;
  @Autowired private RepositorioPublicacionesProveedorJpa publicaciones;
  @Autowired private RepositorioBorradoresJpa borradores;

  private Proveedor proveedor;
  private PublicacionProveedor publicacion;

  private void unaPublicacion() {
    proveedor = Proveedor.crear("Bolsos", LineaCatalogo.BOLSOS, "+57 300", "Bolsos Centro", null);
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    mensajes.guardarTodos(List.of(principal));
    publicacion = PublicacionProveedor.abrir(principal);
    publicaciones.guardarTodas(List.of(publicacion));
  }

  private static ProductoExtraido extraido(String titulo, List<String> caracteristicas) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.CONJUNTO_PANTALON,
        Dinero.deCop(60000),
        Tallas.lista(List.of("M", "L", "XL")),
        3,
        List.of("negro", "vino"),
        "burda strech",
        caracteristicas,
        new BigDecimal("0.88"),
        "una nota");
  }

  @Test
  void elBorradorLlenoVaYVuelveEntero() {
    unaPublicacion();
    PHash pHash = PHash.deHex("a1b2c3d4e5f60718");
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Conjunto pantalón", List.of("tela burda strech", "camiseta oversize")),
            "{\"es_producto\":true}",
            Dinero.deCop(60000),
            Dinero.deCop(78000),
            HuellaProveedor.calcular(proveedor.id(), "Conjunto pantalón", Dinero.deCop(60000)),
            pHash,
            Set.of(AlertaBorrador.CONFIANZA_BAJA, AlertaBorrador.SIN_FOTOS),
            T);
    borradores.guardar(borrador);

    BorradorProducto leido = borradores.buscarPorId(borrador.id()).orElseThrow();

    assertThat(leido.titulo()).contains("Conjunto pantalón");
    assertThat(leido.linea()).contains(LineaCatalogo.ROPA);
    assertThat(leido.tipo()).isEqualTo(TipoProductoProveedor.CONJUNTO_PANTALON);
    assertThat(leido.precioProveedor()).contains(Dinero.deCop(60000));
    assertThat(leido.precioVentaSugerido()).contains(Dinero.deCop(78000));
    assertThat(leido.tallas().tipo()).isEqualTo(TipoDeTalla.LISTA);
    assertThat(leido.tallas().valores()).containsExactly("M", "L", "XL");
    assertThat(leido.cantidadTonos()).contains(3);
    assertThat(leido.tonosNombrados()).containsExactly("negro", "vino");
    assertThat(leido.material()).contains("burda strech");
    assertThat(leido.caracteristicas()).containsExactly("tela burda strech", "camiseta oversize");
    assertThat(leido.huella()).isEqualTo(borrador.huella());
    assertThat(leido.pHash()).contains(pHash);
    assertThat(leido.alertas())
        .containsExactlyInAnyOrder(AlertaBorrador.CONFIANZA_BAJA, AlertaBorrador.SIN_FOTOS);
    assertThat(leido.estado()).isEqualTo(EstadoBorrador.EN_REVISION);
    assertThat(leido.extraccionCruda()).isEqualTo("{\"es_producto\":true}");
    assertThat(leido.creadoEn()).isEqualTo(T);
  }

  @Test
  void elBorradorVacioVaYVuelveVacio() {
    unaPublicacion();
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null),
            "{}",
            null,
            null,
            null,
            null,
            Set.of(),
            T);
    borradores.guardar(borrador);

    BorradorProducto leido = borradores.buscarPorId(borrador.id()).orElseThrow();

    assertThat(leido.titulo()).isEmpty();
    assertThat(leido.linea()).isEmpty();
    assertThat(leido.tipo()).isEqualTo(TipoProductoProveedor.OTRO);
    assertThat(leido.precioProveedor()).isEmpty();
    assertThat(leido.tallas().tipo()).isEqualTo(TipoDeTalla.DESCONOCIDA);
    assertThat(leido.tallas().valores()).isEmpty();
    assertThat(leido.tonosNombrados()).isEmpty();
    assertThat(leido.caracteristicas()).isEmpty();
    assertThat(leido.huella()).isEmpty();
    assertThat(leido.pHash()).isEmpty();
    assertThat(leido.alertas()).isEmpty();
  }

  @Test
  void seListaPorEstadoYProveedorYLasHuellasVisualesSolo_DeLoQueYaEsProducto() {
    unaPublicacion();
    BorradorProducto enRevision =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Uno", List.of()),
            "{}",
            Dinero.deCop(1000),
            null,
            null,
            PHash.deHex("0000000000000001"),
            Set.of(),
            T);
    BorradorProducto rechazado =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            extraido("Dos", List.of()),
            "{}",
            Dinero.deCop(1000),
            null,
            null,
            PHash.deHex("0000000000000002"),
            Set.of(),
            T.plusSeconds(1));
    rechazado.rechazar("No.");
    borradores.guardar(enRevision);
    borradores.guardar(rechazado);

    BorradoresPaginados pendientes =
        borradores.listar(EstadoBorrador.EN_REVISION, proveedor.id(), 0, 10);
    assertThat(pendientes.totalBorradores()).isEqualTo(1);
    assertThat(pendientes.items().get(0).id()).isEqualTo(enRevision.id());
    assertThat(borradores.listar(null, null, 0, 10).totalBorradores()).isEqualTo(2);
    assertThat(borradores.listar(null, UUID.randomUUID(), 0, 10).totalBorradores()).isZero();
    assertThat(borradores.listar(null, null, 0, 10).items().get(0).id())
        .as("el más reciente primero")
        .isEqualTo(rechazado.id());

    // Sin producto todavía, ninguna huella visual cuenta.
    assertThat(borradores.huellasVisualesDelProveedor(proveedor.id())).isEmpty();

    BorradorProducto leido = borradores.buscarPorId(rechazado.id()).orElseThrow();
    assertThat(leido.estado()).isEqualTo(EstadoBorrador.RECHAZADO);
    assertThat(leido.motivoRechazo()).contains("No.");
    List<HuellaVisual> ninguna = borradores.huellasVisualesDelProveedor(UUID.randomUUID());
    assertThat(ninguna).isEmpty();
  }
}
