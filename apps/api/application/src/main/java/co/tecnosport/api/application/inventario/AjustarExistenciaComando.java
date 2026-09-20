package co.tecnosport.api.application.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * Lo que llega del panel es <b>el conteo físico</b>, no la diferencia contra lo que había
 * (adr/0049).
 *
 * <p>Quien acaba de contar sabe "hay tres"; pedirle "menos dos" le pide además una resta contra un
 * número que tiene que ir a buscar, y un error en esa resta es indistinguible de una pérdida real:
 * las dos cosas llegan como un ajuste negativo con un motivo escrito por una persona. La resta la
 * hace {@link AjustarExistencia}, que sí tiene delante el saldo del libro.
 *
 * <p>El {@code motivo} es obligatorio y no puede venir en blanco. Un libro de movimientos sin
 * motivo es un contador con pasos de más.
 */
public record AjustarExistenciaComando(UUID varianteId, int cantidadContada, String motivo) {

  public AjustarExistenciaComando {
    if (varianteId == null) {
      throw new ExcepcionDeDominio("El id de la variante es obligatorio para ajustar existencia.");
    }
    if (cantidadContada < 0) {
      throw new ExcepcionDeDominio("El conteo de existencia no puede ser negativo.");
    }
    if (motivo == null || motivo.isBlank()) {
      throw new ExcepcionDeDominio("El motivo del ajuste de existencia es obligatorio.");
    }
    motivo = motivo.trim();
  }
}
