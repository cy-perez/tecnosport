package co.tecnosport.api.presentation.catalogo.dto;

/**
 * Las cuatro medidas, obligatorias. Primitivos y no envoltorios —al revés que {@code
 * AgregarVariantePeticion}, y a propósito—: aquí un campo ausente no es un estado legítimo, y con
 * {@code int} Jackson 3 rechaza el cuerpo incompleto antes de que llegue a ninguna parte.
 *
 * <p>Las cifras las valida {@code Paquete}: mayores que cero, o excepción de dominio. Repetir la
 * comprobación aquí crearía dos definiciones de "medida válida" capaces de divergir.
 */
public record MedirVariantePeticion(int pesoGramos, int largoCm, int anchoCm, int altoCm) {}
