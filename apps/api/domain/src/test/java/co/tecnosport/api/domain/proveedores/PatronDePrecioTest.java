package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PatronDePrecioTest {

  /** Los 25 mensajes del anexo, en orden, sacados del documento sin transcribir. */
  private static List<String> anexo() {
    try (InputStream entrada =
        PatronDePrecioTest.class.getResourceAsStream("/proveedores/anexo-mensajes.txt")) {
      String todo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
      return Arrays.stream(todo.split("\n--- Fotos ---\n")).map(String::strip).toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final long[] PRECIOS_DEL_ANEXO = {
    53000, 60000, 62000, 52000, 60000, 60000, 53000, 40000, 43000, 43000, 65000, 60000, 58000,
    55000, 60000, 45000, 45000, 45000, 45000, 40000, 60000, 30000, 45000, 50000, 50000
  };

  /**
   * El primer criterio de aceptación empieza aquí: si esto falla, los borradores salen con otro
   * precio.
   */
  @Test
  void leeElPrecioDeLosVeinticincoMensajesDelAnexo() {
    List<String> mensajes = anexo();
    assertEquals(25, mensajes.size());

    for (int i = 0; i < mensajes.size(); i++) {
      Optional<Dinero> precio = PatronDePrecio.extraer(mensajes.get(i));
      assertEquals(
          Optional.of(Dinero.deCop(PRECIOS_DEL_ANEXO[i])),
          precio,
          "mensaje " + (i + 1) + ":\n" + mensajes.get(i));
    }
  }

  @Test
  void lasCuatroFormasVistasEnLosChats() {
    assertEquals(Optional.of(Dinero.deCop(53000)), PatronDePrecio.extraer("💰 *53.000*"));
    assertEquals(Optional.of(Dinero.deCop(60000)), PatronDePrecio.extraer("💰*60.000*"));
    assertEquals(Optional.of(Dinero.deCop(60000)), PatronDePrecio.extraer("*PRECIO: $60.000💰*"));
    assertEquals(Optional.of(Dinero.deCop(62000)), PatronDePrecio.extraer("💰62.000_"));
    assertEquals(Optional.of(Dinero.deCop(62000)), PatronDePrecio.extraer("Vale 62.000_"));
    assertEquals(Optional.of(Dinero.deCop(45000)), PatronDePrecio.extraer("PRECIO $45000"));
    assertEquals(Optional.of(Dinero.deCop(45000)), PatronDePrecio.extraer("45,000💰"));
  }

  /** Un teléfono, una fecha o un «3 compartimientos» no son precios: no llevan marca. */
  @Test
  void unNumeroSinMarcaNoEsUnPrecio() {
    assertFalse(PatronDePrecio.tienePrecio("*📲 +57 321 9427252*"));
    assertFalse(PatronDePrecio.tienePrecio("3 Compartimientos internos 🪄"));
    assertFalse(PatronDePrecio.tienePrecio("C.C FICUS Lc 1C-14 & 1C-13"));
    assertFalse(PatronDePrecio.tienePrecio("Tallas M-L-XL-XXL"));
    assertFalse(PatronDePrecio.tienePrecio("*NUEVA POLO 1.1🍯*"));
    assertFalse(PatronDePrecio.tienePrecio("https://wa.link/ppx8li"));
    assertFalse(PatronDePrecio.tienePrecio(null));
    assertFalse(PatronDePrecio.tienePrecio("   "));
  }

  /** Con dos precios gana el primero: el «por difusión» va delante del «después de 6». */
  @Test
  void conDosPreciosGanaElPrimero() {
    String texto = "*PRECIO X DIFUSIÓN $50.000💰*\n*DESPUÉS DE 6 $45.000📦*";

    assertEquals(Optional.of(Dinero.deCop(50000)), PatronDePrecio.extraer(texto));
  }

  @Test
  void unaCifraDeMenosDeMilConMarcaNoCuenta() {
    assertTrue(PatronDePrecio.extraer("💰 500").isEmpty());
  }

  /**
   * La Riverah (exportación del 2 de octubre de 2026): tres de sus ocho productos no abrían
   * publicación, y sus fotos terminaban en el producto vecino.
   */
  @Test
  void lasFormasDeLaRiverah() {
    assertEquals(Optional.of(Dinero.deCop(55000)), PatronDePrecio.extraer("🤑🤑*55.000*"));
    assertEquals(
        Optional.of(Dinero.deCop(65000)),
        PatronDePrecio.extraer("       🤑65.000🥳🥳\nPromo 6x360.000"));
    assertEquals(
        Optional.of(Dinero.deCop(55000)),
        PatronDePrecio.extraer("  Camiseta 🎽55.000~~\nPromo 4x200.000"));
    assertEquals(
        Optional.of(Dinero.deCop(125000)), PatronDePrecio.extraer("*PRECIO X MAYOR* $125.000"));
  }

  /**
   * La Riverah, 7 de octubre de 2026: el precio por mayor tachado y el descuento debajo. Vale el de
   * debajo, y el tachado no aparece entre los precios del texto.
   */
  @Test
  void elPrecioTachadoNoCuenta() {
    String texto =
        "~~ PRECIO x MAYOR🤑99.900🥳~~~\nSúper descuento $69.900\n\n*LÍNEA EXCLUSIVA PARA PEDIDO*";

    assertEquals(Optional.of(Dinero.deCop(69900)), PatronDePrecio.extraer(texto));
    assertEquals(List.of(Dinero.deCop(69900)), PatronDePrecio.extraerTodos(texto));
  }

  /**
   * La Riverah, 26 de octubre de 2026: el tachado pegado al emoji. Leído, el jean salía a 65.000 y
   * los conjuntos de niña al precio de antes del descuento.
   */
  @Test
  void elTachadoPegadoAUnEmojiTampocoCuenta() {
    assertEquals(
        List.of(Dinero.deCop(59900)),
        PatronDePrecio.extraerTodos("       🤑~$65.000~🥳🥳\nPromo $59.900\n"));
    assertEquals(
        List.of(Dinero.deCop(68000)),
        PatronDePrecio.extraerTodos(
            "  Jean + Básica 🤑~$75.000~ \n\n*Descuento aniversario $68.000💙*"));
  }

  /** Imperio Wicho, 27 de octubre de 2026: la tienda compra un par, no la docena. */
  @Test
  void elPrecioPorDocenaNoEsElDeUnPar() {
    assertEquals(
        List.of(Dinero.deCop(12000)),
        PatronDePrecio.extraerTodos(
            "📦Docena *$120,000*\n💰*$12,000*\nPedidos 📲wa.me/573114817126"));
    assertEquals(
        Optional.of(Dinero.deCop(20000)),
        PatronDePrecio.extraer("📦Docena *$204,000*\nDetal 💰*$20,000*"));
  }

  /** Sin otro precio, el de la docena es el único que hay y abre la publicación. */
  @Test
  void soloConElPrecioPorDocenaSeQueda() {
    assertEquals(Optional.of(Dinero.deCop(120000)), PatronDePrecio.extraer("📦Docena *$120,000*"));
  }

  /** Una virgulilla suelta delante del precio no es una tachadura: no cierra en ninguna parte. */
  @Test
  void unaVirgulillaSinCierreNoTachaNada() {
    assertEquals(
        Optional.of(Dinero.deCop(110000)),
        PatronDePrecio.extraer(" Precio x mayor🤑~$110.000🥳\nSúper promo 10x $990.000"));
  }

  /** Violeta escribe el precio en miles detrás de 💲, y a veces completo. */
  @Test
  void lasFormasDeVioleta() {
    assertEquals(
        Optional.of(Dinero.deCop(124000)),
        PatronDePrecio.extraer("Chaleco Denim Oversize(Q287)\n💲124\nTalla U"));
    assertEquals(Optional.of(Dinero.deCop(52000)), PatronDePrecio.extraer("💲52  \nTalla S M L"));
    assertEquals(Optional.of(Dinero.deCop(119900)), PatronDePrecio.extraer("💲119900"));
    // 27 de octubre de 2026: el body con «$ 39» y el pantalón con «💲78» en el mismo anuncio.
    assertEquals(
        List.of(Dinero.deCop(39000), Dinero.deCop(78000)),
        PatronDePrecio.extraerTodos(
            "Body Herraje(VY3193)\nTalla S M L \n$ 39\n\nPantalón prenses (VY2598)\n💲78\nTalla S M L XL"));
  }

  /**
   * Las cifras en miles solo valen pegadas a un símbolo de dinero: detrás de la palabra PRECIO
   * vienen cantidades («PRECIO X 12 unidades»), y una tachadura de WhatsApp ({@code ~70.000~}) es
   * el precio viejo, no el cierre del nuevo.
   */
  @Test
  void loQueSigueSinSerUnPrecio() {
    assertFalse(PatronDePrecio.tienePrecio("PRECIO X 12 unidades"));
    assertFalse(PatronDePrecio.tienePrecio("Tallas 6 8 10 12 14"));
    assertFalse(PatronDePrecio.tienePrecio("💲5"));
    assertFalse(PatronDePrecio.tienePrecio("$5"));
    assertFalse(PatronDePrecio.tienePrecio("Nota: Las gorras tiene un valor de 35mil-"));
    assertEquals(
        Optional.of(Dinero.deCop(55000)), PatronDePrecio.extraer("Antes ~70.000~ hoy 💰55.000"));
  }

  /** Un mensaje de Violeta con dos productos: los dos precios, en el orden del texto. */
  @Test
  void extraerTodosDevuelveCadaPrecioEnOrden() {
    String texto =
        """
        *✨NEW COLLECTION ✨*

        Chaqueta Denim corta (Q377)
        💲108
        Talla S M L

        Jean Mom Fit Licrado (Q343)
        💲119900
        Talla S M L XL""";

    assertEquals(
        List.of(Dinero.deCop(108000), Dinero.deCop(119900)), PatronDePrecio.extraerTodos(texto));
    assertEquals(List.of(), PatronDePrecio.extraerTodos("Tallas M-L-XL-XXL"));
    assertEquals(List.of(), PatronDePrecio.extraerTodos(null));
  }

  /**
   * Tenis Al Pie, 16 y 23 de octubre de 2026: el detal va primero, y el que paga la tienda es el
   * por mayor, escrito en miles detrás de 💰. El detal no cuenta ni en la lista.
   */
  @Test
  void elPrecioPorMayorMandaSobreElDeDetal() {
    assertEquals(
        Optional.of(Dinero.deCop(135000)),
        PatronDePrecio.extraer("Detal :$180.000 \nX mayor 💰135"));
    assertEquals(
        List.of(Dinero.deCop(135000)),
        PatronDePrecio.extraerTodos("Detal :$180.000 \nX mayor unidad 💰135"));
    assertEquals(
        Optional.of(Dinero.deCop(140000)),
        PatronDePrecio.extraer("Detal :$190.000 \nPor mayor $140.000"));
  }

  /** Sin el rótulo, dos o tres cifras detrás de 💰 siguen sin ser un precio. */
  @Test
  void sinElRotuloLasCifrasSueltasNoSonMiles() {
    assertEquals(Optional.empty(), PatronDePrecio.extraer("💰135"));
    assertEquals(
        Optional.of(Dinero.deCop(180000)),
        PatronDePrecio.extraer("Detal :$180.000 \nMayorista 💰135"));
  }

  /** «Mayor» como palabra suelta no rotula nada: el rótulo es «x mayor», «por mayor». */
  @Test
  void elRotuloNecesitaElXOElPor() {
    assertEquals(
        Optional.of(Dinero.deCop(60000)), PatronDePrecio.extraer("Para mayor comodidad 💰60.000"));
  }
}
