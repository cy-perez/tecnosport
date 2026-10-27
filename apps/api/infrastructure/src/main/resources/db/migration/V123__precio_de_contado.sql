-- El precio de contado (26 de octubre de 2026, ADR-0095).
--
-- Cada variante pasa a tener dos precios. `precio_contado` es el que el negocio quiere recibir
-- neto, y lo paga quien compra sin pasarela (transferencia, en el punto, contra entrega). `precio`
-- sigue siendo la columna que leen la vitrina, los filtros, el carrito y Wompi, y desde aqui es el
-- PUBLICADO: el de contado con la comision de Wompi dentro, subido a la centena por debajo de 100.000 y a la unidad de mil desde ahi.
--
-- Los precios que ya estan en el catalogo son de contado (decision 6 del ADR, del negocio): se
-- copian a `precio_contado` y `precio` se recalcula con la formula de `ComisionDePasarela`:
--
--     exacto    = (contado + fija * (1 + iva)) / (1 - variable * (1 + iva))
--     publicado = ceil(exacto / 100) * 100     si exacto < 100000   (a la centena)
--                 ceil(exacto / 1000) * 1000   si no                (a la unidad de mil)
--
-- LAS TARIFAS ESTAN ESCRITAS AQUI A MANO y son las del plan de Wompi leido en el portal el 26 de
-- octubre de 2026: 2,65 % + $700 + IVA del 19 %. Son las mismas que traen por omision
-- WOMPI_COMISION_VARIABLE, WOMPI_COMISION_FIJA y WOMPI_COMISION_IVA en application.yml. Una
-- migracion no lee la configuracion, y no hace falta: corre una sola vez. DESPUES DE ESTA
-- MIGRACION EL RECALCULO LO HACE EL PANEL (RecalcularPreciosPublicados), que usa las tarifas
-- configuradas y ensena la lista antes de aplicarla. MigracionPrecioDeContadoTest compara este
-- calculo contra el de Java.
alter table variante add column precio_contado numeric(14, 2);

update variante
set precio_contado = precio,
    precio = case
        when (precio + 700 * (1 + 0.19)) / (1 - 0.0265 * (1 + 0.19)) < 100000
            then ceil((precio + 700 * (1 + 0.19)) / (1 - 0.0265 * (1 + 0.19)) / 100) * 100
        else ceil((precio + 700 * (1 + 0.19)) / (1 - 0.0265 * (1 + 0.19)) / 1000) * 1000
    end;

alter table variante alter column precio_contado set not null;

-- El publicado es el maximo que alguien paga: por debajo del contado, el descuento por pagar sin
-- pasarela seria un recargo. El dominio lo exige al construir la variante; esto lo exige a
-- cualquier otra puerta.
alter table variante add constraint ck_variante_precio_no_menor_que_contado
    check (precio >= precio_contado);
