package co.tecnosport.api.presentation.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.ListarMedidasDeVariantes;
import co.tecnosport.api.application.catalogo.ListarVariantesSinMedir;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.MedirVariante;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.AjustarExistencia;
import co.tecnosport.api.application.inventario.ListarExistencias;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import java.math.BigDecimal;
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
  @Autowired private RepositorioInventarioDobleDePrueba repositorioInventario;

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
        .andExpect(jsonPath("$.disponible").value(true))
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

  /**
   * El endpoint de la pantalla de corrección. Lo que hay que demostrar es lo contrario que en
   * {@code /sin-medir}: que la ya medida <b>sí</b> sale, y con sus cuatro cifras.
   */
  @Test
  void medidasDevuelveTambienLasYaMedidasConSuPaquete() throws Exception {
    repositorioProductos.conVariantesSinMedir(
        new MedidaDeVariante(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "JBL Go 5",
            "JBL-GO-5",
            EstadoProducto.PUBLICADO,
            new Paquete(320, 14, 10, 6)),
        new MedidaDeVariante(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Moto G17",
            "TS-MOTO-1",
            EstadoProducto.PUBLICADO,
            null));

    mockMvc
        .perform(get("/api/v1/admin/variantes/medidas"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.totalSinMedir").value(1))
        .andExpect(jsonPath("$.totalSinMedirEnPublicados").value(1))
        // Las sin medir van primero: son las que no se pueden enviar a domicilio.
        .andExpect(jsonPath("$.items[0].sku").value("TS-MOTO-1"))
        .andExpect(jsonPath("$.items[0].sinMedir").value(true))
        .andExpect(jsonPath("$.items[0].pesoGramos").doesNotExist())
        .andExpect(jsonPath("$.items[1].sku").value("JBL-GO-5"))
        .andExpect(jsonPath("$.items[1].sinMedir").value(false))
        .andExpect(jsonPath("$.items[1].pesoGramos").value(320))
        .andExpect(jsonPath("$.items[1].largoCm").value(14))
        .andExpect(jsonPath("$.items[1].anchoCm").value(10))
        .andExpect(jsonPath("$.items[1].altoCm").value(6));
  }

  @Test
  void sinMedirDevuelveLaListaYLosDosConteos() throws Exception {
    repositorioProductos.conVariantesSinMedir(
        new MedidaDeVariante(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Moto G17",
            "TS-MOTO-1",
            EstadoProducto.PUBLICADO,
            null),
        new MedidaDeVariante(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Honor X9d",
            "TS-HONOR-1",
            EstadoProducto.BORRADOR,
            null));

    mockMvc
        .perform(get("/api/v1/admin/variantes/sin-medir"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.totalEnPublicados").value(1))
        .andExpect(jsonPath("$.items[0].nombreProducto").value("Moto G17"))
        .andExpect(jsonPath("$.items[0].sku").value("TS-MOTO-1"))
        .andExpect(jsonPath("$.items[0].estadoProducto").value("PUBLICADO"));
  }

  /** El caso al que hay que llegar: el panel usa estos ceros para no enseñar el aviso. */
  @Test
  void sinNadaQueMedirDevuelveCeroYUnaListaVacia() throws Exception {
    // Explícito y no heredado del estado inicial: el doble es un bean del contexto, así que vive
    // entre pruebas, y una que dependa de que ninguna anterior lo haya tocado falla el día que
    // alguien agrega un método más arriba. Pasó al escribir la prueba del endpoint de medidas.
    repositorioProductos.conVariantesSinMedir();

    mockMvc
        .perform(get("/api/v1/admin/variantes/sin-medir"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.totalEnPublicados").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void medirDevuelveLasMedidasGrabadasYDeclaraQueNoEsUnaCorreccion() throws Exception {
    Variante sinMedir = variante("TS-MEDIR-1", null);
    Producto producto = productoDePrueba();
    producto.agregarVariante(sinMedir);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/paquete", sinMedir.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pesoGramos":430,"largoCm":17,"anchoCm":9,"altoCm":5}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("TS-MEDIR-1"))
        .andExpect(jsonPath("$.pesoGramos").value(430))
        .andExpect(jsonPath("$.largoCm").value(17))
        .andExpect(jsonPath("$.correccion").value(false));
  }

  @Test
  void remedirUnaVarianteQueYaTeniaPaqueteLoDeclaraComoCorreccion() throws Exception {
    Variante medida = variante("TS-REMEDIR-1", new Paquete(300, 12, 8, 5));
    Producto producto = productoDePrueba();
    producto.agregarVariante(medida);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/paquete", medida.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pesoGramos":420,"largoCm":14,"anchoCm":9,"altoCm":6}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correccion").value(true));
  }

  @Test
  void medirUnaVarianteQueNoExisteDevuelve404() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/paquete", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pesoGramos":430,"largoCm":17,"anchoCm":9,"altoCm":5}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void medirConUnaDimensionEnCeroDevuelve422() throws Exception {
    Variante sinMedir = variante("TS-MEDIR-CERO", null);
    Producto producto = productoDePrueba();
    producto.agregarVariante(sinMedir);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/paquete", sinMedir.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pesoGramos":430,"largoCm":0,"anchoCm":9,"altoCm":5}
                    """))
        .andExpect(status().isUnprocessableContent());
  }

  /**
   * Un cuerpo al que le falta una medida no se acepta, y aquí sí lo atrapa Jackson: los cuatro
   * componentes de {@code MedirVariantePeticion} son primitivos a propósito. Es lo contrario que en
   * el alta, donde faltar es un estado legítimo.
   */
  @Test
  void medirSinTodasLasMedidasDevuelve422() throws Exception {
    Variante sinMedir = variante("TS-MEDIR-INCOMPLETO", null);
    Producto producto = productoDePrueba();
    producto.agregarVariante(sinMedir);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/paquete", sinMedir.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pesoGramos":430,"anchoCm":9,"altoCm":5}
                    """))
        .andExpect(status().isUnprocessableContent());
  }

  private static Variante variante(String sku, Paquete paquete) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(890_000), new BigDecimal("0.00"), null, paquete, List.of());
  }

  @Test
  void existenciasDevuelveLasCifrasDelLibroYLosConteos() throws Exception {
    UUID varianteId = UUID.randomUUID();
    repositorioProductos.conVariantesActivas(
        new VarianteActiva(
            varianteId, UUID.randomUUID(), "Moto G17", "TS-MOTO-G17", EstadoProducto.PUBLICADO));
    Inventario libro = Inventario.crear(varianteId);
    libro.registrarEntrada(2, "siembra de prueba", Instant.now());
    repositorioInventario.con(libro);

    mockMvc
        .perform(get("/api/v1/admin/variantes/existencias"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.totalSinExistencia").value(0))
        .andExpect(jsonPath("$.totalSinExistenciaEnPublicados").value(0))
        .andExpect(jsonPath("$.items[0].sku").value("TS-MOTO-G17"))
        .andExpect(jsonPath("$.items[0].saldoTotal").value(2))
        .andExpect(jsonPath("$.items[0].disponible").value(2))
        .andExpect(jsonPath("$.items[0].reservadas").value(0));
  }

  @Test
  void ajustarExistenciaDevuelveLoQueCambio() throws Exception {
    Producto producto = productoDePrueba();
    Variante variante = varianteDePrueba();
    producto.agregarVariante(variante);
    repositorioProductos.conProductos(producto);
    Inventario libro = Inventario.crear(variante.id());
    libro.registrarEntrada(5, "siembra de prueba", Instant.now());
    repositorioInventario.con(libro);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/existencia", variante.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cantidadContada":8,"motivo":"Conteo físico del 20 de septiembre"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoAnterior").value(5))
        .andExpect(jsonPath("$.saldoNuevo").value(8))
        .andExpect(jsonPath("$.diferencia").value(3))
        .andExpect(jsonPath("$.sinCambios").value(false))
        .andExpect(jsonPath("$.dejaReservasSinRespaldo").value(false));
  }

  /** Contar lo mismo responde 200 y lo declara: es un resultado, no un error. */
  @Test
  void ajustarConElMismoConteoDevuelveSinCambios() throws Exception {
    Producto producto = productoDePrueba();
    Variante variante = varianteDePrueba();
    producto.agregarVariante(variante);
    repositorioProductos.conProductos(producto);
    Inventario libro = Inventario.crear(variante.id());
    libro.registrarEntrada(5, "siembra de prueba", Instant.now());
    repositorioInventario.con(libro);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/existencia", variante.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cantidadContada":5,"motivo":"Conteo físico, sin novedad"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sinCambios").value(true))
        .andExpect(jsonPath("$.diferencia").value(0));
  }

  @Test
  void ajustarLaExistenciaDeUnaVarianteQueNoExisteDevuelve404() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/existencia", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cantidadContada":3,"motivo":"Conteo"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("VARIANTE_NO_ENCONTRADA_POR_ID"));
  }

  @Test
  void ajustarSinMotivoDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    Variante variante = varianteDePrueba();
    producto.agregarVariante(variante);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/existencia", variante.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cantidadContada":3,"motivo":"   "}
                    """))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void ajustarConUnConteoNegativoDevuelve422() throws Exception {
    Producto producto = productoDePrueba();
    Variante variante = varianteDePrueba();
    producto.agregarVariante(variante);
    repositorioProductos.conProductos(producto);

    mockMvc
        .perform(
            patch("/api/v1/admin/variantes/{id}/existencia", variante.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"cantidadContada":-2,"motivo":"Conteo"}
                    """))
        .andExpect(status().isUnprocessableContent());
  }

  private static Variante varianteDePrueba() {
    return Variante.crear(
        new Sku("TS-EXISTENCIA-CTRL"),
        Dinero.deCop(89_900),
        new BigDecimal("0.00"),
        null,
        new Paquete(180, 30, 25, 4),
        List.of());
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
    ListarVariantesSinMedir listarVariantesSinMedir(RepositorioProductos repositorioProductos) {
      return new ListarVariantesSinMedir(repositorioProductos);
    }

    @Bean
    ListarMedidasDeVariantes listarMedidasDeVariantes(RepositorioProductos repositorioProductos) {
      return new ListarMedidasDeVariantes(repositorioProductos);
    }

    @Bean
    MedirVariante medirVariante(RepositorioProductos repositorioProductos) {
      return new MedirVariante(repositorioProductos);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }

    @Bean
    MapeadorVariantesSinMedir mapeadorVariantesSinMedir() {
      return new MapeadorVariantesSinMedir();
    }

    @Bean
    ListarExistencias listarExistencias(
        RepositorioProductos repositorioProductos,
        RepositorioInventario repositorioInventario,
        Reloj reloj) {
      return new ListarExistencias(repositorioProductos, repositorioInventario, reloj);
    }

    @Bean
    AjustarExistencia ajustarExistencia(
        RepositorioProductos repositorioProductos,
        RepositorioInventario repositorioInventario,
        Reloj reloj) {
      return new AjustarExistencia(repositorioProductos, repositorioInventario, reloj);
    }

    @Bean
    MapeadorExistencias mapeadorExistencias() {
      return new MapeadorExistencias();
    }
  }
}
