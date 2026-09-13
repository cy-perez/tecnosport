-- La unidad en la que se mide un atributo numérico, cuando el número solo no dice nada.
--
-- "Garantía: 12" se mostraba así en la ficha, sin decir 12 qué. El atributo es genérico de tipo
-- NUMERO —igual que "Talla calzado"— y no tenía dónde guardar que se mide en meses. Es del
-- atributo y no de cada valor: todos los valores de un eje se miden igual. Opcional, porque una
-- talla o un color no la tienen.
alter table atributo
    add column unidad varchar(20);
