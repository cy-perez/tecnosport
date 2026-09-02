import createClient from 'openapi-fetch';
import type { paths } from './tipos.js';

/** Fábrica, no una URL fija: apps/web decide la base (relativa siempre, ver docs/07-infra-gcp.md). */
export function crearClienteContratos(baseUrl: string) {
  return createClient<paths>({ baseUrl });
}

export type ClienteContratos = ReturnType<typeof crearClienteContratos>;
