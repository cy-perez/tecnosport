-- Lo que se confirma por WhatsApp antes de venderse ya no es solo el color (ADR-0089, 25 de
-- octubre de 2026).
--
-- V117 creo la marca para el color de tecnologia (ADR-0086). Desde Tenis Al Pie, el calzado se vende
-- igual: el proveedor no dice que tallas tiene de cada modelo, y la talla se confirma antes de
-- cobrar. La marca es la misma —la variante no se compra sin un enlace de compra vigente— y lo que
-- se confirma lo dice la linea del producto, asi que la columna cambia de nombre y nada mas.
alter table variante rename column color_por_confirmar to disponibilidad_por_confirmar;
