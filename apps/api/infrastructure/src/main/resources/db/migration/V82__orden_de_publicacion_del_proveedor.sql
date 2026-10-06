-- El orden en que cada proveedor manda las fotos y el texto con el precio (5 de octubre de 2026).
--
-- Android exporta la hora sin segundos, y una foto que cae en el mismo minuto que dos precios no
-- dice de cual es. El agrupador se la daba siempre al de despues, que es lo que hacen Imperio Wicho
-- y D'Osman (el album primero); La Riverah manda primero el texto, y su jean de cuero negro salio
-- sin fotos. Es un valor de OrdenDePublicacion: FOTOS_PRIMERO o TEXTO_PRIMERO.
--
-- El valor por omision es solo para rellenar las filas que ya existen, y es el comportamiento que
-- tenian: nadie cambia de reparto sin que alguien lo decida en el panel. Despues se quita, porque
-- el orden es obligatorio y no tiene un valor que se pueda suponer: un insert a mano (Adminer, un
-- script) que no lo diga tiene que fallar, no recibir uno sin que nadie lo eligiera. Sin `check`,
-- como `linea`: los valores los cuida el dominio.
alter table proveedor
    add column orden_de_publicacion varchar(20) not null default 'FOTOS_PRIMERO';

alter table proveedor alter column orden_de_publicacion drop default;
