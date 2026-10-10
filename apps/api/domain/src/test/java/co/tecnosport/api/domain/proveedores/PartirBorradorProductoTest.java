package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartirBorradorProductoTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
  private static final UUID F1 = UUID.randomUUID();
  private static final UUID F2 = UUID.randomUUID();
  private static final UUID F3 = UUID.randomUUID();
  private static final UUID AJENA = UUID.randomUUID();
  private static final List<UUID> DE_LA_PUBLICACION = List.of(F1, F2, F3, AJENA);

  private static BorradorProducto camisetas() {
    UUID proveedor = UUID.randomUUID();
    return BorradorProducto.nuevo(
        UUID.randomUUID(),
        proveedor,
        new ProductoExtraido(
            true,
            false,
            "Camiseta oversize",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(55000),
            Tallas.lista(List.of("S", "M")),
            null,
            List.of(),
            null,
            "Camiseta oversize.",
            null,
            false,
            new BigDecimal("0.9"),
            null),
        "{}",
        Dinero.deCop(55000),
        Dinero.deCop(80000),
        HuellaProveedor.deReferencia(proveedor, "RV102347"),
        new PHash(7),
        Set.of(AlertaBorrador.SIN_FOTOS, AlertaBorrador.REPLICA),
        new FotosDelProducto(Set.of(AJENA), Map.of(F2, "negro", F3, "gris"), "{\"l\":1}"),
        AHORA);
  }

  @Test
  void lasFotosNombradasSeVanAUnBorradorNuevoConSusTonosYOtraHuella() {
    BorradorProducto origen = camisetas();

    BorradorProducto nuevo = origen.partir(List.of(F3, F2), DE_LA_PUBLICACION, AHORA);

    assertEquals(Set.of(F1, AJENA), nuevo.fotosDescartadas());
    assertEquals(Map.of(F2, "negro", F3, "gris"), nuevo.tonosSugeridos());
    assertEquals(EstadoBorrador.EN_REVISION, nuevo.estado());
    assertEquals(Set.of(AlertaBorrador.SIN_FOTOS, AlertaBorrador.REPLICA), nuevo.alertas());
    assertEquals(Optional.empty(), nuevo.pHash());
    assertNotEquals(origen.huella(), nuevo.huella());
    assertEquals(
        Optional.of(HuellaProveedor.deParte(origen.huella().orElseThrow(), F2.toString())),
        nuevo.huella(),
        "la huella sale de la primera foto en el orden de la publicación");

    assertEquals(Set.of(AJENA, F2, F3), origen.fotosDescartadas());
    assertEquals(Map.of(), origen.tonosSugeridos());
    assertEquals(Optional.empty(), origen.pHash());
  }

  @Test
  void soloSeLlevaFotosQueElBorradorConservaYLeDejaAlMenosUna() {
    BorradorProducto origen = camisetas();

    assertThrows(
        ExcepcionDeDominio.class, () -> origen.partir(List.of(AJENA), DE_LA_PUBLICACION, AHORA));
    assertThrows(
        ExcepcionDeDominio.class, () -> origen.partir(List.of(), DE_LA_PUBLICACION, AHORA));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> origen.partir(List.of(F1, F2, F3), DE_LA_PUBLICACION, AHORA));
    assertTrue(origen.fotosDescartadas().equals(Set.of(AJENA)), "un intento fallido no toca nada");
  }

  @Test
  void unBorradorQueYaNoEstaEnRevisionNoSeParte() {
    BorradorProducto origen = camisetas();
    origen.rechazar("No es de la tienda.");

    assertThrows(
        ExcepcionDeDominio.class, () -> origen.partir(List.of(F1), DE_LA_PUBLICACION, AHORA));
  }
}
