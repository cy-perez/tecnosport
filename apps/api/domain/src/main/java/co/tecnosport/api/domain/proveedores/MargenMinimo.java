package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo menos que el precio de contado le tiene que ganar al costo del proveedor para que algo se
 * apruebe y se siga vendiendo. Con menos no se vende: la aprobación lo rechaza, y la renovación de
 * un anuncio o la lista de tecnología agotan la variante hasta que el margen vuelva.
 *
 * <pre>
 * ganancia = contado − costo
 * alcanza  ⇔ ganancia ≥ gananciaMinima
 * </pre>
 *
 * <h2>Depende de la línea</h2>
 *
 * <ul>
 *   <li><b>Tecnología</b> ({@link #deTecnologia}): una ganancia entre {@code
 *       PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA} (50.000) y {@code
 *       PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA} (400.000), en pesos y sin porcentaje. Decisión del
 *       negocio del 29 de octubre de 2026 (ADR-0101): el 10 % del costo que se exigía desde el 27
 *       pedía 190.000 a un equipo de 1.900.000 que se quería vender con 50.000. El mínimo es la
 *       regla 18 de la skill de listas —{@code se_ofrece} en {@code
 *       .claude/skills/listas-de-proveedor/scripts/margen.py}—; <b>si cambia allá, cambia aquí</b>:
 *       la skill decide qué se propone, y esto, qué se aprueba. El máximo es el tope de la
 *       sugerencia ({@link SugerenciaDePrecioTecnologia}) y solo lo exige la aprobación: un costo
 *       que baja no agota nada por ganar de más.
 *   <li><b>Ropa, calzado y bolsos</b> ({@link #dePrendas}): la ganancia mínima de su sugerencia,
 *       {@link TopesDeGanancia#minima()} ({@code PROVEEDORES_GANANCIA_MINIMA}), en pesos. Sin
 *       máximo al aprobar.
 * </ul>
 *
 * <p>El precio que se mide es el <b>de contado</b> (ADR-0095): lo que el negocio recibe neto. El
 * publicado lleva encima la comisión de la pasarela, que no es ganancia.
 *
 * <p>La revisión adversarial del 27 de octubre de 2026 encontró que la API no aplicaba ningún
 * mínimo: la aprobación de prendas y tenis aceptaba cualquier precio mayor que cero, la de
 * tecnología cualquiera por encima del costo, y un costo nuevo del proveedor solo dejaba una nota.
 */
public final class MargenMinimo {

  private final Dinero minima;

  /** Nula en las prendas: no tienen techo al aprobar. */
  private final Dinero maxima;

  private MargenMinimo(Dinero minima, Dinero maxima) {
    this.minima = minima;
    this.maxima = maxima;
  }

  /**
   * @param gananciaMinima lo menos que el contado le gana al costo ({@code
   *     PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA})
   * @param gananciaMaxima lo más que puede ganarle al aprobar: la de {@link
   *     SugerenciaDePrecioTecnologia}
   */
  public static MargenMinimo deTecnologia(Dinero gananciaMinima, Dinero gananciaMaxima) {
    Objects.requireNonNull(gananciaMinima, "La ganancia mínima de tecnología no puede ser nula.");
    Objects.requireNonNull(gananciaMaxima, "La ganancia máxima de tecnología no puede ser nula.");
    if (gananciaMinima.valor().compareTo(gananciaMaxima.valor()) > 0) {
      throw new IllegalArgumentException(
          "La ganancia mínima de tecnología no puede pasar de la máxima.");
    }
    return new MargenMinimo(gananciaMinima, gananciaMaxima);
  }

  /**
   * @param gananciaMinima la de {@link TopesDeGanancia#minima()}
   */
  public static MargenMinimo dePrendas(Dinero gananciaMinima) {
    Objects.requireNonNull(gananciaMinima, "La ganancia mínima de las prendas no puede ser nula.");
    return new MargenMinimo(gananciaMinima, null);
  }

  /** Lo menos que hay que ganarle a ese costo, en pesos: no depende del costo. */
  public BigDecimal gananciaMinima(Dinero costo) {
    Objects.requireNonNull(costo, "El costo no puede ser nulo.");
    return minima.valor();
  }

  /**
   * Si este precio de contado le deja al negocio por lo menos la ganancia mínima sobre el costo.
   */
  public boolean alcanza(Dinero precioContado, Dinero costo) {
    Objects.requireNonNull(precioContado, "El precio de contado no puede ser nulo.");
    // Sin dividir: el costo puede ser cero en un dato mal escrito.
    BigDecimal ganancia = ganancia(precioContado, costo);
    return ganancia.compareTo(gananciaMinima(costo)) >= 0;
  }

  /**
   * Lo mismo, pero lanza, y en tecnología exige además el techo: lo usan las aprobaciones.
   *
   * @param que el producto o la configuración, para que quien aprueba sepa cuál
   */
  public void exigir(Dinero precioContado, Dinero costo, String que) {
    if (!alcanza(precioContado, costo)) {
      throw new PrecioSinMargenMinimoException(que, precioContado, costo, minima);
    }
    if (maxima != null && ganancia(precioContado, costo).compareTo(maxima.valor()) > 0) {
      throw new PrecioSobreGananciaMaximaException(que, precioContado, costo, maxima);
    }
  }

  private static BigDecimal ganancia(Dinero precioContado, Dinero costo) {
    return precioContado.valor().subtract(Objects.requireNonNull(costo).valor());
  }
}
