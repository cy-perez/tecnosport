package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Lo menos que el precio de contado le tiene que ganar al costo del proveedor para que algo se
 * apruebe y se siga vendiendo. Con menos no se vende: la aprobación lo rechaza, y la renovación de
 * un anuncio o la lista de tecnología agotan la variante hasta que el margen vuelva.
 *
 * <pre>
 * ganancia = contado − costo
 * alcanza  ⇔ ganancia ≥ gananciaMinima(costo)
 * </pre>
 *
 * <h2>Depende de la línea (decisión del negocio del 27 de octubre de 2026)</h2>
 *
 * <ul>
 *   <li><b>Tecnología</b> ({@link #deTecnologia}): {@code min(10 % del costo, tope)}. El 10 % es la
 *       regla 18 de la skill de listas —{@code se_ofrece} en {@code
 *       .claude/skills/listas-de-proveedor/scripts/margen.py}—, y el tope es la ganancia máxima de
 *       {@link SugerenciaDePrecioTecnologia} ({@code PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA}): un
 *       equipo de cinco millones no puede exigir quinientos mil de ganancia mínima cuando la
 *       sugerencia nunca le propone más de la máxima. <b>Si la regla cambia allá, cambia aquí</b>:
 *       la skill decide qué se propone, y esto, qué se aprueba.
 *   <li><b>Ropa, calzado y bolsos</b> ({@link #dePrendas}): la ganancia mínima de su sugerencia,
 *       {@link TopesDeGanancia#minima()} ({@code PROVEEDORES_GANANCIA_MINIMA}), en pesos y sin
 *       porcentaje. Del 27 de octubre en la mañana a esa tarde se les exigió también el 10 %, que
 *       sobre una prenda barata pide menos que la ganancia mínima y sobre una cara, más de lo que
 *       la sugerencia propone.
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

  /** El 10 % de la regla 18 de la skill de listas, solo para tecnología. */
  public static final BigDecimal PORCENTAJE_DE_TECNOLOGIA = new BigDecimal("0.10");

  /** Nulo en las prendas: su mínimo es fijo. */
  private final BigDecimal porcentajeDelCosto;

  /** En tecnología, el tope del porcentaje; en prendas, el mínimo entero. */
  private final Dinero tope;

  private MargenMinimo(BigDecimal porcentajeDelCosto, Dinero tope) {
    this.porcentajeDelCosto = porcentajeDelCosto;
    this.tope = tope;
  }

  /**
   * @param topeDeGanancia la ganancia máxima de {@link SugerenciaDePrecioTecnologia}
   */
  public static MargenMinimo deTecnologia(Dinero topeDeGanancia) {
    Objects.requireNonNull(topeDeGanancia, "El tope de ganancia de tecnología no puede ser nulo.");
    return new MargenMinimo(PORCENTAJE_DE_TECNOLOGIA, topeDeGanancia);
  }

  /**
   * @param gananciaMinima la de {@link TopesDeGanancia#minima()}
   */
  public static MargenMinimo dePrendas(Dinero gananciaMinima) {
    Objects.requireNonNull(gananciaMinima, "La ganancia mínima de las prendas no puede ser nula.");
    return new MargenMinimo(null, gananciaMinima);
  }

  /**
   * Lo menos que hay que ganarle a ese costo, sin redondear: el 10 % de un costo impar tiene
   * centavos, y redondearlo hacia abajo dejaría pasar un peso de menos.
   */
  public BigDecimal gananciaMinima(Dinero costo) {
    Objects.requireNonNull(costo, "El costo no puede ser nulo.");
    if (porcentajeDelCosto == null) {
      return tope.valor();
    }
    return costo.valor().multiply(porcentajeDelCosto).min(tope.valor());
  }

  /**
   * Si este precio de contado le deja al negocio por lo menos la ganancia mínima sobre el costo.
   */
  public boolean alcanza(Dinero precioContado, Dinero costo) {
    Objects.requireNonNull(precioContado, "El precio de contado no puede ser nulo.");
    // Sin dividir: el costo puede ser cero en un dato mal escrito.
    BigDecimal ganancia = precioContado.valor().subtract(Objects.requireNonNull(costo).valor());
    return ganancia.compareTo(gananciaMinima(costo)) >= 0;
  }

  /**
   * Lo mismo, pero lanza: lo usan las aprobaciones.
   *
   * @param que el producto o la configuración, para que quien aprueba sepa cuál
   */
  public void exigir(Dinero precioContado, Dinero costo, String que) {
    if (!alcanza(precioContado, costo)) {
      // Hacia arriba: con un mínimo de 109.999,5 el contado entero tiene que ganar 110.000.
      Dinero minima = Dinero.deCop(gananciaMinima(costo).setScale(0, RoundingMode.CEILING));
      throw new PrecioSinMargenMinimoException(que, precioContado, costo, minima);
    }
  }
}
