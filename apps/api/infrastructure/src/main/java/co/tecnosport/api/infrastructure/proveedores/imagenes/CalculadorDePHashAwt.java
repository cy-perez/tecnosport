package co.tecnosport.api.infrastructure.proveedores.imagenes;

import co.tecnosport.api.application.proveedores.CalculadorDePHash;
import co.tecnosport.api.domain.proveedores.PHash;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Decodifica la foto con {@code javax.imageio}, la reduce a 32×32 en gris por promedio de área y le
 * entrega la luminancia a {@link PHash}, que es quien sabe de la matemática.
 *
 * <p>Corre con {@code -Djava.awt.headless=true}: no hay pantalla ni en Cloud Run ni en el {@code
 * bootRun} de la máquina de quien programa, y sin la bandera {@code java.awt} intenta cargar el
 * sistema gráfico. La imagen del contenedor es el JRE completo de Temurin, que trae {@code
 * java.desktop}.
 */
public final class CalculadorDePHashAwt implements CalculadorDePHash {

  @Override
  public Optional<PHash> de(byte[] imagen) {
    BufferedImage original;
    try {
      original = ImageIO.read(new ByteArrayInputStream(imagen));
    } catch (IOException e) {
      return Optional.empty();
    }
    if (original == null) {
      return Optional.empty();
    }
    return Optional.of(PHash.desdeLuminancia(luminancia(original)));
  }

  /**
   * Promedio de area, a mano: cada celda de la rejilla 32x32 es la media de los pixeles que le caen
   * encima. {@code drawImage} con interpolacion bilineal muestrea cuatro pixeles por celda, y de
   * una foto de 1600 px a 32 eso es tirar el 99 % de la imagen: la misma foto a dos tamanos daba
   * huellas a ocho bits de distancia. Con el promedio dan la misma.
   */
  static int[][] luminancia(BufferedImage original) {
    int ancho = original.getWidth();
    int alto = original.getHeight();
    int[] rgb = original.getRGB(0, 0, ancho, alto, null, 0, ancho);
    int[][] matriz = new int[PHash.LADO][PHash.LADO];
    for (int celdaX = 0; celdaX < PHash.LADO; celdaX++) {
      int x0 = celdaX * ancho / PHash.LADO;
      int x1 = Math.max(x0 + 1, (celdaX + 1) * ancho / PHash.LADO);
      for (int celdaY = 0; celdaY < PHash.LADO; celdaY++) {
        int y0 = celdaY * alto / PHash.LADO;
        int y1 = Math.max(y0 + 1, (celdaY + 1) * alto / PHash.LADO);
        long suma = 0;
        int cuenta = 0;
        for (int y = y0; y < y1 && y < alto; y++) {
          for (int x = x0; x < x1 && x < ancho; x++) {
            int pixel = rgb[y * ancho + x];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            suma += (299 * r + 587 * g + 114 * b) / 1000;
            cuenta++;
          }
        }
        matriz[celdaX][celdaY] = cuenta == 0 ? 0 : (int) (suma / cuenta);
      }
    }
    return matriz;
  }
}
