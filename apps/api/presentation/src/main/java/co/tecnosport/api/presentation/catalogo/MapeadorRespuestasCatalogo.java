package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.catalogo.dto.AtributoValorRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.CategoriaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRotacionRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.MarcaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ProductoRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ResultadoPaginadoRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.RotacionRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VarianteRespuesta;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Domain -> DTO de salida. {@code rotacion} solo se expone si el set está {@code PUBLICADO}
 * (docs/03-api.md: "si el set está incompleto, el campo rotación viene nulo").
 */
@Component
public class MapeadorRespuestasCatalogo {

  public ProductoRespuesta aRespuesta(Producto producto) {
    return new ProductoRespuesta(
        producto.slug().valor(),
        producto.nombre(),
        producto.descripcion(),
        aRespuesta(producto.marca()),
        aRespuesta(producto.categoria()),
        producto.imagenPrincipal().map(this::aRespuesta).orElse(null),
        producto.galeria().stream().map(this::aRespuesta).toList(),
        producto
            .setRotacion()
            .filter(set -> set.estado() == EstadoSetRotacion.PUBLICADO)
            .map(this::aRespuesta)
            .orElse(null),
        producto.variantes().stream().map(this::aRespuesta).toList());
  }

  public ResultadoPaginadoRespuesta<ProductoRespuesta> aRespuesta(
      ResultadoPaginado<Producto> resultado) {
    return new ResultadoPaginadoRespuesta<>(
        resultado.items().stream().map(this::aRespuesta).toList(), resultado.cursorSiguiente());
  }

  /** Listas completas, no paginadas: {@code cursorSiguiente} siempre nulo. */
  public ResultadoPaginadoRespuesta<MarcaRespuesta> aRespuestaDeMarcas(List<Marca> marcas) {
    return new ResultadoPaginadoRespuesta<>(marcas.stream().map(this::aRespuesta).toList(), null);
  }

  public ResultadoPaginadoRespuesta<CategoriaRespuesta> aRespuestaDeCategorias(
      List<Categoria> categorias) {
    return new ResultadoPaginadoRespuesta<>(
        categorias.stream().map(this::aRespuesta).toList(), null);
  }

  public MarcaRespuesta aRespuesta(Marca marca) {
    return new MarcaRespuesta(marca.id(), marca.nombre());
  }

  public CategoriaRespuesta aRespuesta(Categoria categoria) {
    return new CategoriaRespuesta(
        categoria.id(), categoria.nombre(), categoria.slug().valor(), categoria.linea().name());
  }

  private ImagenRespuesta aRespuesta(ImagenProducto imagen) {
    return new ImagenRespuesta(
        imagen.url(),
        imagen.urlWebp(),
        imagen.ancho(),
        imagen.alto(),
        imagen.altEs(),
        imagen.altEn());
  }

  private RotacionRespuesta aRespuesta(SetRotacion setRotacion) {
    List<ImagenRotacionRespuesta> imagenes =
        setRotacion.fotogramas().stream()
            .map(
                f ->
                    new ImagenRotacionRespuesta(
                        f.orden(), f.url(), f.urlWebp(), f.ancho(), f.alto()))
            .toList();
    return new RotacionRespuesta(imagenes.size(), imagenes);
  }

  private VarianteRespuesta aRespuesta(Variante variante) {
    return new VarianteRespuesta(
        variante.id(),
        variante.sku().valor(),
        aRespuesta(variante.precio()),
        variante.existencia(),
        variante.atributos().stream().map(this::aRespuesta).toList());
  }

  private DineroRespuesta aRespuesta(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }

  private AtributoValorRespuesta aRespuesta(ValorAtributo valorAtributo) {
    return new AtributoValorRespuesta(
        valorAtributo.atributo().nombre(), valorAtributo.valor(), valorAtributo.colorHex());
  }
}
