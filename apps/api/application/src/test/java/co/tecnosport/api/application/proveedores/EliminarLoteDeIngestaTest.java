package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarLoteDeIngestaTest {

  private final ApoyoDeIngesta.RepositorioLotesEnMemoria lotes =
      new ApoyoDeIngesta.RepositorioLotesEnMemoria();
  private final ApoyoDeIngesta.AlmacenEnMemoria almacen = new ApoyoDeIngesta.AlmacenEnMemoria();
  private final Set<UUID> publicados = new HashSet<>();
  private final List<UUID> productosBorrados = new ArrayList<>();
  private final EliminacionDeProductos eliminacion =
      productoId -> {
        if (publicados.contains(productoId)) {
          return false;
        }
        productosBorrados.add(productoId);
        return true;
      };
  private final EliminarLoteDeIngesta eliminarLote =
      new EliminarLoteDeIngesta(lotes, eliminacion, almacen);

  private LoteIngesta loteTerminado() {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "proveedores/x/exportacion.zip", ApoyoDeIngesta.AHORA);
    lote.iniciar(ApoyoDeIngesta.AHORA.plusSeconds(1));
    lote.terminar(
        new ResumenIngesta(4, 0, 4, 2, 2, 0, 0, 0, 0), ApoyoDeIngesta.AHORA.plusSeconds(60));
    lotes.guardar(lote);
    return lote;
  }

  @Test
  void borraLosProductosNoPublicadosLosArchivosYElLote() {
    LoteIngesta lote = loteTerminado();
    UUID enBorrador = UUID.randomUUID();
    UUID publicado = UUID.randomUUID();
    publicados.add(publicado);
    almacen.objetos.put("proveedores/x/exportacion.zip", new byte[] {1});
    almacen.objetos.put("proveedores/x/foto.jpg", new byte[] {2});
    lotes.dependencias.put(
        lote.id(),
        new DependenciasDeLote(
            List.of(enBorrador, publicado),
            List.of("proveedores/x/exportacion.zip", "proveedores/x/foto.jpg")));

    LoteEliminado resultado = eliminarLote.ejecutar(lote.id());

    assertEquals(new LoteEliminado(1, 1, 2), resultado);
    assertEquals(List.of(enBorrador), productosBorrados);
    assertTrue(almacen.objetos.isEmpty());
    assertEquals(List.of(lote.id()), lotes.eliminados);
  }

  @Test
  void conElLoteEnCursoNoBorraNada() {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "proveedores/x/exportacion.zip", ApoyoDeIngesta.AHORA);
    lotes.guardar(lote);
    UUID producto = UUID.randomUUID();
    almacen.objetos.put("proveedores/x/exportacion.zip", new byte[] {1});
    lotes.dependencias.put(
        lote.id(),
        new DependenciasDeLote(List.of(producto), List.of("proveedores/x/exportacion.zip")));

    assertThrows(LoteEnCursoException.class, () -> eliminarLote.ejecutar(lote.id()));

    assertTrue(productosBorrados.isEmpty());
    assertFalse(almacen.objetos.isEmpty());
    assertTrue(lotes.eliminados.isEmpty());
  }

  @Test
  void unLoteQueNoExisteFalla() {
    assertThrows(LoteNoEncontradoException.class, () -> eliminarLote.ejecutar(UUID.randomUUID()));
    assertTrue(lotes.eliminados.isEmpty());
  }
}
