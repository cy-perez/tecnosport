-- Toda variante de tecnologia se consulta por WhatsApp antes de venderse (29 de octubre de 2026,
-- enmienda a ADR-0086). Hasta hoy quedaban libres el color que la lista marcaba con su emoji y el
-- de un modelo de un solo color, y con eso se agregaba al carrito un iPad azul que nadie habia
-- confirmado con el proveedor. Tambien las que se crearon desde el panel, que nacian en false.
update variante v
   set disponibilidad_por_confirmar = true
 where not v.disponibilidad_por_confirmar
   and (
       exists (select 1 from variante_de_proveedor vp where vp.variante_id = v.id)
       or exists (
           select 1
             from producto p
             join categoria c on c.id = p.categoria_id
            where p.id = v.producto_id
              and c.linea = 'TECNOLOGIA'
       )
   );
