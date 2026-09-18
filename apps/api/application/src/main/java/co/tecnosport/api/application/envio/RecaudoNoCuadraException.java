package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Lo que habría que declarar ya vale más que lo que el pedido cobra, así que ese carrito no puede
 * ir contraentrega ({@code adr/0037}).
 *
 * <p>No es un fallo del proveedor ni un error del comprador: es aritmética. La plataforma cobra en
 * la puerta la suma de los valores declarados y exige un mínimo por bulto, así que un carrito de
 * muchas unidades muy baratas declara más de lo que vale —diez cables de 8.000 son 100.000
 * declarados contra 80.000 de mercancía— y ningún flete nacional cierra esa diferencia.
 *
 * <p><strong>Se resuelve no ofreciendo contraentrega, nunca cobrando de más.</strong> La diferencia
 * la vería el comprador en el momento de pagar, con el paquete en la mano y sin haber aceptado
 * nada; discutirla ahí la pierde el negocio aunque tenga la razón, y no la tiene.
 *
 * <p>Por eso la atrapa {@code MetodosDePagoDisponibles} y la traduce en "no hay contraentrega para
 * este carrito", igual que ya hace con {@link ArticuloNoAsegurableException}. Que llegue hasta la
 * emisión significaría que un pedido contraentrega se creó sin pasar por esa puerta.
 */
public final class RecaudoNoCuadraException extends RuntimeException {

  private final transient Dinero declarado;
  private final transient Dinero aRecaudar;

  public RecaudoNoCuadraException(Dinero declarado, Dinero aRecaudar) {
    super(
        "Los bultos declaran "
            + declarado.valor()
            + " y el pedido solo cobra "
            + aRecaudar.valor()
            + ": la transportadora recaudaría más de lo que el comprador debe.");
    this.declarado = declarado;
    this.aRecaudar = aRecaudar;
  }

  public Dinero declarado() {
    return declarado;
  }

  public Dinero aRecaudar() {
    return aRecaudar;
  }
}
