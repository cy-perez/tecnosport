package co.tecnosport.api.domain.envio;

/**
 * Por dónde entró el dinero que la transportadora recaudó en la puerta.
 *
 * <p>La plataforma ofrece las dos y la cuenta las tiene habilitadas: a créditos, inmediato y sin
 * comisión, gastable en envíos; o a cuenta bancaria, con comisión financiera y disponible los
 * jueves (docs/13 §3). <b>La elección no viaja en el envío</b> —nada en el cuerpo de la guía dice
 * dónde cae el dinero— sino que se hace en el panel de la plataforma al retirar el saldo acumulado.
 * Por eso esto no es una instrucción que el sistema le dé a nadie: es el registro de lo que ya
 * ocurrió, anotado por quien lo vio.
 *
 * <p>La diferencia que sí es del dominio es la comisión: con {@link #CREDITOS} vale cero siempre, y
 * un número distinto significa que quien concilia se equivocó de modalidad o de cifra. Ver {@code
 * adr/0043}.
 */
public enum ModalidadRecaudo {
  CREDITOS,
  BANCO;

  /** Los créditos no cobran comisión: es la única diferencia que el dominio puede comprobar. */
  public boolean admiteComision() {
    return this == BANCO;
  }
}
