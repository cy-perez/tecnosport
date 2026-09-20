-- El paquete de la variante deja de ser obligatorio (19 de septiembre de 2026, adr/0046).
--
-- La V32 los puso NOT NULL con un argumento correcto —sin peso ni dimensiones no hay cotizacion de
-- envio— que escondia un salto logico: de "no se puede cotizar" no se sigue "no se puede vender".
-- Se puede vender para RECOGIDA EN EL PUNTO, que es un canal que este negocio ya tiene, ya ofrece
-- en el checkout y ya usa como salida cuando ninguna transportadora cubre el destino.
--
-- Lo destapo el primer catalogo real: de los productos listos para publicar, los fabricantes de
-- celulares no publican las medidas de su caja por ningun lado, asi que el NOT NULL obligaba a
-- parar el catalogo entero hasta tener una bascula a mano. Un requisito que bloquea la venta de
-- todo un catalogo para proteger el flete de una parte de el esta mal calibrado.
--
-- Lo que NO se relaja, y por eso esto es un `drop not null` y no un `default 0`:
--
--   * El objeto de valor Paquete sigue exigiendo las cuatro cifras mayores que cero. Si existe, es
--     valido. La diferencia entre "no lo se todavia" (nulo) y "mide cero" (invalido) es justo la
--     que hay que conservar: la segunda es la que cobra fletes de menos en silencio, que es contra
--     lo que advertia la V32 al negarse a rellenar por defecto. Ese razonamiento sigue en pie
--     entero; lo unico que cambia es que ahora hay una tercera respuesta posible.
--   * O van las cuatro o no va ninguna. Una fila con tres reventaria al construir el Paquete, y
--     eso es un error de carga y no un estado del negocio. Lo garantiza la restriccion de abajo.
--
-- Ninguna fila existente se toca: las que tienen medidas las conservan.
alter table variante
    alter column peso_gramos drop not null,
    alter column largo_cm    drop not null,
    alter column ancho_cm    drop not null,
    alter column alto_cm     drop not null;

-- Las cuatro juntas o ninguna. Sin esto, una carga a medias dejaria una fila que revienta al
-- leerse y no al escribirse, que es el peor momento para enterarse.
alter table variante
    add constraint variante_paquete_completo_o_ausente check (
        (peso_gramos is not null and largo_cm is not null and ancho_cm is not null and alto_cm is not null)
        or
        (peso_gramos is null and largo_cm is null and ancho_cm is null and alto_cm is null)
    );
