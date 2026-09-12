package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.VerificadorFirmaEnvio;
import org.springframework.stereotype.Component;

/**
 * Rechaza todo, y es la respuesta correcta mientras el dato no exista.
 *
 * <p>Lo que falta: el nombre exacto de la cabecera y el algoritmo. La única pista es de una fuente
 * no oficial —{@code authorization: HMAC {firma}}, HMAC SHA-512 sobre el cuerpo— y no se ha podido
 * comprobar contra un evento real porque la cuenta de sandbox no tiene créditos para emitir una
 * guía. Ver docs/13-skydropx-capacidades.md, sección 6.
 *
 * <p>Escribir el HMAC de memoria compilaría, pasaría unas pruebas que usaran ese mismo algoritmo
 * inventado, y en producción haría una de dos cosas: rechazar todos los eventos —y el seguimiento
 * no se movería nunca— o, si alguien "arreglara" el rechazo relajando la verificación, aceptar
 * cualquier JSON de cualquiera. La segunda es peor: este endpoint es público y con él se marca un
 * pedido como entregado.
 *
 * <p>Rechazar mientras tanto tiene un costo real y conocido: el seguimiento automático no funciona.
 * Eso lo cubre la conciliación programada, que no depende de la firma.
 */
@Component
final class VerificadorFirmaEnvioPendiente implements VerificadorFirmaEnvio {

  @Override
  public boolean esValida(String cuerpoCrudo, String firma) {
    return false;
  }
}
