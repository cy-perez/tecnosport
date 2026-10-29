package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Un modelo que se aprueba con menos de {@link BorradorTecnologia#MINIMO_DE_FOTOS} fotos porque el
 * fabricante no publica más tomas oficiales (decisión del negocio del 28/10/2026: los AirPods Pro
 * 3, con dos). La declara la persona en la skill de fotos, con su motivo, y viaja con el borrador.
 *
 * <p>Solo baja el mínimo, nunca lo sube ni lo anula: para exigir más fotos se cambia la regla, no
 * un modelo, y un modelo nuevo sin ninguna foto no se publica.
 *
 * @param minimo las fotos que le bastan, de 1 a {@code MINIMO_DE_FOTOS - 1}
 * @param motivo por qué no hay más, tal como lo escribió la persona
 */
public record ExcepcionDeFotos(int minimo, String motivo) {

  public ExcepcionDeFotos {
    if (minimo < 1 || minimo >= BorradorTecnologia.MINIMO_DE_FOTOS) {
      throw new ExcepcionDeDominio(
          "La excepción de fotos va de 1 a "
              + (BorradorTecnologia.MINIMO_DE_FOTOS - 1)
              + " fotos; llegó "
              + minimo
              + ".");
    }
    if (motivo == null || motivo.isBlank()) {
      throw new ExcepcionDeDominio("Una excepción de fotos dice por qué no hay más.");
    }
    motivo = motivo.strip();
  }
}
