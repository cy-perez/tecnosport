package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.application.catalogo.VarianteCreada;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.ConfirmacionDeColor;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La paleta oficial de un equipo de tecnología, desde el panel (ADR-0086): los colores en que el
 * fabricante publica el modelo, que la ficha enseña al pie de las fotos.
 *
 * <p>Y como quien compra elige el color en la ficha y lo confirma por WhatsApp, cada color de la
 * paleta tiene que poder elegirse: a cada configuración que ya se vende le nace la variante de los
 * colores que le falten, con el precio y el costo de sus hermanas, sin existencia y con el color
 * por confirmar. Quitar un color de la paleta no borra su variante: puede tener pedidos, y su
 * existencia la sigue moviendo la lista.
 *
 * <p>Quien llama abre la transacción.
 */
public final class DefinirPaletaDeTecnologia {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioVariantesDeProveedor variantesDeProveedor;
  private final AgregarVariante agregarVariante;

  public DefinirPaletaDeTecnologia(
      RepositorioProductos repositorioProductos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioVariantesDeProveedor variantesDeProveedor,
      AgregarVariante agregarVariante) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.variantesDeProveedor = Objects.requireNonNull(variantesDeProveedor);
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
  }

  /**
   * @return cuántas variantes nacieron para completar la paleta
   */
  public int ejecutar(UUID productoId, List<String> colores) {
    Objects.requireNonNull(productoId, "La paleta es de un producto.");
    Producto producto =
        repositorioProductos
            .buscarPorId(productoId)
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(productoId));
    List<VarianteDeProveedor> vinculos = variantesDeProveedor.deProducto(productoId);
    if (vinculos.isEmpty()) {
      throw new ExcepcionDeDominio(
          producto.nombre()
              + " no viene de una lista de tecnología: su color no se confirma con el proveedor.");
    }
    producto.definirPaleta(colores);
    repositorioProductos.actualizar(producto);

    Map<String, List<VarianteDeProveedor>> porConfiguracion = new LinkedHashMap<>();
    for (VarianteDeProveedor v : vinculos) {
      porConfiguracion.computeIfAbsent(v.configuracion(), k -> new ArrayList<>()).add(v);
    }
    Map<UUID, Boolean> colorPorConfirmar = new HashMap<>();
    int nacidas = 0;
    for (Map.Entry<String, List<VarianteDeProveedor>> entrada : porConfiguracion.entrySet()) {
      List<VarianteDeProveedor> hermanas = entrada.getValue();
      Optional<Variante> modelo = hermanaQueSeVende(producto, hermanas);
      if (modelo.isEmpty()) {
        continue;
      }
      VarianteDeProveedor costoDeHoy = hermanas.get(0);
      for (String color : producto.paleta()) {
        if (hermanas.stream().anyMatch(h -> ConfiguracionTecnologia.mismoColor(h.color(), color))) {
          continue;
        }
        VarianteCreada creada =
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    productoId,
                    ConfiguracionTecnologia.skuDeVariante(entrada.getKey(), color),
                    modelo.get().precio().valor().longValueExact(),
                    modelo.get().tasaIva(),
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    atributosCon(modelo.get(), color)));
        variantesDeProveedor.guardar(
            new VarianteDeProveedor(
                creada.variante().id(),
                productoId,
                costoDeHoy.proveedorId(),
                entrada.getKey(),
                color,
                costoDeHoy.costo(),
                costoDeHoy.actualizadoEn()));
        colorPorConfirmar.put(
            creada.variante().id(),
            ConfirmacionDeColor.requiere(producto.paleta(), List.of(), color));
        nacidas++;
      }
    }
    productosDeProveedor.marcarColoresPorConfirmar(colorPorConfirmar);
    return nacidas;
  }

  /**
   * Una variante de la configuración que se pueda copiar: la que trae el precio de venta y los
   * atributos que no son el color. Si ninguna se vende, la configuración no se completa: quien la
   * dio de baja no quiere colores nuevos de ella.
   */
  private static Optional<Variante> hermanaQueSeVende(
      Producto producto, List<VarianteDeProveedor> hermanas) {
    return producto.variantes().stream()
        .filter(Variante::seVende)
        .filter(v -> hermanas.stream().anyMatch(h -> h.varianteId().equals(v.id())))
        .findFirst();
  }

  private static List<ValorAtributoComando> atributosCon(Variante modelo, String color) {
    List<ValorAtributoComando> valores = new ArrayList<>();
    for (ValorAtributo valor : modelo.atributos()) {
      if (valor.atributo().tipo() == TipoAtributo.COLOR) {
        valores.add(new ValorAtributoComando(valor.atributo().id(), color, null));
      } else {
        valores.add(new ValorAtributoComando(valor.atributo().id(), valor.valor(), null));
      }
    }
    return valores;
  }
}
