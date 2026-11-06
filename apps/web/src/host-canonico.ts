/**
 * Adónde mandar una petición que entró por `www.` del dominio público, o `null` si no hay que
 * mandarla a ninguna parte.
 *
 * El dominio con y sin `www` apuntan al mismo servicio de Cloud Run, y los dos respondían 200 con
 * el mismo contenido. El `canonical` ya dice cuál es el bueno, pero un canónico es una sugerencia
 * que el rastreador puede ignorar; un 301 no. Sin él la señal se reparte entre dos dominios que
 * Google trata como sitios distintos.
 *
 * **Solo redirige `www.` del origen público, y nada más.** Cualquier otro host sigue de largo: la
 * URL `*.run.app` del servicio, el subdominio de ensayo y `localhost` tienen que seguir sirviendo
 * por sí mismos —el flujo de despliegue y las sondas entran por ahí—, y una regla del tipo "todo lo
 * que no sea el canónico" los mandaría a producción.
 *
 * Conserva la ruta y la consulta tal cual: un enlace viejo a `www.…/es/productos?linea=x` tiene que
 * llegar a la misma página, no a la portada.
 */
export function destinoSinWww(
  host: string | undefined,
  rutaYConsulta: string,
  origenPublico: string,
): string | null {
  if (!host) return null;
  const publico = new URL(origenPublico);
  // El host llega en minúsculas por convención, no por garantía.
  if (host.toLowerCase() !== `www.${publico.hostname}`) return null;
  // Concatenado y no `new URL(ruta, origen)`: con una ruta como `//otro.com/x` ese constructor
  // cambia de host, y esto sería una redirección abierta hacia cualquier dominio.
  const ruta = rutaYConsulta.startsWith('/') ? rutaYConsulta : `/${rutaYConsulta}`;
  return `${publico.origin}${ruta}`;
}
