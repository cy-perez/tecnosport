package co.tecnosport.api.infrastructure.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CifradorAesGcmTest {

  private static final byte[] LLAVE = new byte[CifradorAesGcm.BYTES_DE_LLAVE];
  private static final byte[] CLARO = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
  private static final UUID USUARIO = UUID.fromString("0192f4a0-0000-7000-8000-000000000001");

  static {
    for (int i = 0; i < LLAVE.length; i++) {
      LLAVE[i] = (byte) i;
    }
  }

  private final CifradorAesGcm cifrador = new CifradorAesGcm(LLAVE);

  @Test
  void idaYVuelta() {
    String guardado = cifrador.cifrar(CLARO, USUARIO);

    assertThat(guardado).startsWith("v1:");
    assertThat(cifrador.descifrar(guardado, USUARIO)).isEqualTo(CLARO);
  }

  @Test
  void dosCifradosDelMismoTextoDifieren() {
    assertThat(cifrador.cifrar(CLARO, USUARIO)).isNotEqualTo(cifrador.cifrar(CLARO, USUARIO));
  }

  @Test
  void unByteAlteradoNoSeDescifra() {
    String guardado = cifrador.cifrar(CLARO, USUARIO);
    byte[] crudo = Base64.getDecoder().decode(guardado.substring(3));
    crudo[crudo.length - 1] ^= 0x01;
    String alterado = "v1:" + Base64.getEncoder().encodeToString(crudo);

    assertThatThrownBy(() -> cifrador.descifrar(alterado, USUARIO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void laFilaDeOtroUsuarioNoSeDescifra() {
    String guardado = cifrador.cifrar(CLARO, USUARIO);

    assertThatThrownBy(() -> cifrador.descifrar(guardado, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void conOtraLlaveNoSeDescifra() {
    String guardado = cifrador.cifrar(CLARO, USUARIO);
    CifradorAesGcm otro = new CifradorAesGcm(new byte[CifradorAesGcm.BYTES_DE_LLAVE]);

    assertThatThrownBy(() -> otro.descifrar(guardado, USUARIO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void laLlaveTieneQueSerDe32Bytes() {
    assertThatThrownBy(() -> new CifradorAesGcm(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            CifradorAesGcm.desdeBase64(Base64.getEncoder().encodeToString(LLAVE))
                .descifrar(cifrador.cifrar(CLARO, USUARIO), USUARIO))
        .isEqualTo(CLARO);
  }

  @Test
  void unFormatoDesconocidoNoSeDescifra() {
    assertThatThrownBy(() -> cifrador.descifrar("v2:abc", USUARIO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cifrador.descifrar("v1:no-es-base64!", USUARIO))
        .isInstanceOf(IllegalStateException.class);
  }
}
