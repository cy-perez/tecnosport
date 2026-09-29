-- Las etiquetas con que se difunde cada categoria en redes (29 de septiembre de 2026).
--
-- En la categoria y no en el codigo, que era la otra opcion. Son 35 categorias en cuatro lineas, o
-- sea unas 175 etiquetas si se pone un punado en cada una: un mapa constante habria exigido un
-- despliegue por cada ajuste de marketing, y ajustar etiquetas es justo lo que se hace a menudo
-- cuando se mira que funciona. Ademas son datos de negocio, y esos no se inventan desde el codigo.
--
-- Tabla de union y no una columna `text[]`, aunque para un punado de cadenas el arreglo seria mas
-- corto de escribir. Manda el precedente: `atributo_valor_permitido` es exactamente esta misma
-- forma -- una lista corta de cadenas colgando de una fila -- y ya esta ejercida contra esta
-- version de Hibernate. Estrenar aqui el mapeo de arreglos de Postgres seria apostar a que funciona
-- en Boot 4.1 para ahorrar una tabla que no molesta a nadie.
--
-- `orden` porque el orden en que se escriben es el orden en que se publican, y una lista que se
-- reordena sola cada vez que alguien guarda es de esas cosas que nadie reporta pero todos notan.
--
-- La forma de cada etiqueta (`#` mas letras, digitos o guion bajo) la defiende el objeto de valor
-- `Hashtag`, no una restriccion aqui: ahi el error se puede leer y explicar por que un guion
-- partiria la etiqueta en dos al publicarla. Duplicarla en la base solo cambiaria ese mensaje por
-- un 500 crudo el dia que las dos definiciones dejen de coincidir (ADR-0019).
create table categoria_hashtag (
    categoria_id uuid not null references categoria (id) on delete cascade,
    orden integer not null,
    valor varchar(150) not null,
    primary key (categoria_id, orden)
);

-- Borrar una categoria se lleva sus etiquetas por delante, y eso si es `cascade` de verdad: una
-- etiqueta sin categoria no significa nada y no hay nada que auditar en ella. Es el caso contrario
-- al de `publicacion_en_red`, donde la fila huerfana es justo la que alguien va a querer encontrar.

-- La unica consulta es "las etiquetas de esta categoria", y la clave primaria ya la sirve.
