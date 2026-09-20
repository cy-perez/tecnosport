package co.tecnosport.api.presentation.catalogo.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.util.List;
import java.util.UUID;

/**
 * {@code id} identifica la variante para el carrito ({@code POST /carritos/{id}/lineas} pide {@code
 * varianteId}) — mismo criterio que {@code MarcaRespuesta.id}, ya público.
 *
 * <p><b>{@code disponible} es un booleano y no un número, desde adr/0050.</b> Fue {@code
 * existencia}, un entero que salía de una columna del catálogo que solo movía el alta de la
 * variante. Ahora sale del libro de movimientos, y se publica como sí/no por dos razones: es lo
 * único que la vitrina usa —pinta "disponible" o "agotado" y habilita el botón—, y el nivel de
 * inventario no es asunto de quien mira la página. Un número, además, ya está viejo entre el render
 * y el clic; el sí/no también, pero el servidor lo vuelve a comprobar al reservar, que es donde
 * importa.
 */
public record VarianteRespuesta(
    UUID id,
    String sku,
    DineroRespuesta precio,
    boolean disponible,
    List<AtributoValorRespuesta> atributos) {}
