package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Las referencias de envío en memoria, con un contador para saber si alguien fue a leerlas. */
final class RepositorioReferenciasDeEnvioFalso implements RepositorioReferenciasDeEnvio {

  private MedidasDeReferencia medidas;
  private final Map<UUID, Integer> pesos = new LinkedHashMap<>();
  private int lecturas;

  RepositorioReferenciasDeEnvioFalso conMedidas(int largoCm, int anchoCm, int altoCm) {
    medidas = new MedidasDeReferencia(largoCm, anchoCm, altoCm);
    return this;
  }

  RepositorioReferenciasDeEnvioFalso conPeso(UUID categoriaId, int pesoGramos) {
    pesos.put(categoriaId, pesoGramos);
    return this;
  }

  int lecturas() {
    return lecturas;
  }

  Optional<Integer> pesoDe(UUID categoriaId) {
    return Optional.ofNullable(pesos.get(categoriaId));
  }

  @Override
  public Optional<MedidasDeReferencia> medidas() {
    lecturas++;
    return Optional.ofNullable(medidas);
  }

  @Override
  public void guardarMedidas(MedidasDeReferencia medidas) {
    this.medidas = medidas;
  }

  @Override
  public List<PesoDeReferencia> pesos() {
    lecturas++;
    List<PesoDeReferencia> todos = new ArrayList<>();
    pesos.forEach((categoria, gramos) -> todos.add(new PesoDeReferencia(categoria, gramos)));
    return todos;
  }

  @Override
  public void guardarPeso(PesoDeReferencia peso) {
    pesos.put(peso.categoriaId(), peso.pesoGramos());
  }

  @Override
  public void quitarPeso(UUID categoriaId) {
    pesos.remove(categoriaId);
  }
}
