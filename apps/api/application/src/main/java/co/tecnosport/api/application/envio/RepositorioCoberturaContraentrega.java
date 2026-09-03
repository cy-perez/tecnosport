package co.tecnosport.api.application.envio;

import java.util.List;

/**
 * Puerto de la cobertura de contraentrega (docs/11-pagos-y-envios.md: "tabla propia, alimentada por
 * lo que cubre la transportadora con recaudo"). Implementación de producción: JPA con PostgreSQL,
 * cargada a mano por el administrador — sin integración con la transportadora todavía.
 */
public interface RepositorioCoberturaContraentrega {

  boolean estaCubierta(String codigoDaneCiudad);

  void agregar(String codigoDaneCiudad);

  void quitar(String codigoDaneCiudad);

  List<String> listar();
}
