package co.tecnosport.api.infrastructure.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.RepositorioProductosJpa;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

/** El borrador de tecnología y el vínculo de variante contra la V92, en PostgreSQL de verdad. */
@SpringBootTest
@Testcontainers
@Transactional
class RepositoriosDeTecnologiaJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant LUNES = Instant.parse("2026-10-05T05:00:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioBorradoresTecnologiaJpa borradores;
  @Autowired private RepositorioVariantesDeProveedorJpa vinculos;
  @Autowired private RepositorioProductosJpa productos;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @PersistenceContext private EntityManager em;

  private Proveedor proveedor() {
    Proveedor proveedor =
        Proveedor.crear(
            "Tecnología",
            LineaCatalogo.TECNOLOGIA,
            "+57 300",
            "Tecno",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);
    return proveedor;
  }

  private static ModeloDeLista a17() {
    return new ModeloDeLista(
        "samsung-galaxy-a17-5g",
        "Samsung Galaxy A17 5G",
        "Samsung",
        "celulares",
        "El Galaxy A17 5G.\n\n## Características principales\n\n- 5G.",
        "El Galaxy A17 5G.",
        List.of("Negro", "Gris", "Azul claro"));
  }

  private static ConfiguracionTecnologia config(String sku, String sim, Dinero mercado) {
    return ConfiguracionTecnologia.deLista(
        sku,
        "Samsung Galaxy A17 5G 8GB RAM 256GB " + sim,
        "8GB",
        "256GB",
        sim,
        Dinero.deCop(675_000),
        mercado,
        List.of("Negro", "Azul claro"));
  }

  @Test
  void elBorradorVaYVuelveConSusConfiguracionesEnOrdenYLoElegido() {
    Proveedor proveedor = proveedor();
    BorradorTecnologia borrador =
        BorradorTecnologia.nuevo(
            proveedor.id(),
            a17(),
            List.of(
                config("a17-dual-sim", "Dual SIM", null),
                config("a17-1-sim", "1 SIM", Dinero.deCop(849_900))),
            null,
            LUNES,
            LUNES);
    borradores.guardar(borrador);
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion(
                "a17-1-sim", List.of("Gris", "Azul claro"), Dinero.deCop(829_900))));
    borradores.actualizar(borrador);
    em.flush();
    em.clear();

    BorradorTecnologia leido = borradores.buscarPorId(borrador.id()).orElseThrow();

    assertEquals(a17(), leido.modelo());
    assertEquals(borrador.huella(), leido.huella());
    assertEquals(
        List.of("a17-dual-sim", "a17-1-sim"),
        leido.configuraciones().stream().map(ConfiguracionTecnologia::sku).toList());
    ConfiguracionTecnologia dual = leido.configuraciones().get(0);
    ConfiguracionTecnologia unaSim = leido.configuraciones().get(1);
    assertEquals(null, dual.precioMercado());
    assertEquals(List.of(), dual.coloresElegidos());
    assertEquals(List.of("Negro", "Azul claro"), unaSim.coloresSugeridos());
    assertEquals(List.of("Gris", "Azul claro"), unaSim.coloresElegidos());
    assertEquals(Dinero.deCop(829_900), unaSim.precioVenta());
    assertEquals(Dinero.deCop(849_900), unaSim.precioMercado());
    assertEquals(LUNES, leido.vistoEn());
  }

  @Test
  void enRevisionHayUnoPorModeloYLosResueltosSeBuscanAparte() {
    Proveedor proveedor = proveedor();
    BorradorTecnologia rechazado =
        BorradorTecnologia.nuevo(
            proveedor.id(), a17(), List.of(config("a17-1-sim", "1 SIM", null)), null, LUNES, LUNES);
    rechazado.rechazar("No vendemos esta gama");
    borradores.guardar(rechazado);
    BorradorTecnologia abierto =
        BorradorTecnologia.nuevo(
            proveedor.id(),
            a17(),
            List.of(config("a17-dual", "Dual SIM", null)),
            null,
            LUNES,
            LUNES);
    borradores.guardar(abierto);
    em.flush();

    assertEquals(
        abierto.id(),
        borradores.buscarEnRevision(proveedor.id(), "samsung-galaxy-a17-5g").orElseThrow().id());
    assertEquals(
        List.of(rechazado.id()),
        borradores.listarResueltos(proveedor.id(), "samsung-galaxy-a17-5g").stream()
            .map(BorradorTecnologia::id)
            .toList());
    assertEquals(1, borradores.listarPorEstado(EstadoBorrador.RECHAZADO).size());

    borradores.guardar(
        BorradorTecnologia.nuevo(
            proveedor.id(), a17(), List.of(config("a17-otra", "1 SIM", null)), null, LUNES, LUNES));
    assertThrows(Exception.class, () -> em.flush(), "dos en revisión del mismo modelo no entran");
  }

  @Test
  void elVinculoDeVarianteGuardaElCostoYSeBuscaPorConfiguracion() {
    Proveedor proveedor = proveedor();
    MarcaJpaEntity m =
        marcas.save(
            new MarcaJpaEntity(UUID.randomUUID(), "Marca " + UUID.randomUUID(), Instant.now()));
    CategoriaJpaEntity c =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(),
                "Celulares",
                "celulares-" + UUID.randomUUID(),
                "TECNOLOGIA",
                null,
                Instant.now()));
    Producto producto =
        Producto.crearDeProveedor(
            "Samsung Galaxy A17 5G",
            Slug.generarDesde("a17-" + UUID.randomUUID()),
            "El A17.",
            new Marca(m.getId(), m.getNombre()),
            new Categoria(
                c.getId(),
                c.getNombre(),
                new Slug(c.getSlug()),
                LineaCatalogo.TECNOLOGIA,
                null,
                List.of()),
            proveedor.id(),
            Dinero.deCop(675_000),
            HuellaProveedor.deModelo(proveedor.id(), "samsung-galaxy-a17-5g"),
            LUNES);
    productos.guardar(producto);
    String sku = config("a17-1-sim", "1 SIM", null).skuDeVariante("Negro");
    Variante variante =
        Variante.crear(new Sku(sku), Dinero.deCop(849_900), BigDecimal.ZERO, null, null, List.of());
    productos.agregarVariante(producto.id(), variante);
    vinculos.guardar(
        new VarianteDeProveedor(
            variante.id(),
            producto.id(),
            proveedor.id(),
            "a17-1-sim",
            "Negro",
            Dinero.deCop(675_000),
            LUNES));
    em.flush();

    VarianteDeProveedor actualizado =
        vinculos
            .deConfiguracion(proveedor.id(), "a17-1-sim")
            .getFirst()
            .conCosto(Dinero.deCop(660_000), LUNES.plusSeconds(86_400));
    vinculos.guardar(actualizado);
    em.flush();
    em.clear();

    List<VarianteDeProveedor> delProducto = vinculos.deProducto(producto.id());
    assertEquals(1, delProducto.size(), "guardar otra vez reemplaza, no duplica");
    assertEquals(Dinero.deCop(660_000), delProducto.getFirst().costo());
    assertEquals("Negro", delProducto.getFirst().color());
  }

  @Test
  void laMigracionSiembraLosEjesDeUnaVarianteDeTecnologia() {
    @SuppressWarnings("unchecked")
    List<String> nombres =
        em.createNativeQuery(
                "select nombre from atributo where lower(nombre) in"
                    + " ('color', 'ram', 'almacenamiento', 'sim')")
            .getResultList();
    assertTrue(
        nombres.containsAll(List.of("Color", "RAM", "Almacenamiento", "SIM")), nombres.toString());
  }
}
