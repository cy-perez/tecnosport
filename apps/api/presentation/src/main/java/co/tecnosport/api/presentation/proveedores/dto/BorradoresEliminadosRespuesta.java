package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.BorradoresEliminados;

/** Lo que dejó una tanda del borrado en bloque; con {@code quedan > 0} el panel pide otra. */
public record BorradoresEliminadosRespuesta(int eliminados, int archivosBorrados, long quedan) {

  public static BorradoresEliminadosRespuesta de(BorradoresEliminados resultado) {
    return new BorradoresEliminadosRespuesta(
        resultado.eliminados(), resultado.archivosBorrados(), resultado.quedan());
  }
}
