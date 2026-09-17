-- El rótulo de la guía, cuando la plataforma lo devolvió. Nullable y no es un descuido: dos guías
-- de Servientrega emitidas por el mismo camino, una trajo label_url y la otra no lo trajo nunca,
-- ni con el envío ya entregado (§6.7). Qué lo decide sigue sin saberse.
alter table guia_envio add column url_etiqueta text;

comment on column guia_envio.url_etiqueta is
    'El rotulo que devolvio la plataforma. Vacio en las guias tecleadas a mano y en algunas '
    'emitidas: no esta garantizado (docs/13 6.7).';
