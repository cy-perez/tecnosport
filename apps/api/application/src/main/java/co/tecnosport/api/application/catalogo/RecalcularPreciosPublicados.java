package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recalcula el precio publicado de las variantes con las tarifas de la pasarela que hay hoy en la
 * configuración (ADR-0095).
 *
 * <p>Cambiar {@code WOMPI_COMISION_*} no mueve ningún precio por sí solo, y es a propósito: un
 * precio anunciado no debe cambiar sin que una persona lo vea. Por eso son dos operaciones: {@link
 * #previsualizar} enseña qué variantes cambiarían y a cuánto, y {@link #aplicar} lo hace. El de
 * contado no se toca: es lo que el negocio quiere recibir, y no depende de la pasarela.
 *
 * <p>{@link #aplicar} graba una fila por variante: quien llama lo envuelve en una transacción, para
 * que un fallo a mitad no deje el catálogo con la mitad de los precios recalculados.
 */
public final class RecalcularPreciosPublicados {

  private final PreciosDelCatalogo precios;
  private final ComisionDePasarela comision;

  public RecalcularPreciosPublicados(PreciosDelCatalogo precios, ComisionDePasarela comision) {
    this.precios = Objects.requireNonNull(precios);
    this.comision = Objects.requireNonNull(comision);
  }

  /** Las variantes cuyo publicado no es el que dan las tarifas de hoy; vacía si todas lo son. */
  public List<PrecioPorRecalcular> previsualizar() {
    List<PrecioPorRecalcular> pendientes = new ArrayList<>();
    for (PreciosDeVariante variante : precios.listar()) {
      Dinero nuevo = comision.publicadoDesde(variante.contado());
      if (nuevo.valor().compareTo(variante.publicado().valor()) != 0) {
        pendientes.add(
            new PrecioPorRecalcular(
                variante.varianteId(),
                variante.sku(),
                variante.nombreProducto(),
                variante.contado(),
                variante.publicado(),
                nuevo));
      }
    }
    return List.copyOf(pendientes);
  }

  /**
   * Recalcula todas las que {@link #previsualizar} enseña.
   *
   * @return lo que cambió, para que quien llama lo cuente y lo deje en el registro
   */
  public List<PrecioPorRecalcular> aplicar() {
    List<PrecioPorRecalcular> pendientes = previsualizar();
    for (PrecioPorRecalcular pendiente : pendientes) {
      precios.actualizarPublicado(pendiente.varianteId(), pendiente.publicadoNuevo());
    }
    return pendientes;
  }
}
