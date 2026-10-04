package co.tecnosport.api.application.proveedores;

import java.util.List;

/**
 * Lo que cuelga de un proveedor y decide si se puede eliminar.
 *
 * @param productos los del catálogo que salieron de él; con uno solo, no se elimina
 * @param ingestaEnCurso si tiene un lote en la cola o a medio procesar
 * @param archivos las keys del bucket privado que son suyas: las exportaciones subidas y las fotos
 *     de sus mensajes
 */
public record DependenciasDeProveedor(
    long productos, boolean ingestaEnCurso, List<String> archivos) {

  public DependenciasDeProveedor {
    archivos = archivos == null ? List.of() : List.copyOf(archivos);
  }
}
