package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.EntradaMapaDelSitio;
import co.tecnosport.api.application.catalogo.ListarMapaDelSitio;
import co.tecnosport.api.application.catalogo.RepositorioMapaDelSitio;
import co.tecnosport.api.domain.compartido.Slug;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MapaDelSitioControlador.class)
@Import(MapaDelSitioControladorTest.Configuracion.class)
class MapaDelSitioControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioMapaDelSitioDobleDePrueba repositorio;

  @Test
  void devuelveSlugYFechaDeCadaProductoPublicado() throws Exception {
    repositorio.con(
        new EntradaMapaDelSitio(
            new Slug("tenis-trail-runner"), Instant.parse("2026-09-08T12:00:00Z")));

    mockMvc
        .perform(get("/api/v1/mapa-del-sitio"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productos", hasSize(1)))
        .andExpect(jsonPath("$.productos[0].slug").value("tenis-trail-runner"))
        .andExpect(jsonPath("$.productos[0].actualizadoEn").value("2026-09-08T12:00:00Z"));
  }

  /**
   * Un catálogo vacío devuelve 200 con la lista vacía, no 404: el sitemap del sitio se genera igual
   * —tiene portada y páginas legales— y un error aquí lo dejaría sin construir entero.
   */
  @Test
  void unCatalogoVacioDevuelve200ConListaVacia() throws Exception {
    repositorio.con();

    mockMvc
        .perform(get("/api/v1/mapa-del-sitio"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productos", hasSize(0)));
  }

  // Se intentó una tercera prueba —"la ruta no cuelga de /api/v1/productos, donde chocaría con
  // /{slug}"— y se quitó: en un @WebMvcTest de un solo controlador, ProductoControlador ni siquiera
  // está cargado, así que pedir esa ruta no demuestra nada sobre la colisión. Pasaba o fallaba por
  // motivos ajenos a lo que decía comprobar (devolvía 500, no 404, porque la rebanada no trae el
  // manejo de recurso no encontrado). Un guardián que dispara por otra cosa es peor que ninguno:
  // la decisión de dónde cuelga la ruta está razonada en el javadoc del controlador.

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioMapaDelSitioDobleDePrueba repositorioMapaDelSitio() {
      return new RepositorioMapaDelSitioDobleDePrueba();
    }

    @Bean
    ListarMapaDelSitio listarMapaDelSitio(RepositorioMapaDelSitio repositorio) {
      return new ListarMapaDelSitio(repositorio);
    }
  }

  static class RepositorioMapaDelSitioDobleDePrueba implements RepositorioMapaDelSitio {

    private List<EntradaMapaDelSitio> entradas = List.of();

    void con(EntradaMapaDelSitio... entradas) {
      this.entradas = List.of(entradas);
    }

    @Override
    public List<EntradaMapaDelSitio> listarProductosPublicados(int limite) {
      return entradas;
    }
  }
}
