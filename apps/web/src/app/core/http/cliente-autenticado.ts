import { crearClienteContratos } from '@tecnosport/contratos';
import { SesionStore } from '../autenticacion/sesion.store';

/**
 * Envuelve {@link crearClienteContratos} con el token de acceso — para las rutas de
 * `/api/v1/admin/**` y cualquier otra que exija sesión. Middleware real de `openapi-fetch` 0.17
 * (`cliente.use(...)`, firma verificada contra `node_modules/openapi-fetch/src/index.js`, no
 * adivinada): `onRequest` agrega `Authorization`; `onResponse` con 401 intenta un refresco
 * silencioso una vez y reintenta con el token nuevo. Si el refresco falla, deja pasar el 401 tal
 * cual — no hay redirección automática aquí, el guardia de rutas ya protege la navegación, y esta
 * capa no sabe en qué pantalla está.
 *
 * <p>El reintento clona la petición en {@code onRequest}, no en {@code onResponse}: el código
 * fuente de `openapi-fetch` (`src/index.js`) hace {@code fetch(request, ...)} con esa misma
 * variable antes de llamar a {@code onResponse} con ella — para cuando el 401 llega, el cuerpo de
 * esa petición original ya se consumió, y clonarla ahí fallaría en cualquier POST con cuerpo
 * (`despacho`, `recaudo`, etc.). Clonar antes de que se use es lo único seguro.
 */
export function crearClienteAutenticado(baseUrl: string, sesionStore: SesionStore) {
  const cliente = crearClienteContratos(baseUrl);
  const copiasParaReintentar = new WeakMap<Request, Request>();

  cliente.use({
    onRequest({ request }) {
      const token = sesionStore.sesion()?.accessToken;
      if (token) {
        request.headers.set('Authorization', `Bearer ${token}`);
      }
      copiasParaReintentar.set(request, request.clone());
      return request;
    },
    async onResponse({ request, response }) {
      if (response.status !== 401) {
        return response;
      }
      const copia = copiasParaReintentar.get(request);
      if (!copia) {
        return response;
      }
      try {
        await sesionStore.intentarRefrescar();
      } catch {
        return response;
      }
      const tokenNuevo = sesionStore.sesion()?.accessToken;
      if (!tokenNuevo) {
        return response;
      }
      copia.headers.set('Authorization', `Bearer ${tokenNuevo}`);
      return fetch(copia);
    },
  });

  return cliente;
}
