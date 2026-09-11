-- El paquete de la variante: peso y dimensiones, obligatorios (adr/0021, docs/02-modelo-datos.md).
-- Sin ellos no hay cotización de envío, así que una variante sin paquete no se puede vender.
--
-- La migración va en tres tiempos a propósito: columnas nulables, relleno por SKU explícito, y
-- recién entonces NOT NULL. Si al llegar al tercer paso queda una fila sin paquete, la migración
-- revienta y el despliegue se detiene. Eso es lo que se quiere: el peso de un producto es un dato
-- que se mide, no que se deduce, y un relleno por defecto le pondría el mismo valor a una camiseta
-- y a un par de tenis. Un flete cobrado de menos se paga; una migración que falla se arregla.
alter table variante
    add column peso_gramos integer,
    add column largo_cm    integer,
    add column ancho_cm    integer,
    add column alto_cm     integer;

-- Medidas de DEMOSTRACIÓN para el catálogo sembrado, que es ficción completa: ni "Under Trail" ni
-- el "Celular TecnoSport Aurora" existen, así que no hay peso real que averiguar. Mismo criterio
-- que las fotos de picsum y que hashDeSiembra en SembradorCatalogo, que documenta ahí por qué un
-- dato de ejemplo puede cumplir una invariante sin ser un valor al azar.
--
-- Estos cuatro números están duplicados en SembradorCatalogo.guardarVariante (para las bases que
-- nacen ya migradas). Si se tocan aquí, se tocan allá.
--
-- TODO: peso y dimensiones reales de las variantes del catálogo de producción, medidos con el
-- producto empacado. No se heredan de estas filas.
update variante set peso_gramos = 180, largo_cm = 30, ancho_cm = 25, alto_cm = 4
    where sku in ('TS-CAM-AZ-M', 'TS-CAM-NG-L');

update variante set peso_gramos = 900, largo_cm = 33, ancho_cm = 22, alto_cm = 13
    where sku in ('UT-TEN-40', 'UT-TEN-38.5');

update variante set peso_gramos = 700, largo_cm = 45, ancho_cm = 30, alto_cm = 20
    where sku in ('TS-MOR-NG-25', 'TS-MOR-AZ-25');

update variante set peso_gramos = 400, largo_cm = 18, ancho_cm = 10, alto_cm = 6
    where sku in ('TS-CEL-AUR-128', 'TS-CEL-AUR-256');

alter table variante
    alter column peso_gramos set not null,
    alter column largo_cm    set not null,
    alter column ancho_cm    set not null,
    alter column alto_cm     set not null;

-- La invariante de Paquete, repetida aquí porque el sembrador escribe entidades JPA directo y no
-- pasa por el dominio. Sin topes máximos: son de cada transportadora y todavía no están confirmados
-- (docs/13-skydropx-capacidades.md).
alter table variante
    add constraint ck_variante_paquete_positivo
        check (peso_gramos > 0 and largo_cm > 0 and ancho_cm > 0 and alto_cm > 0);
