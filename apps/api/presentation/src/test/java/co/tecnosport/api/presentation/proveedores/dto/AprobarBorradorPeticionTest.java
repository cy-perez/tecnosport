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
                        1, "Negro", "#000000", null, FOTO_1),
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        2, "Cocoa", "#875F42", List.of("S"), FOTO_2),
                    new AprobarBorradorPeticion.PrendaAprobadaPeticion(
                        3, "Crema", null, null, null)),
                null)
            .aComando(BORRADOR);

    assertThat(comando.borradorId()).isEqualTo(BORRADOR);
    assertThat(comando.prendas())
        .extracting(AprobarBorradorComando.PrendaAprobada::seVeEn)
        .containsExactly(FOTO_1, FOTO_2, null);
    assertThat(comando.prendas().get(1).tallas()).containsExactly("S");
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

  private static AprobarBorradorPeticion peticion(
      List<AprobarBorradorPeticion.PrendaAprobadaPeticion> prendas, Boolean principalEnLaFicha) {
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
        principalEnLaFicha);
  }
}
