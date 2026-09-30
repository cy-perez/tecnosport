package co.tecnosport.api.application.proveedores;

/**
 * La exportación pasa del tope. El tope existe por el trabajador, no por la red: el zip entero se
 * abre en memoria en una instancia de Cloud Run con memoria contada, y un chat de años con sus
 * fotos no es lo que este flujo procesa — se exporta el chat reciente, o sin medios.
 */
public final class ExportacionDemasiadoGrandeException extends RuntimeException {

  public ExportacionDemasiadoGrandeException(long tamanoBytes, long maximoBytes) {
    super(
        "La exportación pesa "
            + enMegas(tamanoBytes)
            + " MB y el máximo es "
            + enMegas(maximoBytes)
            + " MB. Exporta un periodo más corto, o sin archivos.");
  }

  private static long enMegas(long bytes) {
    return Math.round(bytes / 1_048_576.0);
  }
}
