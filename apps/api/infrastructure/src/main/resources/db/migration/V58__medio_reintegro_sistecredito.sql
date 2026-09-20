-- Sistecrédito como medio de reintegro (adr/0048). El valor no significa lo mismo que los otros
-- cuatro y por eso se nombra aparte: en los demás el dinero vuelve al comprador; aquí el comprador
-- nunca pagó —quedó debiendo un crédito— y lo que se deshace es el crédito y el pagaré, a
-- solicitud del comercio en el portal Credinet.
--
-- `reintegro.medio` no tiene restricción de valores: la validación vive en el enum del dominio.
-- `solicitud_retracto.medio_preferido` sí la tiene (V27), y hay que ampliarla o un comprador que
-- pagó con Sistecrédito no podría ni siquiera pedir que le anulen por ahí.
alter table solicitud_retracto
    drop constraint if exists ck_solicitud_retracto_medio_preferido;

alter table solicitud_retracto
    add constraint ck_solicitud_retracto_medio_preferido
        check (medio_preferido is null
            or medio_preferido in
               ('WOMPI', 'SISTECREDITO', 'TRANSFERENCIA_BANCARIA', 'EFECTIVO', 'OTRO'));
