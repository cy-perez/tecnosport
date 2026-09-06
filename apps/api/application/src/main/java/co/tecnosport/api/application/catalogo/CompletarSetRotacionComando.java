package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.UUID;

public record CompletarSetRotacionComando(UUID setId, List<FotogramaComando> fotogramas) {}
