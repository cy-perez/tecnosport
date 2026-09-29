-- `TARJETA`, `PSE`, `NEQUI` y `BANCOLOMBIA` se agrupan en `WOMPI` (28 de septiembre de 2026,
-- docs/11-pagos-y-envios.md).
--
-- POR QUÉ. Los cuatro los cobraba Wompi, y lo que el comprador elegía en nuestro checkout **nunca
-- viajaba**: la URL del Web Checkout hospedado no le manda a Wompi el método elegido, Wompi pinta
-- su propia lista y el comprador vuelve a elegir allí. Lo dice la `V39`, que añadió
-- `pago.medio_reportado_pasarela` justamente porque un pedido podía decir NEQUI y haberse cobrado
-- con tarjeta, sin ninguna señal. Cuatro valores que el sistema no puede honrar no son cuatro
-- datos: son una intención disfrazada de dato. `WOMPI` dice lo que de verdad se sabe.
--
-- `medio_reportado_pasarela` NO se toca: ahí sigue, crudo y sin traducir, con qué se cobró de
-- verdad (`CARD`, `NEQUI`, `PSE`, `BANCOLOMBIA_TRANSFER`...). Esta migración agrupa la intención,
-- no la evidencia.
--
-- ESTA MIGRACIÓN SÍ MUEVE FILAS, y en eso se diferencia de la `V61`. Aquella quitó `ADDI` del enum
-- comprobando que no hubiera ninguna, y podía: `ADDI` nunca se pudo elegir. Estos cuatro sí, así
-- que hay historial y hay que decidir qué pasa con él. Se migran a `WOMPI` porque es verdad —los
-- cobró esa pasarela— y porque lo contrario, dejarlos con un valor que el enum ya no tiene, revienta
-- al leer el pedido con `MetodoPago.valueOf`, lejos de aquí y sin decir por qué.
--
-- NEQUI TAMBIÉN VA A `WOMPI`, y no a `TRANSFERENCIA_MANUAL`. Desde hoy Nequi se recibe a mano, pero
-- eso vale para los pedidos nuevos: los que ya existen los cobró la pasarela y se confirmaron solos.
-- Escribir que se conciliaron a mano sería falsear el historial de un pago.
--
-- Las columnas son `varchar(30)` (`V4` y `V7`), no un tipo enumerado de Postgres, así que no hay
-- tipo ni restricción que alterar: solo filas.
update pedido
set metodo_pago = 'WOMPI'
where metodo_pago in ('TARJETA', 'PSE', 'NEQUI', 'BANCOLOMBIA');

update pago
set metodo_pago = 'WOMPI'
where metodo_pago in ('TARJETA', 'PSE', 'NEQUI', 'BANCOLOMBIA');

-- Y se comprueba, con el mismo criterio de la `V61`: una migración que falla se arregla; un dato
-- que se lee mal, no se nota. Si algo quedó fuera de los dos `update` de arriba —una tabla nueva que
-- guarde métodos de pago y que nadie recordó aquí— el despliegue se detiene en este punto en vez de
-- arrancar una aplicación que revienta al abrir una ficha.
do $$
declare
    pedidos integer;
    pagos integer;
begin
    select count(*) into pedidos from pedido
        where metodo_pago in ('TARJETA', 'PSE', 'NEQUI', 'BANCOLOMBIA');
    select count(*) into pagos from pago
        where metodo_pago in ('TARJETA', 'PSE', 'NEQUI', 'BANCOLOMBIA');
    if pedidos > 0 or pagos > 0 then
        raise exception
            'Quedan % pedido(s) y % pago(s) con un metodo de pasarela que el enum ya no tiene. '
            'Los updates de esta migracion no los alcanzaron.', pedidos, pagos;
    end if;
end $$;
