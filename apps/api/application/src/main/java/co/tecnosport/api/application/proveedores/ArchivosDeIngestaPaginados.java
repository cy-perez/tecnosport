package co.tecnosport.api.application.proveedores;

import java.util.List;

public record ArchivosDeIngestaPaginados(
    List<ArchivoDeIngestaEnLista> items, int pagina, int totalPaginas, long totalArchivos) {}
