package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Con menos de un 10 % de margen sobre el costo del proveedor no se vende (decisión del negocio del
 * 15 de octubre de 2026; el umbral subió del 5 % al 10 % el 24 de octubre). Es la regla 18 de la
 * skill de listas —{@code MARGEN_MINIMO} en {@code .claude/skills/listas-de-proveedor/scripts/
 * margen.py}— con la misma fórmula: {@code (precio − costo) / costo ≥ 0,10}. <b>Si el número cambia
 * allá, cambia aquí</b>: la skill decide qué se propone, y esto, qué se aprueba y qué se sigue
 * vendiendo cuando el costo sube.
 *
 * <p>El precio que se mide es el <b>de contado</b> (ADR-0095): lo que el negocio recibe neto. El
 * publicado lleva encima la comisión de la pasarela, que no es ganancia.
 *
 * <p>La revisión adversarial del 27 de octubre de 2026 encontró que la API no la aplicaba en
 * ninguna parte: la aprobación de prendas y tenis aceptaba cualquier precio mayor que cero, la de
 * tecnología cualquiera por encima del costo, y un costo nuevo del proveedor solo dejaba una nota.
 */
public final class MargenMinimo {

  /** El 10 % de la regla 18. */
  public static final BigDecimal PORCENTAJE = new BigDecimal("0.10");

  private MargenMinimo() {}

  /** Si este precio de contado le deja al negocio por lo menos el margen mínimo sobre el costo. */
  public static boolean alcanza(Dinero precioContado, Dinero costo) {
    Objects.requireNonNull(precioContado, "El precio de contado no puede ser nulo.");
    Objects.requireNonNull(costo, "El costo no puede ser nulo.");
    // precio − costo ≥ 0,10 × costo, sin dividir: el costo puede ser cero en un dato mal escrito.
    BigDecimal ganancia = precioContado.valor().subtract(costo.valor());
    return ganancia.compareTo(costo.valor().multiply(PORCENTAJE)) >= 0;
  }

  /**
   * Lo mismo, pero lanza: lo usan las aprobaciones.
   *
   * @param que el producto o la configuración, para que quien aprueba sepa cuál
   */
  public static void exigir(Dinero precioContado, Dinero costo, String que) {
    if (!alcanza(precioContado, costo)) {
      throw new PrecioSinMargenMinimoException(que, precioContado, costo);
    }
  }
}
