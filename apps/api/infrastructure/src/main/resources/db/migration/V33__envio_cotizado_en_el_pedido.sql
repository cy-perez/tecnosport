-- La tarifa de envío que el pedido congela, y con ella el flete que se le cobra al comprador.
--
-- Hasta la Fase 7 el flete era un costo estándar metido dentro del precio de cada línea
-- (adr/0012) y el pedido no lo conocía: su total era la suma de las líneas y punto. Desde
-- adr/0021 el envío se cotiza por destino, así que el pedido tiene que guardar cuánto cobró y con
-- qué tarifa, porque una cotización vive 24 horas y el pedido vive para siempre.
--
-- Se guarda la tarifa entera y no solo el monto: el identificador es lo que permitirá emitir la
-- guía (Fase 7, paso 7), y la transportadora y el plazo son lo que el comprador vio al comprar.
-- Un pedido que solo guardara el número no podría decir después de quién era ese precio.
--
-- OJO con la diferencia que estas columnas introducen: `costo_envio` es lo que el comprador PAGA;
-- `envio.costo_envio` es lo que el despacho CUESTA, se conoce al despachar y puede no coincidir.
-- Precio y costo, dos números distintos en dos tablas distintas.
alter table pedido
    add column costo_envio numeric(14, 2) not null default 0,
    add column tarifa_envio_id text,
    add column tarifa_envio_transportadora text,
    add column tarifa_envio_servicio text,
    add column tarifa_envio_dias integer,
    add column tarifa_envio_admite_contraentrega boolean,
    add column tarifa_envio_vence_en timestamptz;

-- Los pedidos que ya existen quedan en cero, y es históricamente cierto: bajo adr/0012 su flete
-- ya estaba cobrado dentro de las líneas. Poner cualquier otra cosa reescribiría lo que pagaron.
-- El default se retira enseguida para que ningún pedido nuevo herede un cero por descuido: desde
-- ahora, quien inserta un pedido dice explícitamente cuánto cobró de envío.
alter table pedido alter column costo_envio drop default;

-- El flete no se fracciona, igual que todo el dinero de este sistema, y no puede ser negativo.
alter table pedido add constraint ck_pedido_costo_envio_no_negativo check (costo_envio >= 0);

-- O están los seis campos de la tarifa, o no está ninguno. Media tarifa —un monto sin saber de
-- quién, o una transportadora sin identificador con el que emitir la guía— no sirve para nada y
-- es la clase de fila que aparece meses después, cuando ya nadie recuerda qué pasó.
alter table pedido add constraint ck_pedido_tarifa_envio_completa check (
    (tarifa_envio_id is null
        and tarifa_envio_transportadora is null
        and tarifa_envio_servicio is null
        and tarifa_envio_dias is null
        and tarifa_envio_admite_contraentrega is null
        and tarifa_envio_vence_en is null)
    or (tarifa_envio_id is not null
        and tarifa_envio_transportadora is not null
        and tarifa_envio_servicio is not null
        and tarifa_envio_dias is not null
        and tarifa_envio_admite_contraentrega is not null
        and tarifa_envio_vence_en is not null));

-- Sin tarifa el flete es cero. Los dos casos legítimos en que eso pasa —el retiro en punto, que no
-- cotiza, y los pedidos anteriores a la Fase 7— cobran cero de envío, y al revés: un costo mayor
-- que cero sin una tarifa que lo explique es un cobro sin respaldo.
alter table pedido add constraint ck_pedido_costo_envio_con_tarifa check (
    (tarifa_envio_id is null and costo_envio = 0) or tarifa_envio_id is not null);

-- El retiro en punto no despacha nada, así que no puede llevar tarifa. Es la misma invariante que
-- el dominio ya exige en el constructor de Pedido, repetida aquí porque la base la puede romper
-- cualquiera que inserte sin pasar por el agregado.
alter table pedido add constraint ck_pedido_retiro_sin_tarifa check (
    tipo_entrega <> 'RETIRO_EN_PUNTO' or tarifa_envio_id is null);

comment on column pedido.costo_envio is
    'Lo que el comprador paga de flete, congelado al confirmar. Distinto de envio.costo_envio, que '
    'es lo que el despacho le cuesta al negocio. Cero en retiro en punto y en los pedidos '
    'anteriores a la Fase 7, cuyo flete iba dentro del precio de cada linea (adr/0012).';

comment on column pedido.tarifa_envio_id is
    'Identificador de la tarifa en la transportadora, con el que se emite la guia. Nulo cuando no '
    'hubo cotizacion.';

comment on column pedido.tarifa_envio_vence_en is
    'Cuando vencia la cotizacion que se congelo. Se guarda para poder auditar despues si la guia '
    'se emitio con una tarifa ya vencida.';
