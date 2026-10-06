package co.tecnosport.api.application.proveedores;

/**
 * La foto subida a un borrador pasa del tope. Como el de la exportación, el tope es por la
 * instancia y no por la red: confirmarla la abre entera en memoria para comprobar que es una
 * imagen, y aprobarla la vuelve a abrir para medirla.
 */
public final class FotoDemasiadoGrandeException extends RuntimeException {

  public FotoDemasiadoGrandeException(long tamanoBytes, long maximoBytes) {
    super(
        "La foto pesa "
            + enMegas(tamanoBytes)
            + " MB y el máximo es "
            + enMegas(maximoBytes)
            + " MB. Redúcela o expórtala con menos resolución.");
  }

  private static String enMegas(long bytes) {
    return String.format(java.util.Locale.ROOT, "%.1f", bytes / 1_048_576.0);
  }
}
