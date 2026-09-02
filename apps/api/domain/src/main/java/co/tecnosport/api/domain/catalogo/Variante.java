package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La unidad de inventario y de compra. {@code existencia} es un conteo simple en este paso de solo
 * lectura; el agregado {@code Inventario} por movimientos llega en Fase 2, ver
 * docs/09-plan-de-arranque.md.
 */
public final class Variante {

  private final UUID id;
  private final Sku sku;
  private final Dinero precio;
  private final BigDecimal tasaIva;
  private final int existencia;
  private final String codigoBarras;
  private final EstadoVariante estado;
  private final List<ValorAtributo> atributos;
  private final SetRotacion setRotacionPropio;

  public Variante(
      UUID id,
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      int existencia,
      String codigoBarras,
      EstadoVariante estado,
      List<ValorAtributo> atributos,
      SetRotacion setRotacionPropio) {
    this.id = Objects.requireNonNull(id, "El id de la variante no puede ser nulo.");
    this.sku = Objects.requireNonNull(sku, "El SKU de la variante no puede ser nulo.");
    this.precio = Objects.requireNonNull(precio, "El precio de la variante no puede ser nulo.");
    this.tasaIva = validarTasaIva(tasaIva);
    if (existencia < 0) {
      throw new ExcepcionDeDominio("La existencia de una variante no puede ser negativa.");
    }
    this.existencia = existencia;
    this.codigoBarras = codigoBarras == null || codigoBarras.isBlank() ? null : codigoBarras.trim();
    this.estado = Objects.requireNonNull(estado, "El estado de la variante no puede ser nulo.");
    this.atributos = List.copyOf(Objects.requireNonNullElse(atributos, List.of()));
    this.setRotacionPropio = setRotacionPropio;
  }

  public static Variante crear(
      Sku sku,
      Dinero precio,
      BigDecimal tasaIva,
      int existencia,
      String codigoBarras,
      List<ValorAtributo> atributos) {
    return new Variante(
        GeneradorIdentificador.nuevo(),
        sku,
        precio,
        tasaIva,
        existencia,
        codigoBarras,
        EstadoVariante.ACTIVA,
        atributos,
        null);
  }

  private static BigDecimal validarTasaIva(BigDecimal tasaIva) {
    Objects.requireNonNull(tasaIva, "La tasa de IVA no puede ser nula.");
    if (tasaIva.signum() < 0 || tasaIva.compareTo(BigDecimal.ONE) > 0) {
      throw new ExcepcionDeDominio("La tasa de IVA debe estar entre 0 y 1: " + tasaIva);
    }
    return tasaIva;
  }

  public UUID id() {
    return id;
  }

  public Sku sku() {
    return sku;
  }

  public Dinero precio() {
    return precio;
  }

  public BigDecimal tasaIva() {
    return tasaIva;
  }

  public int existencia() {
    return existencia;
  }

  public Optional<String> codigoBarras() {
    return Optional.ofNullable(codigoBarras);
  }

  public EstadoVariante estado() {
    return estado;
  }

  public List<ValorAtributo> atributos() {
    return atributos;
  }

  public Optional<SetRotacion> setRotacionPropio() {
    return Optional.ofNullable(setRotacionPropio);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Variante otra && id.equals(otra.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
