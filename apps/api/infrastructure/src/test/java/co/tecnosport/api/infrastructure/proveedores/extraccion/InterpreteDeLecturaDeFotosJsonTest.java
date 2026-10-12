package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import java.util.List;
import org.junit.jupiter.api.Test;

/** La lectura que el borrador guardó, leída otra vez al abrirlo en el panel. */
class InterpreteDeLecturaDeFotosJsonTest {

  private final InterpreteDeLecturaDeFotosJson interprete = new InterpreteDeLecturaDeFotosJson();

  @Test
  void losColoresDeCadaFotoSalenConSuPosicion() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"album_de_disenos\":false,\"fotos\":["
                    + "{\"foto\":1,\"colores\":[\"Negro\",\"cocoa\",\"verde\"]},"
                    + "{\"foto\":2,\"colores\":[\"negro\"]}]}")
            .orElseThrow();

    assertEquals(0, lectura.fotos().get(0).posicion());
    assertEquals(List.of("negro", "cocoa", "verde"), lectura.fotos().get(0).colores());
    assertEquals(List.of("negro"), lectura.fotos().get(1).colores());
  }

  /** Una lectura vieja o rota no impide revisar el borrador: no hay tonos que proponer, y ya. */
  @Test
  void loQueNoSeEntiendeNoEsUnaLectura() {
    assertTrue(interprete.interpretar("no es json").isEmpty());
    assertTrue(interprete.interpretar("{\"sin\":\"fotos\"}").isEmpty());
    assertTrue(interprete.interpretar("").isEmpty());
    assertTrue(interprete.interpretar(null).isEmpty());
  }

  @Test
  void unaFotoSinNumeroNoSeLee() {
    LecturaDeFotos lectura =
        interprete.interpretar("{\"fotos\":[{\"colores\":[\"rojo\",\"azul\"]}]}").orElseThrow();

    assertTrue(lectura.fotos().isEmpty());
  }
}
