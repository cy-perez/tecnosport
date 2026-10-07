import { inject } from '@angular/core';
import { injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  REPOSITORIO_CATEGORIAS,
  RepositorioCategorias,
} from '../domain/repositorio-categorias.puerto';
import {
  REPOSITORIO_MARCAS,
  REPOSITORIO_MARCAS_DE_VITRINA,
  RepositorioMarcasDeVitrina,
} from '../domain/repositorio-marcas.puerto';
import { PRECARGA_NO_BLOQUEANTE, sinBloquearLaNavegacion } from '../../../core/consultas/precarga';

/**
 * Categorías y marcas para poblar los filtros. No cambian durante la sesión
 * de un visitante — staleTime largo, sin refetch agresivo.
 */
const STALE_TIME_OPCIONES = 5 * 60_000;

function opcionesCategorias(repositorio: RepositorioCategorias) {
  return {
    queryKey: ['catalogo', 'categorias'] as const,
    queryFn: () => repositorio.listarTodas(),
    staleTime: STALE_TIME_OPCIONES,
  };
}

function opcionesMarcas(repositorio: RepositorioMarcasDeVitrina) {
  return {
    queryKey: ['catalogo', 'marcas'] as const,
    queryFn: () => repositorio.listarDeVitrina(),
    staleTime: STALE_TIME_OPCIONES,
  };
}

/** Las del filtro de la vitrina: solo las marcas publicadas, con las líneas en las que venden. */
export function usarOpcionesFiltro() {
  const repositorioCategorias = inject(REPOSITORIO_CATEGORIAS);
  const repositorioMarcas = inject(REPOSITORIO_MARCAS_DE_VITRINA);

  const categorias = injectQuery(() => opcionesCategorias(repositorioCategorias));
  const marcas = injectQuery(() => opcionesMarcas(repositorioMarcas));

  return { categorias, marcas };
}

/**
 * Las de los desplegables del panel —crear producto, editarlo, revisar un borrador—: las mismas
 * categorías, y <b>todas</b> las marcas, incluida la que todavía no tiene ni un producto, que es
 * justamente la que hace falta para cargarle el primero.
 *
 * <b>Llave propia y no la `['catalogo','marcas']` de la vitrina</b>, por lo mismo que ya razona
 * `usarMarcasAdmin`: aquella la pedían las dos pantallas con adaptadores distintos, así que lo
 * que devolvía dependía de quién hubiera llegado primero. Mientras las dos respuestas tuvieran
 * la misma forma eso era una rareza; desde que la de la vitrina trae las líneas y la del panel no,
 * la carrera que pierde la vitrina deja el filtro leyendo `lineas` de un objeto que no lo tiene.
 */
export function usarOpcionesDeFormulario() {
  const repositorioCategorias = inject(REPOSITORIO_CATEGORIAS);
  const repositorioMarcas = inject(REPOSITORIO_MARCAS);

  const categorias = injectQuery(() => opcionesCategorias(repositorioCategorias));
  const marcas = injectQuery(() => ({
    queryKey: ['admin', 'marcas', 'formulario'] as const,
    queryFn: () => repositorioMarcas.listarTodas(),
    staleTime: STALE_TIME_OPCIONES,
  }));

  return { categorias, marcas };
}

/**
 * Solo las categorías, para quien no necesita las marcas.
 *
 * La portada las usa para saber qué líneas tienen algo detrás, y pedirle también las marcas sería
 * una petición de más en la pantalla más visitada del sitio y en cada arranque en frío. Comparte
 * la llave y las opciones con `usarOpcionesFiltro`, así que las dos pantallas reaprovechan la
 * misma entrada de caché.
 */
export function usarCategorias() {
  const repositorio = inject(REPOSITORIO_CATEGORIAS);
  return injectQuery(() => opcionesCategorias(repositorio));
}

export function precargarCategorias(): Promise<void> {
  const repositorio = inject(REPOSITORIO_CATEGORIAS);
  return sinBloquearLaNavegacion(
    inject(QueryClient).prefetchQuery({
      ...opcionesCategorias(repositorio),
      ...PRECARGA_NO_BLOQUEANTE,
    }),
  );
}

/**
 * Mismo problema que `precargarProductos`: sin esto, las opciones de
 * categoría y marca llegan vacías en el HTML de SSR (la consulta todavía no
 * resolvió cuando Angular serializa) y solo aparecen tras hidratar.
 */
export function precargarOpcionesFiltro(): Promise<void> {
  const repositorioCategorias = inject(REPOSITORIO_CATEGORIAS);
  const repositorioMarcas = inject(REPOSITORIO_MARCAS_DE_VITRINA);
  const queryClient = inject(QueryClient);

  return sinBloquearLaNavegacion(
    Promise.all([
      queryClient.prefetchQuery({
        ...opcionesCategorias(repositorioCategorias),
        ...PRECARGA_NO_BLOQUEANTE,
      }),
      queryClient.prefetchQuery({
        ...opcionesMarcas(repositorioMarcas),
        ...PRECARGA_NO_BLOQUEANTE,
      }),
    ]),
  ).then(() => undefined);
}
