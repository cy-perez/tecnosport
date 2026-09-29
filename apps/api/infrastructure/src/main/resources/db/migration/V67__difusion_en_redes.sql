-- La constancia de cada difusion de un producto en una red social (29 de septiembre de 2026).
--
-- Guarda el pie completo y no solo una referencia al producto, y no es redundancia: el pie se arma
-- con el precio de ese dia, la descripcion de ese dia y los hashtags que la categoria tenia ese
-- dia, y ademas quien publica puede editarlo antes de enviarlo. Reconstruirlo despues daria un
-- texto distinto del que la gente leyo.
create table publicacion_en_red (
    id uuid primary key,
    -- `on delete set null` y no `cascade`: la publicacion sigue viva en Instagram aunque nosotros
    -- borremos el producto del catalogo, y esa es justo la fila que alguien va a querer encontrar
    -- -- un post que ahora apunta a un 404. Con `cascade` desapareceria la unica pista.
    producto_id uuid references producto (id) on delete set null,
    red varchar(20) not null,
    estado varchar(20) not null,
    -- Nulo mientras la red no conteste. Cuando el estado es PUBLICADA el agregado exige que este,
    -- y sin el no se podria volver al post ni comprobar si sigue vivo.
    id_publicacion_externa varchar(100),
    -- `text` y no un `varchar(2200)`: el tope de Instagram es de Meta, cambia cuando Meta quiera y
    -- se iria con ella; su sitio es la configuracion del adaptador. El tope del agregado
    -- (`PublicacionEnRed.MAXIMO_CARACTERES_PIE`) es otra cosa -- evitar que una fila crezca sin
    -- limite -- y ahi es donde tiene que fallar, con un mensaje entendible. Duplicar cualquiera de
    -- los dos aqui solo cambiaria ese error por un 500 crudo de Postgres el dia que las cifras
    -- dejen de coincidir, que es lo que le paso a `imagen_producto.hash` (ADR-0019).
    pie_de_foto text not null,
    -- Cual imagen se mando, no cual imagen tiene hoy el producto. Si manana se sube otra principal,
    -- el post sigue enseniando esta.
    url_imagen text not null,
    solicitada_en timestamptz not null,
    publicada_en timestamptz,
    detalle_del_fallo text
);

-- Sin restriccion de unicidad sobre (producto_id, red) a proposito: difundir el mismo producto en
-- septiembre y otra vez en diciembre es el caso bueno. Lo que hay que atajar es el doble clic, que
-- es una ventana de tiempo y vive en el caso de uso.
--
-- La consulta que el panel va a hacer siempre es "que se ha difundido de este producto, y cuando",
-- para que la ficha pueda avisar antes de repetir.
create index ix_publicacion_en_red_producto on publicacion_en_red (producto_id, solicitada_en desc);

-- Y la de la bandeja, el dia que exista: que quedo pendiente o fallido. Parcial porque las filas
-- PUBLICADA son la inmensa mayoria y no las busca nadie por estado.
create index ix_publicacion_en_red_sin_resolver
    on publicacion_en_red (estado, solicitada_en)
    where estado <> 'PUBLICADA';
