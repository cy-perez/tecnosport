package co.tecnosport.api.domain.proveedores;

import java.util.Objects;
import java.util.Optional;

/**
 * Lo que el lector ve de un calzado en una foto (ADR-0089, 25 de octubre de 2026). Nació con Tenis
 * Al Pie, cuyo chat no dice ni la marca, ni el modelo, ni para quién es el tenis: todo eso está
 * solo en la foto.
 *
 * <p><b>El modelo de una réplica es una suposición del lector</b>, y los parecidos se confunden: un
 * Jordan 3 y un Jordan 4, un Shox TL y un R4. Desde el 26 de octubre de 2026 (ADR-0090) el modelo
 * va al título aunque el lector dude, porque un título sin modelo no le sirve a nadie para buscar
 * el tenis; el que duda deja el borrador con {@code MODELO_POR_CONFIRMAR}, y quien aprueba lo
 * confirma antes de publicar: un modelo mal nombrado es información falsa para quien compra (Ley
 * 1480, art. 23).
 *
 * @param modelo el modelo que el lector cree reconocer —«Air Force 1», «Samba»—; nulo si no cree
 *     ninguno
 * @param modeloSeguro si el lector lo reconoce sin dudar entre dos parecidos
 * @param genero para quién es, por la horma, los colores y el contexto; nulo si no lo sabe
 * @param tipo qué calzado es
 * @param productoSolo si la foto muestra el calzado solo: sobre una superficie o sostenido en la
 *     mano, sin estar puesto, sin pies ni piernas y sin utilería de otras marcas
 * @param marca la marca a la que se parece el tenis, por el logo, la silueta o la forma, aunque no
 *     se lea escrita —el gallo de Le Coq Sportif, el Oversized de Alexander McQueen—; nula si no
 *     reconoce ninguna. Las marcas que <b>se leen</b> siguen en la lectura de la foto
 * @param mismoTenisQue la posición, desde 0, de la primera foto de la tanda que muestra este mismo
 *     tenis en cualquier color; la suya si es la primera. Nula en las lecturas de antes del 26 de
 *     octubre de 2026, que no lo traen: entonces cada foto es su propio tenis
 */
public record LecturaDeCalzado(
    String modelo,
    boolean modeloSeguro,
    GeneroDeCalzado genero,
    TipoDeCalzado tipo,
    boolean productoSolo,
    String marca,
    Integer mismoTenisQue) {

  public LecturaDeCalzado {
    modelo = modelo == null || modelo.isBlank() ? null : modelo.strip();
    modeloSeguro = modeloSeguro && modelo != null;
    tipo = Objects.requireNonNullElse(tipo, TipoDeCalzado.OTRO);
    marca = marca == null || marca.isBlank() ? null : marca.strip();
    mismoTenisQue = mismoTenisQue == null || mismoTenisQue < 0 ? null : mismoTenisQue;
  }

  /** Una lectura de antes del 26 de octubre de 2026: sin la marca por parecido ni el grupo. */
  public LecturaDeCalzado(
      String modelo,
      boolean modeloSeguro,
      GeneroDeCalzado genero,
      TipoDeCalzado tipo,
      boolean productoSolo) {
    this(modelo, modeloSeguro, genero, tipo, productoSolo, null, null);
  }

  /** El modelo que va al título: el que el lector cree reconocer, aunque dude (ADR-0090). */
  public Optional<String> modeloParaElTitulo() {
    return Optional.ofNullable(modelo);
  }

  /** El título lleva un modelo que el lector no dio por seguro: quien aprueba lo confirma. */
  public boolean modeloPorConfirmar() {
    return modelo != null && !modeloSeguro;
  }

  public Optional<GeneroDeCalzado> generoOpcional() {
    return Optional.ofNullable(genero);
  }

  public Optional<String> marcaOpcional() {
    return Optional.ofNullable(marca);
  }

  public Optional<Integer> mismoTenisQueOpcional() {
    return Optional.ofNullable(mismoTenisQue);
  }

  /** Si es un tenis que se puede publicar: un tenis, y visto solo. */
  public boolean seVende() {
    return tipo.seVende() && productoSolo;
  }
}
