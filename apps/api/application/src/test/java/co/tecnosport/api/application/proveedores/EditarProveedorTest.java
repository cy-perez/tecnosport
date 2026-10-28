package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.AjustesDeIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                    "Meraki",
                    LineaCatalogo.ROPA,
                    "+57 321 942 7252",
                    "Meraki Cúcuta",
                    null,
                    OrdenDePublicacion.FOTOS_PRIMERO));
    assertEquals(Optional.of(creado), proveedores.buscarPorId(creado.id()));
    assertTrue(creado.activo());
    assertEquals(OrdenDePublicacion.FOTOS_PRIMERO, creado.ordenDePublicacion());

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
                    new BigDecimal("1.30"),
                    OrdenDePublicacion.TEXTO_PRIMERO));

    assertEquals("Meraki Cúcuta", editado.nombre());
    assertFalse(editado.activo());
    assertEquals(Optional.of(new BigDecimal("1.30")), editado.factorDeMargen());
    assertEquals(
        OrdenDePublicacion.TEXTO_PRIMERO,
        proveedores.buscarPorId(creado.id()).orElseThrow().ordenDePublicacion());
    assertEquals(
        "M E R A K I", proveedores.buscarPorId(creado.id()).orElseThrow().nombreEnExportacion());
  }

  /** Meraki publica también como «Merakiapartados»: se guarda al crear y se cambia al editar. */
  @Test
  void losOtrosRemitentesSeGuardanAlCrearYSeReemplazanAlEditar() {
    Proveedor creado =
        new CrearProveedor(proveedores)
            .ejecutar(
                new CrearProveedorComando(
                    "Meraki",
                    LineaCatalogo.ROPA,
                    "+57 321 942 7252",
                    "Meraki",
                    null,
                    OrdenDePublicacion.FOTOS_PRIMERO,
                    true,
                    Set.of(),
                    false,
                    AjustesDeIngesta.POR_OMISION,
                    false,
                    List.of("Merakiapartados")));
    Proveedor guardado = proveedores.buscarPorId(creado.id()).orElseThrow();
    assertEquals(List.of("Merakiapartados"), guardado.otrosRemitentes());
    assertTrue(guardado.esRemitente("Merakiapartados"));

    new EditarProveedor(proveedores)
        .ejecutar(
            new EditarProveedorComando(
                creado.id(),
                "Meraki",
                LineaCatalogo.ROPA,
                "+57 321 942 7252",
                "Meraki",
                true,
                false,
                null,
                OrdenDePublicacion.FOTOS_PRIMERO,
                true,
                Set.of(),
                false,
                AjustesDeIngesta.POR_OMISION,
                false,
                List.of("Meraki Apartados 2")));

    Proveedor editado = proveedores.buscarPorId(creado.id()).orElseThrow();
    assertEquals(List.of("Meraki Apartados 2"), editado.otrosRemitentes());
    assertFalse(editado.esRemitente("Merakiapartados"));
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
                        null,
                        OrdenDePublicacion.FOTOS_PRIMERO)));
  }
}
