-- Por dónde pidió el comprador que le devolvieran el dinero.
--
-- La Ley 2439 de 2024 no dejó la elección al negocio: la devolución del dinero en comercio
-- electrónico "deberá realizarse a través del medio de pago que prefiera el consumidor". Sin esta
-- columna el sistema podía cumplirlo y no podía demostrarlo — sabía por dónde salió la plata
-- (reintegro.medio) y no por dónde la pidieron. Quien tiene la carga de probar que cumplió es el
-- negocio.
--
-- Nullable, y no por comodidad: el comprador puede retractarse sin decir cómo quiere el dinero, y
-- exigirlo para radicar convertiría un derecho incondicional en un trámite con condiciones.
-- "No lo pidió" y "lo pidió por transferencia" son dos hechos distintos, y un valor por omisión
-- los confundiría.
alter table solicitud_retracto
    add column medio_preferido varchar(30);

-- Los mismos valores que reintegro.medio, por el mismo motivo que allí: comparar la preferencia
-- con lo que se hizo solo tiene sentido si los dos hablan el mismo idioma.
alter table solicitud_retracto
    add constraint ck_solicitud_retracto_medio_preferido
        check (medio_preferido is null
            or medio_preferido in ('WOMPI', 'TRANSFERENCIA_BANCARIA', 'EFECTIVO', 'OTRO'));

comment on column solicitud_retracto.medio_preferido is
    'Medio de pago que el comprador pidió para el reintegro (Ley 2439 de 2024). Nulo si no lo dijo.';
