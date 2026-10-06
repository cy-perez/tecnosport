package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * El segundo paso de la subida: el panel dice qué key subió y el servidor lo comprueba contra el
 * bucket antes de colgar la foto del borrador. No se confía en el cliente: la key tiene que estar
 * bajo el prefijo de <b>este</b> borrador, el objeto tiene que existir, no pasar del tope y abrirse
 * como imagen.
 *
 * <p><b>Se abre aquí y no solo al aprobar.</b> La aprobación la abre igual para medirla, pero
 * entonces el error llega con el formulario lleno y nombra una key que nadie reconoce. Un archivo
 * que no abre se borra del bucket antes de responder: no es de nadie y nadie más lo va a limpiar.
 */
public final class ConfirmarFotoDeBorrador {

  private final RepositorioBorradores repositorioBorradores;
  private final AlmacenDeArchivosDeProveedor almacen;
  private final ProcesadorDeImagenes procesador;
  private final Reloj reloj;
  private final long maximoBytes;

  /**
   * @param maximoBytes el tope de la foto ({@code PROVEEDORES_FOTO_MAXIMA_BYTES}): se mira con el
   *     tamaño que dice el bucket, antes de traer un solo byte
   */
  public ConfirmarFotoDeBorrador(
      RepositorioBorradores repositorioBorradores,
      AlmacenDeArchivosDeProveedor almacen,
      ProcesadorDeImagenes procesador,
      Reloj reloj,
      long maximoBytes) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.almacen = Objects.requireNonNull(almacen);
    this.procesador = Objects.requireNonNull(procesador);
    this.reloj = Objects.requireNonNull(reloj);
    if (maximoBytes <= 0) {
      throw new IllegalArgumentException("El tope de la foto tiene que ser positivo.");
    }
    this.maximoBytes = maximoBytes;
  }

  public VerBorrador.FotoDeBorrador ejecutar(UUID borradorId, String objectKey) {
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    String clave = objectKey == null ? null : objectKey.strip();
    if (!ClavesDeProveedor.esFotoDeBorrador(borrador.proveedorId(), borrador.id(), clave)) {
      throw new IllegalArgumentException(
          "El objeto '" + clave + "' no es una foto subida a este borrador.");
    }
    if (borrador.fotosSubidas().stream().anyMatch(f -> f.referenciaArchivo().equals(clave))) {
      throw new IllegalArgumentException("Esa foto ya está en el borrador.");
    }
    long tamano =
        almacen
            .tamanoBytes(clave)
            .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(clave));
    // La URL firmada no acota lo que se sube: el tope se aplica aquí, y lo que lo pasa se borra
    // porque no es de nadie.
    if (tamano > maximoBytes) {
      almacen.borrar(clave);
      throw new FotoDemasiadoGrandeException(tamano, maximoBytes);
    }
    byte[] bytes =
        almacen.leer(clave).orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(clave));
    try {
      procesador.procesar(bytes, contentTypeDe(clave));
    } catch (ImagenDeProveedorIlegibleException e) {
      almacen.borrar(clave);
      throw new ImagenDeProveedorIlegibleException(clave);
    }

    FotoSubida foto = new FotoSubida(GeneradorIdentificador.nuevo(), clave, reloj.ahora());
    borrador.agregarFotoSubida(foto);
    repositorioBorradores.actualizar(borrador);
    return new VerBorrador.FotoDeBorrador(
        foto.id(), almacen.urlDeLectura(clave).url(), null, VerBorrador.OrigenDeFoto.PANEL);
  }

  /**
   * La key ya pasó por {@link ClavesDeProveedor#esFotoDeBorrador}: es {@code .jpg} o {@code .png}.
   */
  private static String contentTypeDe(String clave) {
    return clave.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
  }
}
