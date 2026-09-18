package co.tecnosport.api.application.catalogo;

import java.math.BigDecimal;

/**
 * El negocio no es responsable de IVA y la variante que se intenta dar de alta declara una tasa
 * distinta de cero.
 *
 * <p>No es una validación de formato —{@code Variante} ya rechaza una tasa fuera de 0..1—, es una
 * condición del negocio que vive en configuración: el literal a del art. 1.3.1.15.2 del Decreto
 * 1625 de 2016 prohíbe a un no responsable adicionar al precio suma alguna por concepto de IVA, y
 * hacerlo lo obliga a cumplir íntegramente el régimen de los responsables. Un dedo que resbala en
 * el panel no puede tener esa consecuencia.
 */
public final class TasaIvaNoPermitidaException extends RuntimeException {

  public TasaIvaNoPermitidaException(BigDecimal tasaIva) {
    super(
        "El negocio no es responsable de IVA, así que una variante no puede declarar la tasa "
            + tasaIva
            + ".");
  }
}
