package co.tecnosport.api.domain.proveedores;

import java.util.Objects;
import java.util.Optional;

/**
 * Lo que el lector ve de un calzado en una foto (ADR-0089, 25 de octubre de 2026). Nació con Tenis
 * Al Pie, cuyo chat no dice ni la marca, ni el modelo, ni para quién es el tenis: todo eso está
 * solo en la foto.
 *
 * <p><b>El modelo de una réplica es una suposición del lector</b>, y los parecidos se confunden: un
 * Jordan 3 y un Jordan 4, un Shox TL y un R4. Un modelo mal nombrado en el título es información
 * falsa para quien compra (Ley 1480, art. 23), así que solo se usa cuando el lector lo da por
 * seguro; si no, el título se queda con la marca, y quien aprueba lo confirma en el panel.
 *
 * @param modelo el modelo que el lector cree reconocer —«Air Force 1», «Samba»—; nulo si no cree
 *     ninguno
 * @param modeloSeguro si el lector lo reconoce sin dudar entre dos parecidos
 * @param genero para quién es, por la horma, los colores y el contexto; nulo si no lo sabe
 * @param tipo qué calzado es
 * @param productoSolo si la foto muestra el calzado solo: sobre una superficie o sostenido en la
 *     mano, sin estar puesto, sin pies ni piernas y sin utilería de otras marcas
 */
public record LecturaDeCalzado(
    String modelo,
    boolean modeloSeguro,
    GeneroDeCalzado genero,
    TipoDeCalzado tipo,
    boolean productoSolo) {

  public LecturaDeCalzado {
    modelo = modelo == null || modelo.isBlank() ? null : modelo.strip();
    modeloSeguro = modeloSeguro && modelo != null;
    tipo = Objects.requireNonNullElse(tipo, TipoDeCalzado.OTRO);
  }

  /** El modelo que va al título: solo el que el lector da por seguro. */
  public Optional<String> modeloParaElTitulo() {
    return modeloSeguro ? Optional.of(modelo) : Optional.empty();
  }

  public Optional<GeneroDeCalzado> generoOpcional() {
    return Optional.ofNullable(genero);
  }
}
