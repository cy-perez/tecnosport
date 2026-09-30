package co.tecnosport.api.application.proveedores;

/**
 * Lo que se le hace a la foto del proveedor antes de publicarla: recorte, fondo, variantes en
 * varios anchos. <b>Hoy no se le hace nada</b> —la implementación devuelve el original con sus
 * medidas— y el puerto existe para que el día que se conecte el retoque no haya que tocar la
 * aprobación, igual que {@code EmisorFacturaElectronica} espera a la DIAN (ADR-0005).
 */
public interface ProcesadorDeImagenes {

  /**
   * @throws ImagenDeProveedorIlegibleException si los bytes no son una imagen que se pueda abrir
   */
  ImagenProcesada procesar(byte[] original, String contentType);
}
