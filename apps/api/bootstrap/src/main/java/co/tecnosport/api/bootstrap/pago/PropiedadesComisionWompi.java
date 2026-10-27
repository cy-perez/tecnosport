package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code WOMPI_COMISION_VARIABLE}, {@code WOMPI_COMISION_FIJA} y {@code WOMPI_COMISION_IVA}: la
 * tarifa del plan de Wompi, con la que se calcula el precio publicado desde el de contado
 * (ADR-0095). Son configuración y no constantes porque Wompi cambia de plan; cambiarlas no mueve
 * ningún precio por sí solo —eso lo hace el recálculo del panel—.
 *
 * <p>Se valida al arrancar, y con el nombre de la propiedad: un {@code 2.65} escrito como
 * porcentaje y no como fracción dejaría cada precio del catálogo en el triple, y eso tiene que
 * impedir arrancar, no aparecer en la vitrina.
 *
 * @param variable la parte proporcional, en fracción: {@code 0.0265} es el 2,65 %
 * @param fija el cargo fijo por transacción, en pesos y sin IVA
 * @param iva el IVA que Wompi cobra sobre su comisión, en fracción
 */
@ConfigurationProperties(prefix = "tecnosport.wompi.comision")
public record PropiedadesComisionWompi(BigDecimal variable, Long fija, BigDecimal iva) {

  public PropiedadesComisionWompi {
    exigir(variable, "tecnosport.wompi.comision.variable", "WOMPI_COMISION_VARIABLE");
    exigir(fija, "tecnosport.wompi.comision.fija", "WOMPI_COMISION_FIJA");
    exigir(iva, "tecnosport.wompi.comision.iva", "WOMPI_COMISION_IVA");
    if (variable.signum() < 0 || variable.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalStateException(
          "tecnosport.wompi.comision.variable (WOMPI_COMISION_VARIABLE) es una fracción entre 0 y"
              + " 1 —0.0265 es el 2,65 %—, y vale "
              + variable.toPlainString()
              + ".");
    }
    if (fija < 0) {
      throw new IllegalStateException(
          "tecnosport.wompi.comision.fija (WOMPI_COMISION_FIJA) no puede ser negativa: "
              + fija
              + ".");
    }
    if (iva.signum() < 0 || iva.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalStateException(
          "tecnosport.wompi.comision.iva (WOMPI_COMISION_IVA) es una fracción entre 0 y 1 —0.19"
              + " es el 19 %—, y vale "
              + iva.toPlainString()
              + ".");
    }
    try {
      comoComision(variable, fija, iva);
    } catch (ExcepcionDeDominio e) {
      throw new IllegalStateException("tecnosport.wompi.comision.*: " + e.getMessage(), e);
    }
  }

  /** La tarifa como objeto de dominio, la que reciben los casos de uso. */
  public ComisionDePasarela comoComision() {
    return comoComision(variable, fija, iva);
  }

  private static ComisionDePasarela comoComision(BigDecimal variable, long fija, BigDecimal iva) {
    return new ComisionDePasarela(variable, Dinero.deCop(fija), iva);
  }

  private static void exigir(Object valor, String propiedad, String variable) {
    if (valor == null) {
      throw new IllegalStateException(
          propiedad + " no está configurado: falta la variable de entorno " + variable + ".");
    }
  }
}
