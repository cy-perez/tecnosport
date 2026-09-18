-- Por dónde entró el recaudo de cada envío contraentrega: créditos de la plataforma (sin comisión,
-- inmediato) o consignación bancaria (con comisión, los jueves). La cuenta tiene las dos, y la
-- elección se hace en el panel de Skydropx al retirar — nada en el cuerpo del envío la lleva, así
-- que esta columna registra lo que pasó y no instruye nada. Ver adr/0043 y docs/13 §3.
--
-- Nullable porque solo tiene valor cuando el recaudo se concilió, igual que comision_recaudo y
-- recaudo_conciliado_en, que llegaron juntas en la V13.
alter table envio
    add column modalidad_recaudo text;

alter table envio
    add constraint chk_envio_modalidad_recaudo
        check (modalidad_recaudo is null or modalidad_recaudo in ('CREDITOS', 'BANCO'));

-- Los créditos no cobran comisión, y eso sí es una invariante: sin esta comprobación una
-- conciliación podría registrar "entró a créditos" con una comisión encima, que es una de las dos
-- cosas mal. La invariante vive además en el dominio; aquí abajo por lo mismo que el check de
-- positividad del paquete, porque hay caminos que escriben JPA directo.
alter table envio
    add constraint chk_envio_creditos_sin_comision
        check (modalidad_recaudo is distinct from 'CREDITOS' or coalesce(comision_recaudo, 0) = 0);
