-- El pantalon Americanino: dos fotos de "Azul oscuro" que eran una sola variante (7 de octubre
-- de 2026).
--
-- Arreglo puntual de un producto ya aprobado. `AprobarBorrador` agrupaba los tonos con
-- `distinct()`, asi que dos fotos marcadas con el mismo color colapsaban en **una** variante con
-- las dos fotos colgando: en la ficha salia un solo circulo y quien compra no podia elegir cual de
-- las dos prendas queria. El codigo ya no lo hace -- numera los tonos repetidos --, pero eso vale
-- para las aprobaciones nuevas y este producto ya estaba publicado.
--
-- **Un bloque `DO` y es el primero del proyecto.** Las demas migraciones son SQL plano porque
-- describen el esquema o corrigen filas con un `update` parametrizado por un join. Esto no: hay
-- que crear una variante nueva por cada talla del color repetido, con su valor de atributo y su
-- inventario, y colgarle una foto concreta. Escrito como SQL plano serian cinco sentencias
-- encadenadas por CTE que nadie podria leer dentro de un anio.
--
-- Todo va guardado: en una base donde este producto no exista -- las de prueba, una instalacion
-- nueva -- la migracion no hace nada y no falla.

do $$
declare
    v_producto uuid;
    v_duplicada uuid;
    v_atributo_color uuid;
    v_valor text;
    v_hex varchar(7);
    v_talla_de_la_foto text;
    v_nueva_de_la_foto uuid;
    v_nueva uuid;
    r record;
begin
    select id into v_producto
    from producto
    where slug = 'pantalon-estilo-levi-s-americanino';
    if v_producto is null then
        return;
    end if;

    -- La variante que carga mas de una foto de galeria: la senial de que dos tonos iguales
    -- colapsaron. Si no hay ninguna, no hay nada que separar.
    select variante_id into v_duplicada
    from imagen_producto
    where producto_id = v_producto and tipo = 'GALERIA' and variante_id is not null
    group by variante_id
    having count(*) > 1
    limit 1;
    if v_duplicada is null then
        return;
    end if;

    select atributo_id, valor, color_hex into v_atributo_color, v_valor, v_hex
    from variante_atributo_valor
    where variante_id = v_duplicada and color_hex is not null;
    -- Ya numerado: la migracion corrio antes, o alguien lo arreglo a mano.
    if v_valor is null or v_valor ~ ' \d+$' then
        return;
    end if;

    -- La talla de la variante duplicada, para saber a cual de las nuevas le toca la segunda foto.
    select valor into v_talla_de_la_foto
    from variante_atributo_valor
    where variante_id = v_duplicada and atributo_id <> v_atributo_color;

    -- 1. Las que ya existen pasan a "<color> 1". El numero no dice nada de la prenda, pero
    --    distingue, y distinguir es lo que hace falta: el valor del atributo es lo que el pedido
    --    congela y lo que lee quien empaca.
    update variante_atributo_valor vav
    set valor = v_valor || ' 1'
    from variante v
    where vav.variante_id = v.id
      and v.producto_id = v_producto
      and vav.atributo_id = v_atributo_color
      and vav.valor = v_valor;

    -- 2. Una variante nueva por cada talla de ese color, con "<color> 2".
    --
    --    **Nacen con el inventario en cero y es deliberado.** Cuando las dos prendas colapsaron en
    --    una, las unidades se cargaron una sola vez por (color, talla), y no hay forma de saber
    --    desde aqui cuantas eran de una y cuantas de la otra. Repartirlas a ojo seria inventar un
    --    dato de negocio. Salen agotadas, se ven tachadas en la ficha, y quien administra pone la
    --    cifra real desde el panel -- que es un paso mas, pero ninguno de los dos numeros es una
    --    suposicion nuestra.
    for r in
        select v.id, v.sku, v.precio, v.tasa_iva, v.estado,
               vt.atributo_id as atributo_talla, vt.valor as talla
        from variante v
        join variante_atributo_valor vc
          on vc.variante_id = v.id
         and vc.atributo_id = v_atributo_color
         and vc.valor = v_valor || ' 1'
        left join variante_atributo_valor vt
          on vt.variante_id = v.id
         and vt.atributo_id <> v_atributo_color
        where v.producto_id = v_producto
        order by v.creado_en, v.sku
    loop
        v_nueva := gen_random_uuid();

        insert into variante (id, producto_id, sku, precio, tasa_iva, estado, creado_en)
        values (v_nueva, v_producto, r.sku || '-2', r.precio, r.tasa_iva, r.estado, now());

        insert into variante_atributo_valor (id, variante_id, atributo_id, valor, color_hex)
        values (gen_random_uuid(), v_nueva, v_atributo_color, v_valor || ' 2', v_hex);

        if r.atributo_talla is not null then
            insert into variante_atributo_valor (id, variante_id, atributo_id, valor, color_hex)
            values (gen_random_uuid(), v_nueva, r.atributo_talla, r.talla, null);
        end if;

        -- Sin fila de inventario la variante no existe para el libro de movimientos (ADR-0050) y
        -- el panel no podria ni ajustarle la existencia.
        insert into inventario (id, variante_id) values (gen_random_uuid(), v_nueva);

        if r.talla is not distinct from v_talla_de_la_foto then
            v_nueva_de_la_foto := v_nueva;
        end if;
    end loop;

    -- 3. Y la segunda foto cuelga de la nueva. La primera -- la de menor `orden` -- se queda donde
    --    estaba: es la que la tarjeta del catalogo ya usa para ese color.
    update imagen_producto
    set variante_id = v_nueva_de_la_foto
    where producto_id = v_producto
      and tipo = 'GALERIA'
      and variante_id = v_duplicada
      and id <> (
          select id from imagen_producto
          where producto_id = v_producto and tipo = 'GALERIA' and variante_id = v_duplicada
          order by orden
          limit 1);
end
$$;
