package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;

/**
 * Lo que devuelve {@link MedirVariante}. Lleva el {@code producto} porque la respuesta del panel
 * nombra la variante por su producto, y {@code correccion} porque medir por primera vez y enmendar
 * una medida equivocada son el mismo cambio en la base y dos hechos muy distintos en el registro:
 * el segundo dice que en algún momento se cobró un flete con la cifra vieja.
 */
public record ResultadoDeMedicion(Producto producto, Variante variante, boolean correccion) {}
