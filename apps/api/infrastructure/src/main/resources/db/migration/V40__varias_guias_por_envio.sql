-- Un envío, varias guías (adr/0031).
--
-- Ninguna transportadora colombiana de la cuenta admite multipaquete: los siete servicios dicen
-- multi_packages_enabled=false (docs/13-skydropx-capacidades.md §6.3). Con la regla de un bulto por
-- variante, un pedido de dos variantes son dos guías, cada una con su número, su cobro y su propio
-- rastro de eventos. La tabla envio guardaba una sola.
--
-- El envío sigue siendo la unidad de dinero del pedido —la comisión de recaudo es una por pedido—;
-- la guía pasa a ser la unidad de rastreo.
create table guia_envio (
    id uuid primary key,
    envio_id uuid not null references envio (id),
    transportadora varchar(120) not null,
    numero varchar(120) not null,
    costo_envio numeric(14, 2) not null,
    -- Unico global, y no por envío: es lo que RepositorioEnvios.buscarPorGuia ya da por cierto
    -- cuando resuelve un evento del webhook solo con el número. Hasta hoy no lo garantizaba nadie.
    constraint uq_guia_envio_numero unique (numero)
);

create index ix_guia_envio_envio on guia_envio (envio_id);

-- Cada envío existente se convierte en su única guía, con los mismos datos.
insert into guia_envio (id, envio_id, transportadora, numero, costo_envio)
select gen_random_uuid(), id, transportadora, guia, costo_envio
from envio;

-- Los eventos dejan de colgar del envío y pasan a colgar de la guía, que es de quien hablan: la
-- transportadora reporta el movimiento de un paquete, no el de un pedido.
alter table evento_seguimiento add column guia_id uuid references guia_envio (id);

update evento_seguimiento e
set guia_id = g.id
from guia_envio g
where g.envio_id = e.envio_id;

alter table evento_seguimiento alter column guia_id set not null;

alter table evento_seguimiento drop constraint uq_evento_seguimiento_externo;
alter table evento_seguimiento add constraint uq_evento_seguimiento_externo unique (guia_id, id_externo);

drop index ix_evento_seguimiento_envio;
create index ix_evento_seguimiento_guia on evento_seguimiento (guia_id, ocurrio_en);

alter table evento_seguimiento drop column envio_id;

alter table envio drop column transportadora;
alter table envio drop column guia;
alter table envio drop column costo_envio;

comment on table guia_envio is
    'Una guia: un paquete, una transportadora y un cobro. Varias por envio porque ninguna '
    'transportadora colombiana admite multipaquete (adr/0031).';

comment on column guia_envio.costo_envio is
    'Lo que esa guia nos cuesta. El costo del despacho es la suma de las guias del envio: con dos '
    'bultos la cotizacion cambia a multishipment y cobra el doble.';
