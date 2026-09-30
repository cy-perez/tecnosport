package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditarProveedorTest {

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();

  @Test
  void crearGuardaYEditarModifica() {
    Proveedor creado =
        new CrearProveedor(proveedores)
            .ejecutar(
                new CrearProveedorComando(
                    "Meraki", LineaCatalogo.ROPA, "+57 321 942 7252", "Meraki Cúcuta", null));
    assertEquals(Optional.of(creado), proveedores.buscarPorId(creado.id()));
    assertTrue(creado.activo());

    Proveedor editado =
        new EditarProveedor(proveedores)
            .ejecutar(
                new EditarProveedorComando(
                    creado.id(),
                    "Meraki Cúcuta",
                    LineaCatalogo.ROPA,
                    "+57 321 942 7252",
                    "M E R A K I",
                    false,
                    false,
                    new BigDecimal("1.30")));

    assertEquals("Meraki Cúcuta", editado.nombre());
    assertFalse(editado.activo());
    assertEquals(Optional.of(new BigDecimal("1.30")), editado.factorDeMargen());
    assertEquals(
        "M E R A K I", proveedores.buscarPorId(creado.id()).orElseThrow().nombreEnExportacion());
  }

  @Test
  void noSeEditaLoQueNoExiste() {
    assertThrows(
        ProveedorNoEncontradoException.class,
        () ->
            new EditarProveedor(proveedores)
                .ejecutar(
                    new EditarProveedorComando(
                        UUID.randomUUID(),
                        "X",
                        LineaCatalogo.BOLSOS,
                        "+57",
                        "X",
                        true,
                        false,
                        null)));
  }
}
