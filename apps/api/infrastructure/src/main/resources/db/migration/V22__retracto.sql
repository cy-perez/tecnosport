-- Solicitudes de retracto (Ley 1480 de 2011, art. 47). El comprador lo ejerce por correo o
-- WhatsApp, que es el canal que los términos publicados prometen: esta tabla deja constancia de
-- ese acto, no lo sustituye.
create table solicitud_retracto (
    id uuid primary key,
    -- FK sí, a diferencia de autorizacion_datos: aquí el pedido tiene que existir. Una solicitud
    -- de retracto sobre un pedido inexistente no es un dato de auditoría que valga la pena
    -- conservar, es un error.
    pedido_id uuid not null references pedido (id),
    radicada_en timestamptz not null,
    -- Siempre una persona del negocio ("admin:<id>"), nunca el comprador.
    radicada_por varchar(120) not null,
    -- Opcional porque el artículo 47 concede el retracto sin necesidad de justificar la decisión.
    motivo text,
    -- Foto de lo que se sabía el día de radicar, congelada a propósito: el calendario de festivos
    -- puede cargarse después y cambiaría un veredicto ya usado para decidir. INDETERMINADO existe
    -- porque sin festivos cargados no se puede afirmar que un plazo venció.
    verdicto_plazo varchar(20) not null,
    estado varchar(30) not null,
    -- Desde aquí corre el plazo de reintegro de quince días calendario contra el negocio.
    producto_recibido_en timestamptz,
    -- El reembolso vive en esta misma fila y no en tabla propia: no tiene ciclo de vida
    -- independiente y así un reembolso sin solicitud es imposible de escribir.
    reembolso_monto numeric(14, 2),
    reembolso_medio varchar(30),
    reembolso_comprobante varchar(120),
    reembolso_registrado_en timestamptz,
    reembolso_registrado_por varchar(120),

    -- Las dos invariantes del agregado, también aquí: una fila que las rompa no se puede
    -- rehidratar sin que el dominio reviente, y prefiero que reviente al escribir.
    constraint ck_retracto_recibido check (
        estado <> 'PRODUCTO_RECIBIDO' or producto_recibido_en is not null
    ),
    constraint ck_retracto_reembolsada check (
        estado <> 'REEMBOLSADA' or (
            reembolso_monto is not null
            and reembolso_medio is not null
            and reembolso_registrado_en is not null
            and reembolso_registrado_por is not null
        )
    )
);

-- La consulta del panel es siempre "qué retractos tiene este pedido".
create index ix_solicitud_retracto_pedido on solicitud_retracto (pedido_id, radicada_en desc);

-- Y la de operación: lo que falta por cerrar, lo más viejo arriba — mismo criterio que el recaudo
-- pendiente, porque un reintegro sin hacer es un plazo legal corriendo.
create index ix_solicitud_retracto_abiertas on solicitud_retracto (estado, radicada_en);
