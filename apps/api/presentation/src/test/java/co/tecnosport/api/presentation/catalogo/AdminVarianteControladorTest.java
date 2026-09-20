package co.tecnosport.api.presentation.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(AdminVarianteControlador.class)
@Import(AdminVarianteControladorTest.Configuracion.class)
class AdminVarianteControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorioProductos;
  @Autowired private RepositorioAtributosDobleDePrueba repositorioAtributos;

  private static Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit", new Slug("camiseta-running-dry-fit"), "", marca, categoria);
  }

  @Test
  void crearDevuelve201ConLaVarianteYSusAtributos() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
    repositorioAtributos.conAtributos(color);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-CAM-AZ-M","precio":89900,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":5,"pesoGramos":180,"largoCm":30,"anchoCm":25,"altoCm":4,
                     "atributos":[{"atributoId":"%s","valor":"Azul marino","colorHex":"#1E3A8A"}]}
                    """
                        .formatted(producto.id(), color.id())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("TS-CAM-AZ-M"))
        .andExpect(jsonPath("$.existencia").value(5))
        .andExpect(jsonPath("$.atributos[0].valor").value("Azul marino"));
  }

  @Test
  void crearConProductoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-1","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,"pesoGramos":180,"largoCm":30,"anchoCm":25,"altoCm":4,"atributos":[]}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void crearConSkuYaEnUsoDevuelve409() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    repositorioProductos.conSkusEnUso("TS-YA-EXISTE");

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-YA-EXISTE","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,"pesoGramos":180,"largoCm":30,"anchoCm":25,"altoCm":4,"atributos":[]}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isConflict());
  }

  @Test
  void crearConTasaDeIvaDistintaDeCeroDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-1","precio":1000,"tasaIva":0.19,
                     "codigoBarras":null,"existenciaInicial":0,"pesoGramos":180,"largoCm":30,"anchoCm":25,"altoCm":4,"atributos":[]}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TASA_IVA_NO_PERMITIDA"));
  }

  @Test
  void crearConAtributoInexistenteDevuelve404() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-1","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,"pesoGramos":180,"largoCm":30,"anchoCm":25,"altoCm":4,
                     "atributos":[{"atributoId":"%s","valor":"Azul","colorHex":null}]}
                    """
                        .formatted(producto.id(), UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  /**
   * Hasta el 19 de septiembre de 2026 esto devolvía 422, y el javadoc explicaba por qué estaba
   * bien: Jackson 3 no rellena los componentes que falten de un record, así que un cuerpo sin
   * paquete moría antes de llegar al dominio. Con {@code adr/0046} la variante sin medir es un
   * estado legítimo — se vende, pero solo con recogida — y el cuerpo sin esos campos se acepta.
   *
   * <p>El SKU va distinto del de las otras pruebas a propósito: el doble es un singleton que Spring
   * comparte entre los métodos de esta clase, y desde que este cuerpo SÍ crea la variante, repetir
   * "TS-1" hacía fallar a otra prueba con un 409 que no tenía nada que ver con ella.
   */
  @Test
  void crearSinLosCamposDelPaqueteCreaUnaVarianteSinMedir() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-SIN-MEDIR","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,"atributos":[]}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isCreated());
  }

  /** Tres medidas y un peso ausente no es "a medio medir": es una carga rota. */
  @Test
  void crearConElPaqueteAMediasDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-A-MEDIAS","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,"largoCm":30,"anchoCm":25,"altoCm":4,
                     "atributos":[]}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void crearConUnaDimensionEnCeroDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/variantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productoId":"%s","sku":"TS-1","precio":1000,"tasaIva":0,
                     "codigoBarras":null,"existenciaInicial":0,
                     "pesoGramos":180,"largoCm":0,"anchoCm":25,"altoCm":4,"atributos":[]}
                    """
                        .formatted(producto.id())))
        .andExpect(status().isUnprocessableContent());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    RepositorioAtributosDobleDePrueba repositorioAtributos() {
      return new RepositorioAtributosDobleDePrueba();
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    Reloj reloj() {
      return Instant::now;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    AgregarVariante agregarVariante(
        RepositorioProductos repositorioProductos,
        RepositorioAtributos repositorioAtributos,
        RepositorioInventario repositorioInventario,
        Reloj reloj) {
      // false, como en produccion: el negocio no es responsable de IVA (adr/0041).
      return new AgregarVariante(
          repositorioProductos, repositorioAtributos, repositorioInventario, reloj, false);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }
  }
}
