-- El negocio es no responsable de IVA (parágrafo 3 del art. 437 del Estatuto Tributario), así que
-- ninguna variante puede llevar impuesto: el literal a del art. 1.3.1.15.2 del Decreto 1625 de 2016
-- prohíbe adicionar al precio suma alguna por ese concepto, y quien lo hace queda obligado a cumplir
-- íntegramente el régimen de los responsables. Ver adr/0041.
--
-- Se pone en cero lo sembrado, que hasta hoy declaraba 0.19 en todas sus variantes.
update variante
set tasa_iva = 0.0000
where tasa_iva <> 0.0000;

-- `linea_pedido` NO se toca, y la omisión es deliberada: esa columna guarda la tasa con la que se
-- cobró un pedido, no la que se cobra hoy. Reescribirla hacia atrás falsificaría el registro de lo
-- que el comprador pagó. Hoy no hay un solo pedido real, así que la decisión no cuesta nada; el día
-- que los haya, sigue siendo la correcta.

-- Sin `check (tasa_iva = 0)`, y esto también es a propósito. La condición de no responsable depende
-- de umbrales anuales del parágrafo 3 del art. 437 y se pierde desde el período siguiente a
-- incumplir cualquiera de ellos. Un `check` en la base convertiría ese día en una migración urgente
-- bajo presión. Quien impide escribir una tasa distinta es NEGOCIO_RESPONSABLE_IVA, que es
-- configuración y se cambia sin tocar el esquema.
