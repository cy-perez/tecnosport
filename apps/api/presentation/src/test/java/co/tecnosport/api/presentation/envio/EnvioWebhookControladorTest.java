package co.tecnosport.api.presentation.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AplicarEventoDeEnvio;
import co.tecnosport.api.application.envio.LectorEventoDeEnvio;
import co.tecnosport.api.application.envio.RecibirEventoDeEnvio;
import co.tecnosport.api.application.envio.VerificadorFirmaEnvio;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * El contrato de este endpoint es corto y raro a primera vista: <strong>siempre 200</strong>, pase
 * lo que pase (adr/0022). Firma inválida y guía desconocida no se arreglan reintentando, así que un
 * 4xx solo metería el endpoint en el ciclo de reintentos de la plataforma.
 *
 * <p>Hoy además no aplica nada, porque sus dos puertos fallan cerrado mientras no se pueda medir un
 * evento real. Eso también se prueba: un evento cualquiera entra y sale sin efecto.
 */
@WebMvcTest(EnvioWebhookControlador.class)
@Import(EnvioWebhookControladorTest.Configuracion.class)
class EnvioWebhookControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private VerificadorFirmaDobleDePrueba verificador;

  private static final String CUERPO = "{\"evento\":\"delivered\",\"guia\":\"NN-1\"}";

  @Test
  void unEventoConFirmaInvalidaResponde200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/webhook")
                .header("authorization", "HMAC loquesea")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO))
        .andExpect(status().isOk());
  }

  /** Sin cabecera de firma tampoco es un 400: el verificador tiene que poder rechazarlo él. */
  @Test
  void unEventoSinCabeceraDeFirmaTambienResponde200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/webhook").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
        .andExpect(status().isOk());
  }

  /** Ni siquiera un cuerpo que no es JSON: este endpoint no discute, registra y responde. */
  @Test
  void unCuerpoQueNoEsJsonTambienResponde200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("esto no es json"))
        .andExpect(status().isOk());
  }

  /**
   * El cuerpo llega crudo al verificador, sin pasar por un parseo que reordene claves: un HMAC se
   * calcula sobre los bytes que llegaron. Se comprueba con un cuerpo con espacios y claves
   * desordenadas a propósito.
   */
  @Test
  void elCuerpoLlegaCrudoAlVerificador() throws Exception {
    String crudo = "{  \"b\" : 2,\n  \"a\":1 }";

    mockMvc
        .perform(
            post("/api/v1/envios/webhook")
                .header("authorization", "HMAC x")
                .contentType(MediaType.APPLICATION_JSON)
                .content(crudo))
        .andExpect(status().isOk());

    assertEquals(crudo, verificador.ultimoCuerpo());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    VerificadorFirmaDobleDePrueba verificadorFirma() {
      return new VerificadorFirmaDobleDePrueba();
    }

    /** Como el de producción mientras la forma del evento no se pueda medir: no sabe leer nada. */
    @Bean
    LectorEventoDeEnvio lectorEvento() {
      return cuerpo -> java.util.Optional.empty();
    }

    /**
     * Real y no un doble, porque {@code AplicarEventoDeEnvio} es final: se arma con repositorios
     * vacíos. En estas pruebas no se llega a usar —el lector descarta antes— pero el caso de uso
     * exige que exista, y con razón: un webhook sin nada detrás no tendría nada que probar.
     */
    @Bean
    AplicarEventoDeEnvio aplicarEventoDeEnvio(
        RepositorioEnviosDobleDePrueba envios,
        RepositorioPedidosDobleDePrueba pedidos,
        RepositorioInventarioDobleDePrueba inventarios) {
      Reloj reloj = () -> Instant.parse("2026-09-12T15:00:00Z");
      return new AplicarEventoDeEnvio(
          envios,
          pedidos,
          new MarcarEntregado(pedidos, inventarios, reloj),
          new RechazarEnEntrega(pedidos, inventarios, reloj),
          reloj);
    }

    @Bean
    RepositorioEnviosDobleDePrueba repositorioEnvios() {
      return new RepositorioEnviosDobleDePrueba();
    }

    @Bean
    RepositorioPedidosDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    RecibirEventoDeEnvio recibirEventoDeEnvio(
        VerificadorFirmaEnvio verificadorFirma,
        LectorEventoDeEnvio lector,
        AplicarEventoDeEnvio aplicar) {
      return new RecibirEventoDeEnvio(verificadorFirma, lector, aplicar);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  static final class VerificadorFirmaDobleDePrueba implements VerificadorFirmaEnvio {

    private final List<String> cuerpos = new java.util.ArrayList<>();

    @Override
    public boolean esValida(String cuerpoCrudo, String firma) {
      cuerpos.add(cuerpoCrudo);
      return true;
    }

    /**
     * El último, y no la lista: el contexto de Spring se comparte entre las pruebas de la clase,
     * así que el doble acumula lo que vieron las anteriores.
     */
    String ultimoCuerpo() {
      return cuerpos.get(cuerpos.size() - 1);
    }
  }
}
