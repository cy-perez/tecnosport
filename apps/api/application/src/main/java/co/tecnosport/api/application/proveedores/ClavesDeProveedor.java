package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.ZonaDelNegocio;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Dónde vive cada cosa en el bucket privado del proveedor.
 *
 * <pre>
 * proveedores/{proveedorId}/exportaciones/{uuid}.zip     lo que subió el panel
 * proveedores/{proveedorId}/{yyyy}/{MM}/{mensajeId}.{ext}  cada foto, por mes de envío
 * </pre>
 *
 * <p>El mes es el de la zona del negocio, no UTC: un mensaje de las 11 de la noche del 30 es del
 * 30, que es como lo va a buscar quien mire el bucket. Y el prefijo de exportaciones es lo que deja
 * comprobar que la key que el panel devuelve es de ese proveedor y no de otro sitio.
 */
final class ClavesDeProveedor {

  private static final DateTimeFormatter MES =
      DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZonaDelNegocio.ZONA);

  private static final Map<String, String> EXTENSIONES =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp",
          "image/gif", "gif");

  static final String CONTENT_TYPE_ZIP = "application/zip";

  /** Lo que mandan los navegadores de Windows por un {@code .zip}. */
  static final String CONTENT_TYPE_ZIP_WINDOWS = "application/x-zip-compressed";

  private ClavesDeProveedor() {}

  static String nuevaExportacion(UUID proveedorId) {
    return prefijoDeExportaciones(proveedorId) + GeneradorIdentificador.nuevo() + ".zip";
  }

  static boolean esExportacionDe(UUID proveedorId, String objectKey) {
    return objectKey != null
        && objectKey.startsWith(prefijoDeExportaciones(proveedorId))
        && objectKey.endsWith(".zip")
        && !objectKey.contains("..");
  }

  static String medio(UUID proveedorId, Instant enviadoEn, UUID mensajeId, String contentType) {
    return "proveedores/"
        + proveedorId
        + "/"
        + MES.format(enviadoEn)
        + "/"
        + mensajeId
        + "."
        + EXTENSIONES.getOrDefault(contentType, "bin");
  }

  static boolean esZip(String contentType) {
    return CONTENT_TYPE_ZIP.equals(contentType) || CONTENT_TYPE_ZIP_WINDOWS.equals(contentType);
  }

  private static String prefijoDeExportaciones(UUID proveedorId) {
    return "proveedores/" + proveedorId + "/exportaciones/";
  }
}
