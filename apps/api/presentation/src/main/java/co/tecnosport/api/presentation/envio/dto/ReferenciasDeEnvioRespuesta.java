package co.tecnosport.api.presentation.envio.dto;

import java.util.List;

/**
 * La pantalla de referencias de envío del panel ({@code adr/0071}): las medidas de la bolsa —nulas
 * si nadie las ha fijado— y cada categoría hoja de ropa, calzado y bolsos con su peso.
 */
public record ReferenciasDeEnvioRespuesta(
    MedidasDeReferenciaRespuesta medidas, List<CategoriaConPesoRespuesta> categorias) {

  /**
   * Una categoría hoja. {@code rama} es el nombre de la madre ("Dama"), nulo si cuelga directo de
   * la línea; {@code pesoGramos} nulo es "sin promedio", y esa categoría no se cotiza.
   */
  public record CategoriaConPesoRespuesta(
      String categoriaId, String nombre, String rama, String linea, Integer pesoGramos) {}
}
