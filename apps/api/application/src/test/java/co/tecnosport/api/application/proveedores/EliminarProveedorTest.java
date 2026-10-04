package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarProveedorTest {

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final EliminarProveedor eliminar = new EliminarProveedor(proveedores, almacen);

  private Proveedor guardado() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    return proveedor;
  }

  @Test
  void sinProductosSeVaConSuHistorialYSusArchivos() {
    Proveedor proveedor = guardado();
    almacen.guardar("p/exportaciones/a.zip", "application/zip", new byte[] {1});
    almacen.guardar("p/fotos/1.jpg", "image/jpeg", new byte[] {2});
    almacen.guardar("otro/fotos/9.jpg", "image/jpeg", new byte[] {3});
    proveedores.dependencias.put(
        proveedor.id(),
        new DependenciasDeProveedor(0, false, List.of("p/exportaciones/a.zip", "p/fotos/1.jpg")));

    int borrados = eliminar.ejecutar(proveedor.id());

    assertEquals(2, borrados);
    assertEquals(Optional.empty(), proveedores.buscarPorId(proveedor.id()));
    assertEquals(List.of(proveedor.id()), proveedores.eliminados);
    assertEquals(List.of("otro/fotos/9.jpg"), List.copyOf(almacen.objetos.keySet()));
  }

  /** Ni las filas ni los archivos: un rechazo no deja el proveedor a medio borrar. */
  @Test
  void conProductosNoSeTocaNada() {
    Proveedor proveedor = guardado();
    almacen.guardar("p/fotos/1.jpg", "image/jpeg", new byte[] {2});
    proveedores.dependencias.put(
        proveedor.id(), new DependenciasDeProveedor(4, false, List.of("p/fotos/1.jpg")));

    ProveedorConProductosException rechazo =
        assertThrows(ProveedorConProductosException.class, () -> eliminar.ejecutar(proveedor.id()));

    assertEquals(4, rechazo.productos());
    assertTrue(proveedores.buscarPorId(proveedor.id()).isPresent());
    assertTrue(proveedores.eliminados.isEmpty());
    assertTrue(almacen.objetos.containsKey("p/fotos/1.jpg"));
  }

  @Test
  void conUnaIngestaEnCursoNoSeTocaNada() {
    Proveedor proveedor = guardado();
    almacen.guardar("p/exportaciones/a.zip", "application/zip", new byte[] {1});
    proveedores.dependencias.put(
        proveedor.id(), new DependenciasDeProveedor(0, true, List.of("p/exportaciones/a.zip")));

    assertThrows(
        ProveedorConIngestaEnCursoException.class, () -> eliminar.ejecutar(proveedor.id()));

    assertTrue(proveedores.eliminados.isEmpty());
    assertTrue(almacen.objetos.containsKey("p/exportaciones/a.zip"));
  }

  @Test
  void unoQueNoExisteEsNoEncontrado() {
    assertThrows(ProveedorNoEncontradoException.class, () -> eliminar.ejecutar(UUID.randomUUID()));
  }
}
