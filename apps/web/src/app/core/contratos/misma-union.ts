/**
 * ¿La unión que el dominio declara a mano y la que el contrato genera tienen exactamente los
 * mismos valores?
 *
 * Existe porque un enum del backend y su unión en el frontend se separaban en silencio. `metodoPago`
 * viajaba como `string` libre en el OpenAPI, así que el cliente generado no restringía nada y las
 * uniones de `domain/` estaban escritas a mano: quitar `ADDI` obligó a tocar cuatro sitios uno por
 * uno y **nada habría fallado si me olvido de alguno** — sobra un valor que la API nunca manda, o
 * falta uno y la pantalla pinta la clave de traducción cruda. Al revés es peor: un método nuevo en
 * el enum no aparece en el checkout y nadie se entera (`docs/09`, deuda 25).
 *
 * Con el enum ya publicado en el OpenAPI, esto es el eslabón que faltaba. Se comprueba en las dos
 * direcciones a propósito: `extends` solo mira una, y la dirección que sobra es justo la que dejó
 * el panel con un `MetodoPago` sin `SISTECREDITO` durante días.
 *
 * Se usa así, en el `infrastructure/` que ya conoce los dos lados —el dominio no importa el
 * contrato, que sería la flecha al revés—:
 *
 * ```ts
 * export const METODOS_DE_PAGO_AL_DIA: MismaUnion<MetodoPago, MetodoPagoDto> = true;
 * ```
 *
 * Cuando dejan de coincidir no compila, y el error nombra los valores que sobran o faltan en vez
 * de decir solo "true no es asignable".
 */
export type MismaUnion<A extends string, B extends string> = [Exclude<A, B>] extends [never]
  ? [Exclude<B, A>] extends [never]
    ? true
    : { faltanEnElDominio: Exclude<B, A> }
  : { elContratoNoTieneEstos: Exclude<A, B> };
