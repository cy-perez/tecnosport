package co.tecnosport.api.infrastructure.proveedores.imagenes;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.ImagenDeProveedorIlegibleException;
import co.tecnosport.api.application.proveedores.ImagenProcesada;
import co.tecnosport.api.domain.proveedores.PHash;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** La foto real: la misma reescalada da distancia cero; otra da distancia alta. */
class CalculadorDePHashAwtTest {

  private final CalculadorDePHashAwt calculador = new CalculadorDePHashAwt();

  /** Un "bolso": fondo claro, una forma oscura, un asa. Con estructura, como una foto de verdad. */
  private static BufferedImage bolso(int ancho, int alto) {
    BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = imagen.createGraphics();
    g.setColor(new Color(235, 235, 235));
    g.fillRect(0, 0, ancho, alto);
    g.setColor(new Color(90, 40, 60));
    g.fillRoundRect(ancho / 5, alto / 3, ancho * 3 / 5, alto / 2, ancho / 10, alto / 10);
    g.setColor(new Color(60, 30, 40));
    g.fillArc(ancho / 3, alto / 8, ancho / 3, alto / 3, 0, 180);
    g.setColor(new Color(200, 170, 60));
    g.fillOval(ancho / 2 - ancho / 40, alto / 2, ancho / 20, alto / 20);
    g.dispose();
    return imagen;
  }

  private static byte[] jpeg(BufferedImage imagen) {
    try {
      ByteArrayOutputStream salida = new ByteArrayOutputStream();
      ImageIO.write(imagen, "jpg", salida);
      return salida.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void laMismaFotoReescaladaDaLaMismaHuella() {
    PHash grande = calculador.de(jpeg(bolso(1200, 1600))).orElseThrow();
    PHash chica = calculador.de(jpeg(bolso(300, 400))).orElseThrow();

    assertThat(grande.distanciaHamming(chica)).isLessThanOrEqualTo(2);
  }

  @Test
  void laMismaFotoRecomprimidaSigueCerca() throws IOException {
    byte[] original = jpeg(bolso(800, 1000));
    BufferedImage releida = ImageIO.read(new java.io.ByteArrayInputStream(original));
    byte[] recomprimida = jpeg(releida);

    PHash una = calculador.de(original).orElseThrow();
    PHash otra = calculador.de(recomprimida).orElseThrow();

    assertThat(una.distanciaHamming(otra)).isLessThanOrEqualTo(2);
  }

  @Test
  void otraFotoQuedaLejos() {
    BufferedImage ruido = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
    Random azar = new Random(11);
    for (int x = 0; x < 600; x++) {
      for (int y = 0; y < 800; y++) {
        ruido.setRGB(x, y, azar.nextInt(0xFFFFFF));
      }
    }

    PHash bolso = calculador.de(jpeg(bolso(600, 800))).orElseThrow();
    PHash otra = calculador.de(jpeg(ruido)).orElseThrow();

    assertThat(bolso.distanciaHamming(otra)).isGreaterThan(12);
  }

  @Test
  void loQueNoEsUnaImagenNoDejaHuella() {
    assertThat(calculador.de("hola".getBytes(StandardCharsets.UTF_8))).isEmpty();
  }

  @Test
  void elProcesadorNuloDevuelveElOriginalConSusMedidas() {
    byte[] original = jpeg(bolso(640, 480));

    ImagenProcesada procesada = new ProcesadorDeImagenesNulo().procesar(original, "image/jpeg");

    assertThat(procesada.bytes()).isSameAs(original);
    assertThat(procesada.ancho()).isEqualTo(640);
    assertThat(procesada.alto()).isEqualTo(480);
    Assertions.assertThatThrownBy(
            () ->
                new ProcesadorDeImagenesNulo()
                    .procesar("no".getBytes(StandardCharsets.UTF_8), "image/jpeg"))
        .isInstanceOf(ImagenDeProveedorIlegibleException.class);
  }
}
