-- A quién se le entrega el pedido y a qué número se le avisa.
--
-- El pedido nacía solo con el correo del comprador. Sirve para identificarlo y para escribirle,
-- pero no para despachar: la guía de la transportadora exige nombre y teléfono del destinatario,
-- y el mensajero de contraentrega llama antes de llegar. El plan de arranque lo registraba como
-- riesgo aceptado sin discutir; desde aquí es un dato del pedido.
--
-- Nulas a propósito: los pedidos que ya existen no lo tienen y se reconstruyen tal como quedaron.
-- La exigencia para un pedido nuevo vive en CrearPedido, igual que la de la tarifa de envío (V33).
alter table pedido
    add column nombre_contacto varchar(120),
    add column telefono_contacto varchar(20);

-- O van los dos o no va ninguno: medio contacto no sirve para nada.
alter table pedido
    add constraint pedido_contacto_completo check (
        (nombre_contacto is null) = (telefono_contacto is null)
    );
