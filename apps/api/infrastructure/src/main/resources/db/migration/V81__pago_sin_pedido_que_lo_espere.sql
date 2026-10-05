-- Pagos aprobados sin un pedido que los esperara (4 de octubre de 2026).
--
-- Un pago que se aprueba cuando su pedido ya no esta en PAGO_PENDIENTE -otro intento lo pago, o
-- estaba cancelado o fallido- entraba en silencio: el pago quedaba APROBADO, el pedido no se tocaba
-- y el dinero no se devolvia nunca. Desde aqui queda marcado con la fecha en que se detecto, y el
-- panel lo muestra hasta que exista su reintegro (motivo PAGO_SIN_PEDIDO, origen = el pago).

alter table pago add column sin_pedido_que_lo_espere_desde timestamptz;

-- Solo un pago aprobado puede quedar asi: el dominio lo exige, y la base lo repite para que un
-- arreglo a mano no deje un estado que el codigo no sabe leer.
alter table pago add constraint ck_pago_sin_pedido_solo_aprobado
    check (sin_pedido_que_lo_espere_desde is null or estado = 'APROBADO');

create index ix_pago_sin_pedido_que_lo_espere
    on pago (sin_pedido_que_lo_espere_desde)
    where sin_pedido_que_lo_espere_desde is not null;
