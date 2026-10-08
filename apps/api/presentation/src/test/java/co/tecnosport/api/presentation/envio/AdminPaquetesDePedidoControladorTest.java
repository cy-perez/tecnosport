package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.envio.ArmadorDeBultos;
import co.tecnosport.api.application.envio.ConsultarPaquetesDePedido;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los paquetes para crear la guía a mano, por HTTP (adr/0071): el peso en kilos enteros, como lo
 * pide el formulario de la plataforma, y el valor declarado como dinero.
 */
@WebMvcTest(AdminPaquetesDePedidoControlador.class)
@Import(AdminPaquetesDePedidoControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminPaquetesDePedidoControladorTest {

  private static final String RUTA = "/api/v1/admin/envios/paquetes/";
  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null);

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private RepositorioPedidos pedidos;

  private Variante parlante;

  @BeforeEach
  void catalogo() {
    Producto producto =
        Producto.crear(
            "Parlante",
            new Slug("parlante"),
            "Descripción",
            Marca.crear("JBL"),
            Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA));
    parlante =
        Variante.crear(
            new Sku("TS-PAR-1"),
            Dinero.deCop(400_000),
            new BigDecimal("0.00"),
            null,
            new Paquete(1_500, 25, 20, 15),
            List.of());
    producto.agregarVariante(parlante);
    productos.conProductos(producto);
  }

  private Pedido sembrarPedido(TipoEntrega tipoEntrega) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 7),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    parlante.id(),
                    new Sku("TS-PAR-1"),
                    "Parlante",
                    1,
                    Dinero.deCop(400_000),
                    new BigDecimal("0.00"),
                    null,
                    UUID.randomUUID())),
            tipoEntrega,
            tipoEntrega == TipoEntrega.ENVIO_A_DOMICILIO ? MEDELLIN : null,
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            Instant.parse("2026-10-07T15:00:00Z"));
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void trae_cada_paquete_con_el_peso_en_kilos_enteros() throws Exception {
    Pedido pedido = sembrarPedido(TipoEntrega.ENVIO_A_DOMICILIO);

    mockMvc
        .perform(get(RUTA + pedido.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conRecaudo").value(false))
        .andExpect(jsonPath("$.paquetes.length()").value(1))
        .andExpect(jsonPath("$.paquetes[0].pesoKg").value(2))
        .andExpect(jsonPath("$.paquetes[0].largoCm").value(25))
        .andExpect(jsonPath("$.paquetes[0].anchoCm").value(20))
        .andExpect(jsonPath("$.paquetes[0].altoCm").value(15))
        .andExpect(jsonPath("$.paquetes[0].valorDeclarado.valor").value(400_000))
        .andExpect(jsonPath("$.paquetes[0].contenido").value("Electrónica y accesorios"));
  }

  @Test
  void un_retiro_en_punto_es_409() throws Exception {
    Pedido pedido = sembrarPedido(TipoEntrega.RETIRO_EN_PUNTO);

    mockMvc.perform(get(RUTA + pedido.id())).andExpect(status().isConflict());
  }

  @Test
  void un_pedido_que_no_existe_es_404() throws Exception {
    mockMvc.perform(get(RUTA + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    RepositorioPedidos repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    ConsultarPaquetesDePedido consultarPaquetesDePedido(
        RepositorioPedidos pedidos, RepositorioProductos productos) {
      return new ConsultarPaquetesDePedido(
          pedidos,
          ArmadorDeBultos.sinPromedios(productos, Dinero.deCop(10_000), Dinero.deCop(5_000_000)));
    }
  }
}
