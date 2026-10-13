package co.tecnosport.api.presentation.proveedores.dto;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.AprobarBorradorComando;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lo que el cuerpo de la aprobación dice tiene que llegar al comando. La prueba del controlador no
 * lo puede ver: con los dobles de catálogo inertes, la aprobación muere antes de leer las prendas,
 * así que un campo que el controlador dejara atrás pasaría en verde.
 */
class AprobarBorradorPeticionTest {

  private static final UUID BORRADOR = UUID.randomUUID();
  private static final UUID FOTO_1 = UUID.randomUUID();
  private static final UUID FOTO_2 = UUID.randomUUID();

  /** La camiseta de cinco tonos en dos fotos (ADR-0082): cada prenda dice en cuál se ve. */
  @Test
  void laFotoEnQueSeVeCadaPrendaLlegaAlComando() {
    AprobarBorradorComando comando =
        peticion(
                List.of(
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        1, "Negro", "#000000", null, FOTO_1, null),
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        2, "Cocoa", "#875F42", List.of("S"), FOTO_2, null),
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        3, "Crema", null, null, null, null)),
                null)
            .aComando(BORRADOR);

    assertThat(comando.borradorId()).isEqualTo(BORRADOR);
    assertThat(comando.prendas())
        .extracting(AprobarBorradorComando.PrendaAprobada::seVeEn)
        .containsExactly(FOTO_1, FOTO_2, null);
    assertThat(comando.prendas().get(1).tallas()).containsExactly("S");
  }

  /** El SKU de cada prenda de un álbum llega al comando, en mayúsculas y sin espacios. */
  @Test
  void elSkuDeCadaPrendaLlegaAlComando() {
    AprobarBorradorComando comando =
        peticion(
                List.of(
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        1, "Negro", "#000000", null, null, " rv102347 "),
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        2, "Gris", "#808080", null, null, null)),
                null)
            .aComando(BORRADOR);

    assertThat(comando.prendas())
        .extracting(AprobarBorradorComando.PrendaAprobada::referencia)
        .containsExactly("RV102347", null);
  }

  @Test
  void laCasillaDeLaPrincipalLlegaAlComando() {
    assertThat(peticion(null, true).aComando(BORRADOR).principalEnLaFicha()).isTrue();
    assertThat(peticion(null, false).aComando(BORRADOR).principalEnLaFicha()).isFalse();
  }

  /** Un cliente que no conoce el campo sigue aprobando como antes: la portada no entra. */
  @Test
  void sinLaCasillaLaPrincipalNoEntraEnLaFicha() {
    AprobarBorradorComando comando = peticion(null, null).aComando(BORRADOR);

    assertThat(comando.principalEnLaFicha()).isFalse();
    assertThat(comando.prendas()).isEmpty();
  }

  /** El ajuste escrito en el panel viaja, y nulo deja el del borrador (ADR-0085). */
  @Test
  void elAjusteDeTallaViajaYNuloEsElDelBorrador() {
    assertThat(peticion(null, null, 2).aComando(BORRADOR).ajusteDeTalla()).isEqualTo(2);
    assertThat(peticion(null, null).aComando(BORRADOR).ajusteDeTalla()).isNull();
  }

  private static AprobarBorradorPeticion peticion(
      List<AprobarBorradorPeticion.PrendaAprobadaPeticion> prendas, Boolean principalEnLaFicha) {
    return peticion(prendas, principalEnLaFicha, null);
  }

  private static AprobarBorradorPeticion peticion(
      List<AprobarBorradorPeticion.PrendaAprobadaPeticion> prendas,
      Boolean principalEnLaFicha,
      Integer ajusteDeTalla) {
    return new AprobarBorradorPeticion(
        null,
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        71600,
        null,
        1,
        "Camiseta",
        "T-shirt",
        List.of(
            new AprobarBorradorPeticion.FotoAprobadaPeticion(FOTO_1, null, null, null),
            new AprobarBorradorPeticion.FotoAprobadaPeticion(FOTO_2, null, null, null)),
        prendas,
        principalEnLaFicha,
        ajusteDeTalla);
  }
}
