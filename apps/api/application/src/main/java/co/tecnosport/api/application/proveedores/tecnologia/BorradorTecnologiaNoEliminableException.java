package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.EstadoBorrador;

/**
 * Un borrador de tecnología aprobado no se borra (decisión del negocio del 24/10/2026): es la
 * constancia del producto publicado, y guarda las configuraciones que se decidió no vender. Sin él,
 * la lista siguiente volvería a proponer lo que ya se vende.
 */
public final class BorradorTecnologiaNoEliminableException extends RuntimeException {

  public BorradorTecnologiaNoEliminableException(EstadoBorrador estado) {
    super(
        "Un borrador de tecnología "
            + estado
            + " no se borra: solo los que están en revisión y los rechazados.");
  }
}
