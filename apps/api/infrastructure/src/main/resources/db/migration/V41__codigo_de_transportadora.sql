-- El código con el que Skydropx conoce a la transportadora, que no es el nombre que se muestra.
--
-- Medido contra la cuenta el 16 de septiembre de 2026: GET /shipments/tracking exige carrier_name
-- y lo exige en código —`servientrega`, `ninetynineminutes`, `coordinadora`, `interrapidisimo`,
-- `envia`, `dhl`—. Con el nombre visible ("Servientrega") responde 404, y sin el parámetro
-- también. El código no se deriva del nombre: "99 minutes" es `ninetynineminutes`.
--
-- Nullable a propósito, y no es una columna a medio llenar: una guía que teclea una persona en el
-- panel puede no existir en la plataforma —pudo emitirla en la web de la transportadora—, así que
-- lo que decide si se puede consultar su rastreo no es quién la lleva, es si la emitimos nosotros.
-- La llena el adaptador de emisión; las de antes se quedan sin él y la conciliación las salta.
alter table guia_envio add column codigo_transportadora varchar(60);

comment on column guia_envio.codigo_transportadora is
    'Codigo de la transportadora en Skydropx (carrier_name), no el nombre visible. Vacio en las '
    'guias tecleadas a mano: esas no se pueden consultar (adr/0022).';
