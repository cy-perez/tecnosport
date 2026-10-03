package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaNoEncontradaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.ImagenProductoInvalidaException;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** De qué color es una foto de la galería, sobre un producto que ya existe. */
class AsignarColorAImagenDeGaleriaTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final AsignarColorAImagenDeGaleria caso = new AsignarColorAImagenDeGaleria(repositorio);

  private final Producto producto =
      Producto.crear(
          "Bodi herraje",
          new Slug("bodi-herraje"),
          "",
          Marca.crear("Genérica"),
          Categoria.crear("Bodis", new Slug("bodis"), LineaCatalogo.ROPA));
  private final Variante vino =
      Variante.crear(
          new Sku("PRV-VINO"), Dinero.deCop(60000), BigDecimal.ZERO, null, null, List.of());
  private final ImagenProducto foto =
      ImagenProducto.crear(
          TipoImagen.GALERIA,
          0,
          List.of(new VarianteDeImagen(1200, "https://x/vino.jpg", 1000)),
          null,
          1200,
          new HashContenido("%064x".formatted(7)),
          "Bodi vino",
          "Burgundy bodysuit");

  AsignarColorAImagenDeGaleriaTest() {
    producto.agregarVariante(vino);
    producto.agregarImagenGaleria(foto);
    repositorio.conProductos(producto);
  }

  @Test
  void cuelgaLaFotoDeLaVarianteDeSuColorYLoGuarda() {
    ImagenProducto marcada =
        caso.ejecutar(new AsignarColorAImagenDeGaleriaComando(producto.id(), foto.id(), vino.id()));

    assertEquals(Optional.of(vino.id()), marcada.varianteId());
    assertEquals(Optional.of(vino.id()), producto.galeria().get(0).varianteId());
    assertEquals(foto.id(), repositorio.varianteGuardada.getKey());
    assertEquals(vino.id(), repositorio.varianteGuardada.getValue());
  }

  @Test
  void sinVarianteLaFotoValeParaTodosLosColores() {
    caso.ejecutar(new AsignarColorAImagenDeGaleriaComando(producto.id(), foto.id(), vino.id()));

    ImagenProducto suelta =
        caso.ejecutar(new AsignarColorAImagenDeGaleriaComando(producto.id(), foto.id(), null));

    assertEquals(Optional.empty(), suelta.varianteId());
    assertNull(repositorio.varianteGuardada.getValue());
  }

  @Test
  void unaVarianteDeOtroProductoNoSeAsigna() {
    assertThrows(
        ImagenProductoInvalidaException.class,
        () ->
            caso.ejecutar(
                new AsignarColorAImagenDeGaleriaComando(
                    producto.id(), foto.id(), UUID.randomUUID())));
  }

  @Test
  void unaFotoQueNoEstaEnLaGaleriaEs404() {
    assertThrows(
        ImagenDeGaleriaNoEncontradaException.class,
        () ->
            caso.ejecutar(
                new AsignarColorAImagenDeGaleriaComando(
                    producto.id(), UUID.randomUUID(), vino.id())));
  }
}
