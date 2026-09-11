package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import java.util.List;
import java.util.Objects;

/**
 * Alta de una variante nueva para un producto ya existente, con su inventario inicial. Toca dos
 * agregados distintos (`Producto`/`Variante` e `Inventario`) en una sola operación — quien llama
 * (la capa de presentación) tiene que envolver {@link #ejecutar} en una única transacción, mismo
 * criterio que {@code CrearPedido}.
 */
public final class AgregarVariante {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioAtributos repositorioAtributos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public AgregarVariante(
      RepositorioProductos repositorioProductos,
      RepositorioAtributos repositorioAtributos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioAtributos = Objects.requireNonNull(repositorioAtributos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Variante ejecutar(AgregarVarianteComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));

    Sku sku = new Sku(comando.sku());
    if (repositorioProductos.existeVarianteConSku(sku)) {
      throw new SkuYaEnUsoException(sku);
    }

    List<ValorAtributo> atributos = comando.atributos().stream().map(this::aValorAtributo).toList();

    Variante variante =
        Variante.crear(
            sku,
            Dinero.deCop(comando.precio()),
            comando.tasaIva(),
            comando.existenciaInicial(),
            comando.codigoBarras(),
            new Paquete(
                comando.pesoGramos(), comando.largoCm(), comando.anchoCm(), comando.altoCm()),
            atributos);
    producto.agregarVariante(variante);
    repositorioProductos.agregarVariante(producto.id(), variante);

    Inventario inventario = Inventario.crear(variante.id());
    if (comando.existenciaInicial() > 0) {
      inventario.registrarEntrada(
          comando.existenciaInicial(), "Alta inicial de variante", reloj.ahora());
    }
    repositorioInventario.guardar(inventario);

    return variante;
  }

  private ValorAtributo aValorAtributo(ValorAtributoComando comando) {
    Atributo atributo =
        repositorioAtributos
            .buscarPorId(comando.atributoId())
            .orElseThrow(() -> new AtributoNoEncontradoException(comando.atributoId()));
    return comando.colorHex() == null
        ? ValorAtributo.de(atributo, comando.valor())
        : ValorAtributo.deColor(atributo, comando.valor(), comando.colorHex());
  }
}
