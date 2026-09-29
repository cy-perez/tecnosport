package co.tecnosport.api.domain.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicacionEnRedTest {

  private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");
  private static final Instant LUEGO = Instant.parse("2026-09-29T15:00:04Z");
  private static final UUID PRODUCTO = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6");
  private static final String PIE = "JBL Grip — $299.900\n\nUn parlante portátil de 385 gramos.";
  private static final String IMAGEN = "https://storage.googleapis.com/bucket/principal.jpg";

  private static PublicacionEnRed solicitada() {
    return PublicacionEnRed.solicitar(PRODUCTO, RedSocial.INSTAGRAM, PIE, IMAGEN, AHORA);
  }

  @Test
  void naceEnPendienteAunqueLaRedVayaAContestarEnSeguida() {
    PublicacionEnRed publicacion = solicitada();

    assertEquals(EstadoPublicacion.PENDIENTE, publicacion.estado());
    assertEquals(Optional.empty(), publicacion.idPublicacionExterna());
    assertEquals(Optional.empty(), publicacion.publicadaEn());
    assertEquals(Optional.of(PRODUCTO), publicacion.productoId());
  }

  @Test
  void confirmarGuardaElIdentificadorYLaFecha() {
    PublicacionEnRed publicacion = solicitada();

    publicacion.confirmarPublicada("18196134166390376", LUEGO);

    assertEquals(EstadoPublicacion.PUBLICADA, publicacion.estado());
    assertEquals(Optional.of("18196134166390376"), publicacion.idPublicacionExterna());
    assertEquals(Optional.of(LUEGO), publicacion.publicadaEn());
  }

  /**
   * El adaptador puede consultar el estado del contenedor más de una vez y llegar dos veces a la
   * misma respuesta. Eso no es un error y no tiene por qué romper nada.
   */
  @Test
  void confirmarDosVecesConElMismoIdentificadorNoMolesta() {
    PublicacionEnRed publicacion = solicitada();

    publicacion.confirmarPublicada("18196134166390376", LUEGO);
    publicacion.confirmarPublicada("18196134166390376", LUEGO);

    assertEquals(EstadoPublicacion.PUBLICADA, publicacion.estado());
    assertEquals(Optional.of(LUEGO), publicacion.publicadaEn());
  }

  /** Dos identificadores distintos son dos publicaciones, y esta fila solo puede contar una. */
  @Test
  void confirmarConOtroIdentificadorEsUnError() {
    PublicacionEnRed publicacion = solicitada();
    publicacion.confirmarPublicada("18196134166390376", LUEGO);

    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class, () -> publicacion.confirmarPublicada("99999999999", LUEGO));

    assertTrue(error.getMessage().contains("otro identificador"), error.getMessage());
  }

  @Test
  void loQueYaSePublicoNoPuedeDarsePorFallido() {
    PublicacionEnRed publicacion = solicitada();
    publicacion.confirmarPublicada("18196134166390376", LUEGO);

    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> publicacion.marcarFallida("timeout"));

    assertTrue(error.getMessage().contains("el post existe"), error.getMessage());
  }

  @Test
  void loQueFalloNoPuedeDarsePorPublicado() {
    PublicacionEnRed publicacion = solicitada();
    publicacion.marcarFallida("La imagen no se pudo descargar.");

    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> publicacion.confirmarPublicada("18196134166390376", LUEGO));

    assertTrue(error.getMessage().contains("ya falló"), error.getMessage());
  }

  @Test
  void elMotivoDelFalloSeGuarda() {
    PublicacionEnRed publicacion = solicitada();

    publicacion.marcarFallida("La imagen no se pudo descargar.");

    assertEquals(EstadoPublicacion.FALLIDA, publicacion.estado());
    assertEquals(Optional.of("La imagen no se pudo descargar."), publicacion.detalleDelFallo());
  }

  @Test
  void sinPieNoSePublica() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                PublicacionEnRed.solicitar(PRODUCTO, RedSocial.FACEBOOK, "   \n ", IMAGEN, AHORA));

    assertTrue(error.getMessage().contains("sin pie de foto"), error.getMessage());
  }

  @Test
  void sinImagenNoSePublica() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> PublicacionEnRed.solicitar(PRODUCTO, RedSocial.FACEBOOK, PIE, "  ", AHORA));

    assertTrue(error.getMessage().contains("sin imagen"), error.getMessage());
  }

  @Test
  void unPieDesmesuradoNoCabe() {
    String largo = "a".repeat(PublicacionEnRed.MAXIMO_CARACTERES_PIE + 1);

    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> PublicacionEnRed.solicitar(PRODUCTO, RedSocial.FACEBOOK, largo, IMAGEN, AHORA));

    assertTrue(error.getMessage().contains("no puede pasar de"), error.getMessage());
  }

  @Test
  void noSeDifundeUnProductoSinDecirCual() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () -> PublicacionEnRed.solicitar(null, RedSocial.FACEBOOK, PIE, IMAGEN, AHORA));

    assertTrue(error.getMessage().contains("sin decir cuál"), error.getMessage());
  }

  /**
   * El producto borrado del catálogo deja la constancia huérfana, y tiene que poder reconstruirse
   * desde la base: es la fila que dice qué post quedó apuntando a un 404.
   */
  @Test
  void seReconstruyeSinProductoPorqueElCatalogoPudoBorrarlo() {
    PublicacionEnRed publicacion =
        new PublicacionEnRed(
            UUID.randomUUID(),
            null,
            RedSocial.INSTAGRAM,
            PIE,
            IMAGEN,
            EstadoPublicacion.PUBLICADA,
            "18196134166390376",
            AHORA,
            LUEGO,
            null);

    assertEquals(Optional.empty(), publicacion.productoId());
    assertEquals(EstadoPublicacion.PUBLICADA, publicacion.estado());
  }

  @Test
  void unaPublicadaSinIdentificadorNoSeSostiene() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                new PublicacionEnRed(
                    UUID.randomUUID(),
                    PRODUCTO,
                    RedSocial.INSTAGRAM,
                    PIE,
                    IMAGEN,
                    EstadoPublicacion.PUBLICADA,
                    null,
                    AHORA,
                    LUEGO,
                    null));

    assertTrue(error.getMessage().contains("identificador de la red"), error.getMessage());
  }
}
