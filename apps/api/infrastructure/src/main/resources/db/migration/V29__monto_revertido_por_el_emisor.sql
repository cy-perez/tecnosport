-- Cuánto revirtió el emisor del medio de pago por su cuenta.
--
-- El desenlace REVERTIDO_POR_EL_EMISOR no deja fila en `reintegro`, y es correcto que no la deje: ese
-- dinero volvió por la red de pagos y registrar un pago que no hicimos descuadraría la única pregunta
-- que esa tabla responde. Lo que faltaba era el otro lado de la moneda: como no dejaba constancia,
-- ese dinero tampoco contaba contra lo que el pedido todavía puede devolver, así que un contracargo
-- seguido de un retracto devolvía el total dos veces. Lo levantó una revisión adversarial de los
-- caminos del dinero.
--
-- Anotar el hecho no es inventar la constancia: quien resuelve la reversión sabe cuánto revirtió el
-- emisor porque el emisor se lo dijo. Y de paso queda cubierta la reversión parcial, que el artículo
-- 51 de la Ley 1480 y el Decreto 587 de 2016 contemplan cuando la compra fue de varios productos.
alter table solicitud_reversion
    add column monto_revertido_por_el_emisor numeric(14, 2);

-- Nulo en los otros tres desenlaces, y obligatorio en el que revirtió el emisor. La condición mira
-- `desenlace` y no `estado` porque una solicitud sin resolver no tiene ninguno de los dos.
alter table solicitud_reversion
    add constraint ck_reversion_monto_del_emisor check (
        case
            when desenlace = 'REVERTIDO_POR_EL_EMISOR'
                then monto_revertido_por_el_emisor is not null
                    and monto_revertido_por_el_emisor > 0
            else monto_revertido_por_el_emisor is null
        end
    );

comment on column solicitud_reversion.monto_revertido_por_el_emisor is
    'Cuanto revirtio el emisor del medio de pago por su cuenta. No hay fila en reintegro porque ese '
    'dinero no salio de nuestra caja, pero si cuenta contra lo que el pedido todavia puede devolver '
    '(TopeDeReintegro). Nulo en los otros desenlaces y en las filas anteriores a V29.';
