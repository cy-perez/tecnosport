-- `MetodoPago.ADDI` sale del enum (22 de septiembre de 2026, docs/11-pagos-y-envios.md).
--
-- El valor existía desde la Fase 3 y **nunca se pudo elegir**: la lista de habilitados de Wompi no
-- lo incluye desde el 14 de septiembre —Addi exige que el sitio esté en línea para estudiar la
-- activación— y `CrearPedido` rechazaba cualquier método que no estuviera habilitado. Mientras
-- tanto el modelo mentía: el enum decía que lo cobraba Wompi, donde no existe.
--
-- La decisión del dueño del negocio es quitarlo e integrarlo de verdad cuando el sitio esté en
-- producción. Entonces vuelve, y vuelve con su integración, no como un valor suelto.
--
-- ESTA MIGRACIÓN NO CAMBIA NINGUNA FILA: comprueba. Si alguna quedara con 'ADDI', el despliegue se
-- detiene aquí en vez de arrancar una aplicación que revienta al leer ese pedido —el enum ya no
-- tiene el valor, así que `MetodoPago.valueOf` lanzaría, y lo haría al abrir la ficha del pedido,
-- lejos de este punto y sin decir por qué. Mismo criterio que la V32 con los paquetes sin medir:
-- una migración que falla se arregla; un dato que se lee mal, no se nota.
do $$
declare
    pedidos integer;
    pagos integer;
begin
    select count(*) into pedidos from pedido where metodo_pago = 'ADDI';
    select count(*) into pagos from pago where metodo_pago = 'ADDI';
    if pedidos > 0 or pagos > 0 then
        raise exception
            'Hay % pedido(s) y % pago(s) con metodo_pago = ''ADDI'', y el enum ya no lo tiene. '
            'Decide qué método los representa antes de desplegar esto.', pedidos, pagos;
    end if;
end $$;
