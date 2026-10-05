import { inject } from '@angular/core';
import { injectQuery, keepPreviousData } from '@tanstack/angular-query-experimental';
import { FiltroProductosAdmin, ProductosPaginadosAdmin } from '../domain/producto-admin.model';
import { REPOSITORIO_PRODUCTOS_ADMIN } from '../domain/repositorio-productos-admin.puerto';

export function claveListaProductosAdmin(filtro: FiltroProductosAdmin) {
  return ['admin', 'productos', filtro] as const;
}

/** Paginado por página, no scroll infinito — un panel administrativo, no una vitrina
 * (apps/web/CLAUDE.md: "docs/03-api.md: cursor en el catálogo, página en el panel administrativo"). */
export function usarListarProductosAdmin(filtro: () => FiltroProductosAdmin) {
  const repositorio = inject(REPOSITORIO_PRODUCTOS_ADMIN);

  return injectQuery(() => ({
    queryKey: claveListaProductosAdmin(filtro()),
    queryFn: (): Promise<ProductosPaginadosAdmin> => repositorio.listar(filtro()),
    // Al pasar de página la llave cambia, y sin esto la consulta volvía a `pending`: la plantilla
    // pintaba el esqueleto, el paginador desaparecía con el botón pulsado dentro y el foco caía
    // en `<body>`. Con los datos de la página anterior a la vista mientras llega la nueva, el
    // paginador no se desmonta.
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  }));
}
