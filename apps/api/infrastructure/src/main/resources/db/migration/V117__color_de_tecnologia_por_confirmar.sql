-- El color de un equipo de tecnologia se confirma con el proveedor antes de venderse (ADR-0086,
-- 24 de octubre de 2026). La lista casi nunca dice colores, y la paleta oficial del modelo dice que
-- colores existen, no cuales tiene hoy el proveedor.

-- La paleta oficial del modelo, uno por linea: la ficha la ensena al pie de las fotos. Hasta hoy
-- solo vivia en el borrador; el producto aprobado se quedaba con los colores que se volvieron
-- variantes, y una ficha no tenia como decir que el modelo existe en otros.
alter table producto add column paleta text;

update producto p
   set paleta = b.paleta
  from borrador_tecnologia b
 where b.producto_id = p.id
   and b.estado = 'APROBADO'
   and b.paleta is not null
   and p.paleta is null;

-- Una variante cuyo color nadie ha confirmado: no se compra sin un enlace de compra. La recalcula
-- cada lista del proveedor; hasta la proxima, todas las de tecnologia quedan por confirmar salvo
-- las de un modelo de un solo color, que es lo prudente sin saber que dijo la ultima lista.
alter table variante add column color_por_confirmar boolean not null default false;

update variante v
   set color_por_confirmar = true
 where exists (select 1 from variante_de_proveedor vp where vp.variante_id = v.id)
   and not exists (
       select 1 from producto p
        where p.id = v.producto_id
          and p.paleta is not null
          and strpos(trim(both E'\n' from p.paleta), E'\n') = 0
   );

-- El enlace que habilita a una persona a comprar un color ya confirmado con el proveedor. El token
-- se guarda en claro y no su hash, a diferencia de los de la cuenta (ADR-0015): vale 24 horas, una
-- compra, y lo que deja hacer es pagar un equipo al precio publicado; el panel lo necesita entero
-- para volver a copiarlo si el mensaje se perdio.
create table enlace_de_compra (
    id uuid primary key,
    token varchar(64) not null,
    variante_id uuid not null references variante (id) on delete cascade,
    -- La reserva de inventario que aparta la unidad mientras el enlace esta vigente.
    reserva_id uuid not null,
    creado_en timestamptz not null,
    expira_en timestamptz not null,
    -- La unidad entro al libro con el enlace porque no habia ninguna libre: anularlo la saca.
    entrada_propia boolean not null default false,
    usado_en timestamptz,
    pedido_id uuid,
    anulado_en timestamptz,
    constraint ux_enlace_de_compra_token unique (token),
    constraint ck_enlace_de_compra_usado check (usado_en is null or pedido_id is not null),
    constraint ck_enlace_de_compra_un_final check (usado_en is null or anulado_en is null)
);

create index ix_enlace_de_compra_vigentes on enlace_de_compra (expira_en)
    where usado_en is null and anulado_en is null;

-- La linea del carrito recuerda con que enlace entro, para que el checkout lo presente al crear el
-- pedido.
alter table linea_carrito add column enlace_compra varchar(64);
