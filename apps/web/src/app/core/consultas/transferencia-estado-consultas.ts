import {
  EnvironmentProviders,
  inject,
  InjectionToken,
  makeStateKey,
  provideEnvironmentInitializer,
  TransferState,
} from '@angular/core';
import {
  dehydrate,
  DehydratedState,
  hydrate,
  QueryClient,
} from '@tanstack/angular-query-experimental';

/**
 * Un `QueryClient` por aplicación, no uno por módulo.
 *
 * `provideTanStackQuery(new QueryClient())` construía el cliente una sola vez, al evaluar
 * `app.config.ts`, y ese único objeto lo compartían todos los renders del mismo proceso de Node:
 * la caché de un visitante era la caché del siguiente. Para un catálogo público no filtraba nada,
 * pero deshidratar esa caché habría mandado a cada navegador las consultas de todos los renders
 * anteriores. Un token con fábrica `providedIn: 'root'` se resuelve en el inyector raíz de cada
 * aplicación —una por petición en el servidor, una en el navegador—, que es el alcance correcto.
 */
export const CLIENTE_DE_CONSULTAS = new InjectionToken<QueryClient>('CLIENTE_DE_CONSULTAS', {
  providedIn: 'root',
  factory: () => new QueryClient(),
});

/** Pública para la prueba, que cruza los dos lados por el mismo `<script id="ng-state">`. */
export const CLAVE_ESTADO_CONSULTAS = makeStateKey<DehydratedState>('consultas');

/**
 * Servidor: al serializar la página, la caché de consultas viaja en el `TransferState`.
 *
 * `onSerialize` y no `set`: el callback corre cuando Angular serializa, es decir, cuando la
 * aplicación ya está estable y los `resolve` de la ruta terminaron. `dehydrate` solo incluye las
 * consultas que llegaron a `success`; una que falló no viaja, y el navegador la vuelve a pedir.
 */
export function provideEstadoDeConsultasHaciaElNavegador(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const estado = inject(TransferState);
    const cliente = inject(QueryClient);
    estado.onSerialize(CLAVE_ESTADO_CONSULTAS, () => dehydrate(cliente));
  });
}

/**
 * Navegador: antes de la primera navegación, la caché arranca con lo que sirvió el servidor.
 *
 * Es lo que evita que el `resolve` de la ruta vuelva a pedir el catálogo al hidratar: la consulta
 * ya está en caché con el `dataUpdatedAt` del servidor, así que `prefetch` la encuentra fresca
 * —dentro de su `staleTime`— y no sale a la red. Sin esto cada carga pagaba dos veces la misma
 * petición, y con la API en frío la segunda tardaba lo mismo que la primera: 13 a 20 segundos de
 * portada sin productos, medidos en los registros de Cloud Run (`docs/07-infra-gcp.md`).
 *
 * En el servidor el `TransferState` está vacío y esto no hace nada; por eso vive en la
 * configuración común y no solo en la del navegador.
 */
export function provideEstadoDeConsultasDesdeElServidor(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const estado = inject(TransferState);
    const deshidratado = estado.get(CLAVE_ESTADO_CONSULTAS, null);
    if (deshidratado === null) return;
    hydrate(inject(QueryClient), deshidratado);
    // Ya vive en la caché; conservarlo aquí solo duplicaría los datos en memoria.
    estado.remove(CLAVE_ESTADO_CONSULTAS);
  });
}
