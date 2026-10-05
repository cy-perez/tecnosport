/**
 * Una llave nueva para la cabecera `Idempotency-Key`.
 *
 * `crypto.randomUUID` solo existe en contexto seguro y desde Safari 15.4: en un iPhone con iOS
 * anterior, en un WebView viejo o probando por la IP de la red local sin HTTPS, llamarlo lanzaba y
 * "Confirmar pedido" o "Pagar" no hacían nada. `getRandomValues` lleva mucho más tiempo en todos los
 * navegadores y no exige contexto seguro; el último respaldo no es criptográfico, pero una llave de
 * idempotencia solo tiene que no repetirse entre dos intentos del mismo navegador.
 */
export function nuevaLlaveDeIdempotencia(): string {
  const cripto = typeof crypto === 'undefined' ? undefined : crypto;
  if (cripto && typeof cripto.randomUUID === 'function') {
    return cripto.randomUUID();
  }
  if (cripto && typeof cripto.getRandomValues === 'function') {
    const bytes = cripto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`;
}
