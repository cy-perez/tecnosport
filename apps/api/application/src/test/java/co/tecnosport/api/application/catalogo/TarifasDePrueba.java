package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;

/** Las tarifas de Wompi leídas el 26 de octubre de 2026 (ADR-0095): 2,65 % + $700 + IVA. */
public final class TarifasDePrueba {

  public static final ComisionDePasarela WOMPI =
      new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("0.19"));

  private TarifasDePrueba() {}
}
