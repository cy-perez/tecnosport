package co.tecnosport.api.domain.compartido;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID versión 7 (RFC 9562): 48 bits de marca de tiempo en milisegundos más bits aleatorios, para
 * que los identificadores generados en el dominio ordenen aproximadamente por tiempo de creación
 * sin exponer un contador ni depender de la base de datos.
 */
public final class GeneradorIdentificador {

  private static final SecureRandom ALEATORIO = new SecureRandom();

  private GeneradorIdentificador() {}

  public static UUID nuevo() {
    return generar(System.currentTimeMillis(), ALEATORIO);
  }

  static UUID generar(long milisegundosEpoca, SecureRandom aleatorio) {
    byte[] datosAleatorios = new byte[10];
    aleatorio.nextBytes(datosAleatorios);

    long randA = ((datosAleatorios[0] & 0xFFL) << 4) | ((datosAleatorios[1] & 0xF0L) >>> 4);
    long msb = ((milisegundosEpoca & 0xFFFFFFFFFFFFL) << 16) | (0x7L << 12) | (randA & 0xFFFL);

    long randB = 0;
    for (int i = 2; i < datosAleatorios.length; i++) {
      randB = (randB << 8) | (datosAleatorios[i] & 0xFFL);
    }
    long lsb = (0b10L << 62) | (randB & 0x3FFFFFFFFFFFFFFFL);

    return new UUID(msb, lsb);
  }
}
