package co.tecnosport.api.application.usuario;

/**
 * Entró con Google alguien que no tiene cuenta, y no autorizó el tratamiento de datos. Sin ese sí
 * no se crea la cuenta (Ley 1581 de 2012): el sitio lo manda a «Crear cuenta», donde está la
 * casilla. No es {@code AutorizacionRequerida} a secas porque el comprador no la vio: entró desde
 * «Iniciar sesión», donde no se pide.
 */
public final class CuentaGoogleSinRegistroException extends RuntimeException {

  public CuentaGoogleSinRegistroException() {
    super("No hay cuenta con ese correo: para crearla con Google hace falta autorizar los datos.");
  }
}
