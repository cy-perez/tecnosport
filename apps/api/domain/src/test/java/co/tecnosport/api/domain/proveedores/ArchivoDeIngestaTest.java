package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchivoDeIngestaTest {

  private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();

  @Test
  void elNombreSeGuardaSinCarpetasNiEspacios() {
    assertEquals(
        Optional.of("Chat de WhatsApp con Meraki.zip"),
        recibir("C:\\Users\\x\\Descargas\\ Chat de WhatsApp con Meraki.zip ").nombreOriginal());
    assertEquals(Optional.of("a.zip"), recibir("/tmp/b/a.zip").nombreOriginal());
  }

  @Test
  void unNombreVacioEsNuloYUnoLargoSeRecorta() {
    assertTrue(recibir("   ").nombreOriginal().isEmpty());
    assertTrue(recibir(null).nombreOriginal().isEmpty());
    assertEquals(
        ArchivoDeIngesta.LARGO_MAXIMO_DEL_NOMBRE,
        recibir("x".repeat(400) + ".zip").nombreOriginal().orElseThrow().length());
  }

  @Test
  void borrarseMarcaUnaSolaVezConLaPrimeraFecha() {
    ArchivoDeIngesta archivo = recibir("a.zip");
    assertFalse(archivo.borrado());

    archivo.marcarBorrado(T.plusSeconds(60));
    archivo.marcarBorrado(T.plusSeconds(120));

    assertTrue(archivo.borrado());
    assertEquals(Optional.of(T.plusSeconds(60)), archivo.borradoEn());
  }

  @Test
  void elTamanoNoEsNegativo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new ArchivoDeIngesta(UUID.randomUUID(), "k.zip", PROVEEDOR, null, -1L, T, null));
  }

  private static ArchivoDeIngesta recibir(String nombre) {
    return ArchivoDeIngesta.recibir("proveedores/p/exportaciones/k.zip", PROVEEDOR, nombre, 10, T);
  }
}
