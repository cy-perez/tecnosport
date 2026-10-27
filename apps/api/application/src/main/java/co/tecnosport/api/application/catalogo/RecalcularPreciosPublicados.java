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
 * #previsualizar} enseña qué variantes cambiarían y a cuánto, y {@link #aplicar} graba <b>esa
 * lista</b>, la que la persona vio, y no lo que esté desalineado cuando llega la orden. El de
 * contado no se toca: es lo que el negocio quiere recibir, y no depende de la pasarela.
 *
 * <p>Entre ver y confirmar alguien puede cambiar un precio en la ficha, o puede cambiar la tarifa
 * con un despliegue. Por eso cada fila se recalcula con la tarifa de ahora y solo se graba si da lo
 * mismo que se enseñó, y la escritura es condicional al contado y al publicado que se vieron. Lo
 * que no cuadra se omite y se cuenta, para que la persona revise de nuevo.
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
   * Graba el publicado nuevo de las filas revisadas que siguen como se vieron.
   *
   * @param revisadas lo que enseñó {@link #previsualizar} y la persona confirmó
   * @return las grabadas, para el registro, y las omitidas porque cambiaron entretanto
   */
  public RecalculoAplicado aplicar(List<PrecioRevisado> revisadas) {
    Objects.requireNonNull(revisadas, "Falta la lista revisada.");
    List<PrecioRevisado> recalculadas = new ArrayList<>();
    List<PrecioRevisado> omitidas = new ArrayList<>();
    for (PrecioRevisado revisada : revisadas) {
      boolean conLaTarifaDeHoy =
          comision
                  .publicadoDesde(revisada.contado())
                  .valor()
                  .compareTo(revisada.publicadoNuevo().valor())
              == 0;
      if (conLaTarifaDeHoy
          && precios.actualizarPublicado(
              revisada.varianteId(),
              revisada.contado(),
              revisada.publicadoActual(),
              revisada.publicadoNuevo())) {
        recalculadas.add(revisada);
      } else {
        omitidas.add(revisada);
      }
    }
    return new RecalculoAplicado(recalculadas, omitidas);
  }
}
