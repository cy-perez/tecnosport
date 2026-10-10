package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Las fotos de otras publicaciones que se suman a un borrador (10 de octubre de 2026). */
class FotosAgregadasTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
  private static final UUID PROPIA = UUID.randomUUID();
  private static final UUID DESCARTADA = UUID.randomUUID();
  private static final List<UUID> DE_SU_PUBLICACION = List.of(PROPIA, DESCARTADA);

  private static BorradorProducto camiseta(Set<AlertaBorrador> alertas) {
    return BorradorProducto.nuevo(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ProductoExtraido(
            true,
            false,
            "Camiseta slim",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(42000),
            Tallas.lista(List.of("S", "M", "L")),
            null,
            List.of(),
            null,
            "Camiseta slim.",
            null,
            false,
            new BigDecimal("0.9"),
            null),
        "{}",
        Dinero.deCop(42000),
        Dinero.deCop(62000),
        null,
        null,
        alertas,
        new FotosDelProducto(Set.of(DESCARTADA), Map.of(), null),
        AHORA);
  }

  @Test
  void sumaLasDeOtraPublicacionSinRepetirNiTraerLasSuyas() {
    BorradorProducto borrador = camiseta(Set.of(AlertaBorrador.SIN_FOTOS));
    UUID deOtra = UUID.randomUUID();

    List<UUID> sumadas = borrador.agregarFotos(List.of(deOtra, PROPIA, deOtra), DE_SU_PUBLICACION);

    assertEquals(List.of(deOtra), sumadas);
    assertEquals(List.of(deOtra), borrador.fotosAgregadas());
    assertFalse(borrador.alertas().contains(AlertaBorrador.SIN_FOTOS));
    assertEquals(List.of(), borrador.agregarFotos(List.of(deOtra), DE_SU_PUBLICACION));
  }

  @Test
  void descartarUnaAgregadaLaSacaDeLasAgregadasYNoLaMarcaDescartada() {
    BorradorProducto borrador = camiseta(Set.of());
    UUID deOtra = UUID.randomUUID();
    borrador.agregarFotos(List.of(deOtra), DE_SU_PUBLICACION);

    borrador.descartarFoto(deOtra);

    assertEquals(List.of(), borrador.fotosAgregadas());
    assertEquals(Set.of(DESCARTADA), borrador.fotosDescartadas());
  }

  @Test
  void recuperaUnaDeSuPublicacionQueEstabaDescartada() {
    BorradorProducto borrador = camiseta(Set.of(AlertaBorrador.SIN_FOTOS));

    borrador.recuperarFoto(DESCARTADA);

    assertEquals(Set.of(), borrador.fotosDescartadas());
    assertFalse(borrador.alertas().contains(AlertaBorrador.SIN_FOTOS));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.recuperarFoto(PROPIA));
  }

  @Test
  void unBorradorDecididoNoRecibeFotos() {
    BorradorProducto borrador = camiseta(Set.of());
    borrador.rechazar("Repetido.");

    assertThrows(
        ExcepcionDeDominio.class,
        () -> borrador.agregarFotos(List.of(UUID.randomUUID()), DE_SU_PUBLICACION));
    assertTrue(borrador.fotosAgregadas().isEmpty());
  }
}
