package co.tecnosport.api.infrastructure.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.RepositorioProductosJpa;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import co.tecnosport.api.infrastructure.proveedores.RepositorioProductosDeProveedorJpa;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El enlace de compra, la paleta del producto y el color por confirmar de la variante contra
 * Postgres real (V117, ADR-0086).
 */
@SpringBootTest
@Testcontainers
class RepositorioEnlacesDeCompraJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioEnlacesDeCompraJpa enlaces;
  @Autowired private RepositorioProductosJpa productosJpa;
  @Autowired private RepositorioProductosDeProveedorJpa productosDeProveedor;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private ProductoJpaRepository productos;
  @Autowired private VarianteJpaRepository variantes;
  @Autowired private PlatformTransactionManager transactionManager;

  private record Sembrado(UUID productoId, UUID varianteId) {}

  private Sembrado variantePropia(String sku) {
    Instant ahora = Instant.now();
    MarcaJpaEntity marca =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Marca de prueba " + sku, ahora));
    String slug = sku.toLowerCase(Locale.ROOT);
    CategoriaJpaEntity categoria =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(), "Categoría de prueba", slug, "TECNOLOGIA", null, ahora));
    ProductoJpaEntity producto =
        productos.save(
            new ProductoJpaEntity(
                UUID.randomUUID(),
                "Producto de prueba",
                slug,
                "",
                marca.getId(),
                categoria.getId(),
                "PUBLICADO",
                ahora,
                ahora));
    VarianteJpaEntity variante =
        variantes.save(
            new VarianteJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                sku,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                null,
                180,
                30,
                25,
                4,
                "ACTIVA",
                ahora));
    return new Sembrado(producto.getId(), variante.getId());
  }

  @Test
  void elEnlaceSeGuardaSeEncuentraPorSuTokenYSeUsa() {
    Sembrado sembrado = variantePropia("ENLACE-1");
    Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    EnlaceDeCompra enlace =
        EnlaceDeCompra.generar(
            sembrado.varianteId(), UUID.randomUUID(), Duration.ofHours(24), ahora);
    enlaces.guardar(enlace);

    EnlaceDeCompra leido = enlaces.buscarPorToken(enlace.token()).orElseThrow();
    assertThat(leido.varianteId()).isEqualTo(sembrado.varianteId());
    assertThat(leido.expiraEn()).isEqualTo(ahora.plus(Duration.ofHours(24)));
    assertThat(enlaces.listarCreadosDesde(ahora.minusSeconds(1))).contains(leido);

    UUID pedido = UUID.randomUUID();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            estado -> {
              EnlaceDeCompra bloqueado =
                  enlaces.buscarParaUsar(enlace.token(), sembrado.varianteId()).orElseThrow();
              bloqueado.usar(pedido, ahora.plusSeconds(60));
              enlaces.actualizar(bloqueado);
            });

    assertThat(enlaces.buscarPorId(enlace.id()).orElseThrow().pedidoId()).contains(pedido);
    assertThat(enlaces.buscarPorToken("otro")).isEmpty();
  }

  @Test
  void laPaletaYElDisponibilidadPorConfirmarSobrevivenAGuardarElProductoYCambiarElPrecio() {
    Sembrado sembrado = variantePropia("PALETA-1");
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            estado -> {
              Producto producto = productosJpa.buscarPorId(sembrado.productoId()).orElseThrow();
              producto.definirPaleta(List.of("Morado", "Plata", "Negro"));
              productosJpa.actualizar(producto);
              productosDeProveedor.marcarDisponibilidadesPorConfirmar(
                  Map.of(sembrado.varianteId(), true));
            });
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            estado -> productosJpa.actualizarPrecio(sembrado.varianteId(), Dinero.deCop(12_000)));

    Producto leido = productosJpa.buscarPorId(sembrado.productoId()).orElseThrow();
    assertThat(leido.paleta()).containsExactly("Morado", "Plata", "Negro");
    assertThat(leido.variantes().getFirst().disponibilidadPorConfirmar()).isTrue();
  }
}
