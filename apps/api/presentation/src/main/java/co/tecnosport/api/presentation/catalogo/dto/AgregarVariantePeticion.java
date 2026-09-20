package co.tecnosport.api.presentation.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Los cuatro campos del paquete pasan a {@code Integer} el 19 de septiembre de 2026 ({@code
 * adr/0046}): una variante se puede cargar sin medir, y entonces solo se vende con recogida en el
 * punto.
 *
 * <p>Iban como {@code int} a propósito y el motivo era bueno: Jackson 3 no rellena los componentes
 * que falten de un record, así que un cuerpo incompleto moría en 422 antes de llegar al dominio.
 * Con el envoltorio esa red desaparece —un campo ausente llega en nulo, no revienta— y por eso la
 * validación baja aquí, al constructor compacto, que es donde {@code apps/api/CLAUDE.md} dice que
 * tiene que estar cuando el componente es de tipo referencia.
 *
 * <p>La regla es <b>las cuatro o ninguna</b>. Tres medidas y un peso ausente no es "a medio medir":
 * es una carga rota, y dejarla pasar guardaría una fila que revienta al leerse en vez de al
 * escribirse. La misma restricción está en la base ({@code V55}) porque una regla que solo vive en
 * el DTO se salta por cualquier otra puerta.
 */
public record AgregarVariantePeticion(
    UUID productoId,
    String sku,
    long precio,
    BigDecimal tasaIva,
    String codigoBarras,
    int existenciaInicial,
    Integer pesoGramos,
    Integer largoCm,
    Integer anchoCm,
    Integer altoCm,
    List<ValorAtributoPeticion> atributos) {

  public AgregarVariantePeticion {
    int presentes = 0;
    for (Integer medida : new Integer[] {pesoGramos, largoCm, anchoCm, altoCm}) {
      if (medida != null) {
        presentes++;
      }
    }
    if (presentes != 0 && presentes != 4) {
      throw new IllegalArgumentException(
          "El paquete va completo o no va: hay que mandar el peso y las tres medidas, o ninguno de"
              + " los cuatro.");
    }
  }

  /** {@code true} cuando la variante llega medida. */
  public boolean traePaquete() {
    return pesoGramos != null;
  }
}
