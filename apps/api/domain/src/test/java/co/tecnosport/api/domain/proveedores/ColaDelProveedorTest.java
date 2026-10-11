package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColaDelProveedorTest {

  private static final Instant T = Instant.parse("2026-10-11T06:17:00Z");
  private static final UUID MERAKI = UUID.fromString("01a0fe79-f4c1-7e17-a544-4528cf019569");
  private static final UUID VIOLETA = UUID.fromString("01a10496-608e-76ff-ad0b-4a914abe968f");

  private static LoteIngesta subido(UUID proveedor, Instant cuando) {
    return LoteIngesta.recibirExportacion(proveedor, "p/" + UUID.randomUUID() + ".zip", cuando);
  }

  /** El general de Meraki nace un milisegundo después del de caballero, y espera a que termine. */
  @Test
  void elMasNuevoEsperaAlAnteriorDelMismoProveedor() {
    LoteIngesta caballero = subido(MERAKI, T);
    LoteIngesta general = subido(MERAKI, T.plusMillis(1));
    List<LoteIngesta> abiertos = List.of(general, caballero);

    assertTrue(ColaDelProveedor.tieneQueEsperar(general, abiertos));
    assertFalse(ColaDelProveedor.tieneQueEsperar(caballero, abiertos));
  }

  @Test
  void elDeOtroProveedorNoHaceEsperar() {
    LoteIngesta deMeraki = subido(MERAKI, T);
    LoteIngesta deVioleta = subido(VIOLETA, T.plusSeconds(10));

    assertFalse(ColaDelProveedor.tieneQueEsperar(deVioleta, List.of(deMeraki, deVioleta)));
  }

  /**
   * El anterior en pausa sí retiene al siguiente: si el pausado es el chat de caballero, el general
   * no puede leerse antes que él.
   */
  @Test
  void elAnteriorEnPausaTambienHaceEsperar() {
    LoteIngesta anterior = subido(MERAKI, T);
    anterior.iniciar(T);
    anterior.pausar();
    LoteIngesta nuevo = subido(MERAKI, T.plusSeconds(10));

    assertTrue(ColaDelProveedor.tieneQueEsperar(nuevo, List.of(anterior, nuevo)));
  }

  /** En el mismo milisegundo decide el id: uno de los dos espera, nunca los dos ni ninguno. */
  @Test
  void aIgualFechaEsperaExactamenteUno() {
    LoteIngesta uno = subido(MERAKI, T);
    LoteIngesta otro = subido(MERAKI, T);
    List<LoteIngesta> abiertos = List.of(uno, otro);

    assertTrue(
        ColaDelProveedor.tieneQueEsperar(uno, abiertos)
            ^ ColaDelProveedor.tieneQueEsperar(otro, abiertos));
  }

  @Test
  void elSiguienteEsElMasAntiguoQueEsperaEnLaCola() {
    LoteIngesta enCurso = subido(MERAKI, T);
    enCurso.iniciar(T);
    LoteIngesta segundo = subido(MERAKI, T.plusSeconds(5));
    LoteIngesta tercero = subido(MERAKI, T.plusSeconds(9));
    LoteIngesta deOtro = subido(VIOLETA, T.minusSeconds(60));

    assertEquals(
        Optional.of(segundo.id()),
        ColaDelProveedor.siguiente(MERAKI, List.of(tercero, deOtro, enCurso, segundo)));
    assertEquals(Optional.empty(), ColaDelProveedor.siguiente(MERAKI, List.of(enCurso)));
  }
}
