package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.regex.Pattern;

/**
 * La huella visual de una foto: 64 bits que sobreviven a que la foto se reescale, se recomprima o
 * cambie un poco de brillo. Es lo que reconoce el mismo producto cuando el proveedor reescribe el
 * texto y manda la misma foto.
 *
 * <p>Es el pHash clásico: la foto en gris a 32×32, la DCT bidimensional, el bloque 8×8 de
 * frecuencias más bajas sin la componente continua, y un bit por celda según esté por encima de la
 * mediana. Dos fotos son «la misma» cuando la distancia de Hamming entre sus huellas está por
 * debajo de un umbral, que es configuración y no está aquí.
 *
 * <p>Aquí solo la matemática: decodificar el archivo y reducirlo a 32×32 es trabajo de la
 * infraestructura, que tiene {@code java.awt}. Esta clase recibe la luminancia y no sabe de
 * formatos.
 */
public record PHash(long bits) {

  public static final int LADO = 32;
  private static final int BLOQUE = 8;
  private static final Pattern HEX = Pattern.compile("^[0-9a-f]{16}$");

  /**
   * @param luminancia matriz {@value LADO}×{@value LADO} con valores de 0 a 255, fila por fila
   */
  public static PHash desdeLuminancia(int[][] luminancia) {
    if (luminancia == null || luminancia.length != LADO) {
      throw new ExcepcionDeDominio("La luminancia tiene que ser una matriz de " + LADO + " filas.");
    }
    for (int[] fila : luminancia) {
      if (fila == null || fila.length != LADO) {
        throw new ExcepcionDeDominio(
            "La luminancia tiene que ser una matriz de " + LADO + " columnas.");
      }
    }
    double[][] dct = dct(luminancia);

    double[] bajas = new double[BLOQUE * BLOQUE - 1];
    int k = 0;
    for (int u = 0; u < BLOQUE; u++) {
      for (int v = 0; v < BLOQUE; v++) {
        if (u == 0 && v == 0) {
          continue; // la componente continua es el brillo medio, y el brillo no identifica nada
        }
        bajas[k++] = dct[u][v];
      }
    }
    double mediana = mediana(bajas);

    long bits = 0;
    int posicion = 0;
    for (int u = 0; u < BLOQUE; u++) {
      for (int v = 0; v < BLOQUE; v++) {
        if (u == 0 && v == 0) {
          continue;
        }
        if (dct[u][v] > mediana) {
          bits |= 1L << posicion;
        }
        posicion++;
      }
    }
    return new PHash(bits);
  }

  public int distanciaHamming(PHash otro) {
    return Long.bitCount(bits ^ otro.bits);
  }

  public String hex() {
    return String.format("%016x", bits);
  }

  public static PHash deHex(String hex) {
    if (hex == null || !HEX.matcher(hex).matches()) {
      throw new ExcepcionDeDominio("Un pHash son 16 caracteres hexadecimales.");
    }
    return new PHash(Long.parseUnsignedLong(hex, 16));
  }

  /** DCT-II separable, sin optimizar: 32×32 son mil celdas y se calcula una vez por foto. */
  private static double[][] dct(int[][] f) {
    double[][] resultado = new double[LADO][LADO];
    double[][] cos = new double[LADO][LADO];
    for (int x = 0; x < LADO; x++) {
      for (int u = 0; u < LADO; u++) {
        cos[x][u] = Math.cos((2 * x + 1) * u * Math.PI / (2.0 * LADO));
      }
    }
    for (int u = 0; u < BLOQUE; u++) {
      for (int v = 0; v < BLOQUE; v++) {
        double suma = 0;
        for (int x = 0; x < LADO; x++) {
          for (int y = 0; y < LADO; y++) {
            suma += f[x][y] * cos[x][u] * cos[y][v];
          }
        }
        double cu = u == 0 ? Math.sqrt(0.5) : 1;
        double cv = v == 0 ? Math.sqrt(0.5) : 1;
        resultado[u][v] = 0.25 * cu * cv * suma;
      }
    }
    return resultado;
  }

  private static double mediana(double[] valores) {
    double[] copia = valores.clone();
    java.util.Arrays.sort(copia);
    int n = copia.length;
    return n % 2 == 1 ? copia[n / 2] : (copia[n / 2 - 1] + copia[n / 2]) / 2.0;
  }
}
