package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.SolicitudDeSubida;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.AgregarFotoABorradorTecnologiaComando;
import co.tecnosport.api.application.proveedores.tecnologia.FotosFirmadasDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.QuitarFotoDeBorradorTecnologia;
import co.tecnosport.api.application.proveedores.tecnologia.SolicitarSubidaDeFotoDeBorradorTecnologia;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ModeloDeLista;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Las fotos que la skill de fichas sube a un borrador de tecnología (ADR-0079). */
class FotosDeBorradorTecnologiaTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
  private static final UUID PROVEEDOR = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");

  private final TecnologiaPorListasTest.BorradoresEnMemoria borradores =
      new TecnologiaPorListasTest.BorradoresEnMemoria();
  private final ApoyoDeIngesta.AlmacenEnMemoria almacen = new ApoyoDeIngesta.AlmacenEnMemoria();
  private final Reloj reloj = () -> AHORA;
  private BorradorTecnologia borrador;

  @BeforeEach
  void unBorradorEnRevision() {
    borrador =
        BorradorTecnologia.nuevo(
            PROVEEDOR,
            new ModeloDeLista(
                "xiaomi-poco-f9-ultra-5g",
                "Xiaomi POCO F9 Ultra 5G",
                "Xiaomi",
                "celulares",
                "El POCO F9 Ultra.",
                null,
                List.of("Negro")),
            List.of(
                ConfiguracionTecnologia.deLista(
                    "f9-512",
                    "Xiaomi POCO F9 Ultra 5G 16GB RAM 512GB",
                    "16GB",
                    "512GB",
                    null,
                    Dinero.deCop(2_900_000),
                    null,
                    List.of())),
            null,
            AHORA,
            AHORA);
    borradores.guardar(borrador);
  }

  private String subido(String contentType) {
    SolicitudDeSubida solicitud =
        new SolicitarSubidaDeFotoDeBorradorTecnologia(borradores, almacen)
            .ejecutar(borrador.id(), contentType);
    almacen.guardar(solicitud.objectKey(), contentType, new byte[100]);
    return solicitud.objectKey();
  }

  /** Un SHA-256 de mentiras pero bien formado, distinto por etiqueta. */
  private static String hex(String etiqueta) {
    return String.format("%064x", new java.math.BigInteger(1, etiqueta.getBytes()));
  }

  private AgregarFotoABorradorTecnologiaComando foto(String hash, String mayor, String vista) {
    return new AgregarFotoABorradorTecnologiaComando(
        borrador.id(),
        List.of(new AgregarFotoABorradorTecnologiaComando.Variante(1200, mayor)),
        vista,
        1200,
        hex(hash),
        "POCO F9 Ultra sobre fondo blanco",
        "POCO F9 Ultra on a white background");
  }

  private AgregarFotoABorradorTecnologia agregar() {
    return new AgregarFotoABorradorTecnologia(borradores, almacen, reloj);
  }

  @Test
  void laFotoQuedaConElTamanoQueDiceElBucketYEnOrden() {
    agregar().ejecutar(foto("a", subido("image/avif"), subido("image/jpeg")));
    agregar().ejecutar(foto("b", subido("image/avif"), null));

    BorradorTecnologia leido = borradores.buscarPorId(borrador.id()).orElseThrow();
    assertEquals(List.of(hex("a"), hex("b")), leido.fotos().stream().map(f -> f.hash()).toList());
    assertEquals(100, leido.fotos().getFirst().mayor().bytes());
    assertEquals(AHORA, leido.fotos().getFirst().subidaEn());
  }

  @Test
  void unHashMalFormadoNoEntra() {
    String clave = subido("image/avif");
    AgregarFotoABorradorTecnologiaComando conHashMalo =
        new AgregarFotoABorradorTecnologiaComando(
            borrador.id(),
            List.of(new AgregarFotoABorradorTecnologiaComando.Variante(1200, clave)),
            null,
            1200,
            "no-es-un-sha256",
            "alt",
            "alt");

    assertThrows(ExcepcionDeDominio.class, () -> agregar().ejecutar(conHashMalo));
  }

  @Test
  void unaClaveDeOtroBorradorNoEntra() {
    String ajena =
        "proveedores/" + PROVEEDOR + "/borradores-tecnologia/" + UUID.randomUUID() + "/x.avif";
    almacen.guardar(ajena, "image/avif", new byte[10]);

    assertThrows(IllegalArgumentException.class, () -> agregar().ejecutar(foto("a", ajena, null)));
    assertTrue(borradores.buscarPorId(borrador.id()).orElseThrow().fotos().isEmpty());
  }

  @Test
  void unArchivoQueNoLlegoNoEntra() {
    String clave = subido("image/avif");
    almacen.borrar(clave);

    assertThrows(
        ObjetoDeImagenNoEncontradoException.class,
        () -> agregar().ejecutar(foto("a", clave, null)));
  }

  @Test
  void noSeFirmaUnaSubidaQueNoVaACaber() {
    for (int i = 0; i < BorradorTecnologia.TOPE_DE_FOTOS; i++) {
      agregar().ejecutar(foto("f" + i, subido("image/avif"), null));
    }

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new SolicitarSubidaDeFotoDeBorradorTecnologia(borradores, almacen)
                .ejecutar(borrador.id(), "image/avif"));
  }

  @Test
  void quitarBorraLosArchivosDeLaFoto() {
    String mayor = subido("image/avif");
    String vista = subido("image/jpeg");
    String otra = subido("image/avif");
    agregar().ejecutar(foto("a", mayor, vista));
    agregar().ejecutar(foto("b", otra, null));
    UUID fotoId = borradores.buscarPorId(borrador.id()).orElseThrow().fotos().getFirst().id();

    new QuitarFotoDeBorradorTecnologia(borradores, almacen).ejecutar(borrador.id(), fotoId);

    assertFalse(almacen.objetos.containsKey(mayor));
    assertFalse(almacen.objetos.containsKey(vista));
    assertTrue(almacen.objetos.containsKey(otra), "la otra foto no se toca");
  }

  @Test
  void elPanelVeLaVarianteMayorFirmada() {
    String mayor = subido("image/avif");
    agregar().ejecutar(foto("a", mayor, null));

    List<FotosFirmadasDeBorradorTecnologia.FotoVista> fotos =
        new FotosFirmadasDeBorradorTecnologia(almacen)
            .ejecutar(borradores.buscarPorId(borrador.id()).orElseThrow());

    assertEquals("https://firmada.local/leer/" + mayor, fotos.getFirst().url());
  }

  @Test
  void unBorradorRechazadoNoRecibeFotos() {
    String clave = subido("image/avif");
    BorradorTecnologia leido = borradores.buscarPorId(borrador.id()).orElseThrow();
    leido.rechazar("No vendemos esta gama");
    borradores.actualizar(leido);

    assertThrows(
        BorradorNoEditableException.class, () -> agregar().ejecutar(foto("a", clave, null)));
  }
}
