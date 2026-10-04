package co.tecnosport.api.application.proveedores;

import java.util.Objects;
import java.util.UUID;

/**
 * Elimina un proveedor con todo su historial de ingesta: lotes, mensajes, publicaciones, borradores
 * y los archivos de su bucket privado.
 *
 * <p><b>Solo si ningún producto del catálogo salió de él</b> (decidido por el negocio el 3 de
 * octubre de 2026). Hasta entonces un proveedor no se borraba nunca —V69 dejó las llaves en {@code
 * restrict} con esa idea—; ahora sí, mientras no haya un producto que lo nombre. Con productos, la
 * salida sigue siendo desactivarlo.
 *
 * <p>Tampoco con una ingesta en la cola o a medio procesar: el hilo que la lee escribiría mensajes
 * y borradores de un proveedor que ya no existe.
 *
 * <p>Los objetos del bucket se borran antes que las filas, por la misma razón que en {@link
 * EliminarBorrador}: al revés, un fallo a mitad deja objetos que ya ninguna fila nombra. Así un
 * fallo deja filas que apuntan a archivos que ya no están, y reintentar termina el trabajo.
 */
public final class EliminarProveedor {

  private final RepositorioProveedores repositorioProveedores;
  private final AlmacenDeArchivosDeProveedor almacen;

  public EliminarProveedor(
      RepositorioProveedores repositorioProveedores, AlmacenDeArchivosDeProveedor almacen) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.almacen = Objects.requireNonNull(almacen);
  }

  /** Devuelve cuántos objetos se borraron del bucket, para que quien llame lo registre. */
  public int ejecutar(UUID proveedorId) {
    Objects.requireNonNull(proveedorId, "El id no puede ser nulo.");
    if (repositorioProveedores.buscarPorId(proveedorId).isEmpty()) {
      throw new ProveedorNoEncontradoException(proveedorId);
    }
    DependenciasDeProveedor dependencias = repositorioProveedores.dependenciasDe(proveedorId);
    if (dependencias.productos() > 0) {
      throw new ProveedorConProductosException(dependencias.productos());
    }
    if (dependencias.ingestaEnCurso()) {
      throw new ProveedorConIngestaEnCursoException();
    }
    for (String archivo : dependencias.archivos()) {
      almacen.borrar(archivo);
    }
    repositorioProveedores.eliminarConSuHistorial(proveedorId);
    return dependencias.archivos().size();
  }
}
