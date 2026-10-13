package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuántas tallas más arriba nombra el proveedor la talla que la tienda vende (ADR-0085, 13 de
 * octubre de 2026).
 *
 * <p>Meraki escribe «Ojo Horma reducida pedir una talla más · Tallas M-L-XL-XXL»: la que el
 * proveedor llama M le queda a quien usa S, y la tienda la vende como S. Imperio Wicho escribe
 * «Tallas disponibles 40 a la 45 · Aumentar 2 tallas»: se venden de la 38 a la 43. Y una talla EUR
 * es la nacional más una. En los tres casos la tienda vende una talla y al proveedor hay que
 * pedirle otra; este número es la distancia entre las dos, y con él se sabe qué pedirle por
 * cualquier talla —también por una que quien revisa agregó en el panel—, cosa que una tabla fija no
 * da.
 *
 * <p>En letras se cuenta sobre XS…XXXL; una agrupada, parte por parte («S-M» un paso arriba es
 * «M-L»). En números, sobre la escala de la categoría: de 1 en 1 en calzado, de 2 en 2 en jeans. Lo
 * que no se puede contar —una talla fuera de la escalera de letras, la talla única— no se adivina:
 * queda vacío, y quien aprueba decide.
 */
public record AjusteDeTalla(int tallas) {

  /** Más que esto no es una horma reducida: es un error de lectura. */
  static final int MAXIMO = 4;

  private static final List<String> LETRAS = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");

  public static final AjusteDeTalla NINGUNO = new AjusteDeTalla(0);

  public AjusteDeTalla {
    if (Math.abs(tallas) > MAXIMO) {
      throw new ExcepcionDeDominio(
          "Un ajuste de talla de "
              + tallas
              + " no es una horma: va de -"
              + MAXIMO
              + " a "
              + MAXIMO);
    }
  }

  public boolean ninguno() {
    return tallas == 0;
  }

  /**
   * La talla con que el proveedor nombra la que se vende.
   *
   * @param talla la que se vende, como la escribe la variante
   * @param escala la de la categoría donde se vende; las numéricas solo se cuentan sobre ella
   * @return la del proveedor, la misma si no hay ajuste, o vacía si no se puede contar
   */
  public Optional<String> tallaDelProveedor(String talla, List<String> escala) {
    if (talla == null || talla.isBlank()) {
      return Optional.empty();
    }
    if (ninguno()) {
      return Optional.of(talla.strip());
    }
    String normalizada = Atributo.tallaNormalizada(talla);
    if (normalizada.matches("\\d{1,2}")) {
      return numerica(Integer.parseInt(normalizada), escala);
    }
    List<String> partes = new ArrayList<>();
    for (String parte : normalizada.split("-")) {
      int posicion = LETRAS.indexOf(parte) + tallas;
      if (LETRAS.indexOf(parte) < 0 || posicion < 0 || posicion >= LETRAS.size()) {
        return Optional.empty();
      }
      partes.add(LETRAS.get(posicion));
    }
    return Optional.of(String.join("-", partes));
  }

  /**
   * Sobre las numéricas de la escala, que dicen el paso: de 1 en 1 en calzado, de 2 en 2 en jeans,
   * o irregular. Pasado el borde se sigue con el último paso —la 45 más una es la 46: la del
   * proveedor no tiene por qué estar en la escala de la tienda—. Una talla que no está en la
   * escala, o una escala sin números —un pantalón de dama en 6, 8, 10 bajo la de letras—, no dicen
   * el paso: no se adivina.
   */
  private Optional<String> numerica(int talla, List<String> escala) {
    List<Integer> numeros =
        escala.stream()
            .map(Atributo::tallaNormalizada)
            .filter(t -> t.matches("\\d{1,2}"))
            .map(Integer::parseInt)
            .toList();
    int posicion = numeros.indexOf(talla);
    if (posicion < 0 || numeros.size() < 2) {
      return Optional.empty();
    }
    int destino = posicion + tallas;
    int resultado;
    if (destino >= numeros.size()) {
      int ultimo = numeros.size() - 1;
      resultado =
          numeros.get(ultimo)
              + (destino - ultimo) * (numeros.get(ultimo) - numeros.get(ultimo - 1));
    } else if (destino < 0) {
      resultado = numeros.get(0) + destino * (numeros.get(1) - numeros.get(0));
    } else {
      resultado = numeros.get(destino);
    }
    return resultado > 0 ? Optional.of(String.valueOf(resultado)) : Optional.empty();
  }
}
