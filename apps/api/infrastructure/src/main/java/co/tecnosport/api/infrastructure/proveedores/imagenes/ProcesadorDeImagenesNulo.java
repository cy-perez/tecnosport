package co.tecnosport.api.infrastructure.proveedores.imagenes;

import co.tecnosport.api.application.proveedores.ImagenDeProveedorIlegibleException;
import co.tecnosport.api.application.proveedores.ImagenProcesada;
import co.tecnosport.api.application.proveedores.ProcesadorDeImagenes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * No retoca: devuelve los bytes tal cual con sus medidas reales. Es la implementación de esta
 * iteración, igual que la facturación electrónica tiene la suya nula (ADR-0005). Lo único que hace
 * es abrir la imagen para saber cuánto mide, porque la ficha necesita el ancho y el alto y el
 * proveedor no los manda.
 */
public final class ProcesadorDeImagenesNulo implements ProcesadorDeImagenes {

  @Override
  public ImagenProcesada procesar(byte[] original, String contentType) {
    BufferedImage imagen;
    try {
      imagen = ImageIO.read(new ByteArrayInputStream(original));
    } catch (IOException e) {
      throw new ImagenDeProveedorIlegibleException("(" + contentType + ")");
    }
    if (imagen == null) {
      throw new ImagenDeProveedorIlegibleException("(" + contentType + ")");
    }
    return new ImagenProcesada(original, contentType, imagen.getWidth(), imagen.getHeight());
  }
}
