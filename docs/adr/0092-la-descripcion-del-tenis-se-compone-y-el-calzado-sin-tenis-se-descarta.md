# ADR-0092: la descripción del tenis se compone, y el calzado sin un tenis se descarta

Fecha: 2026-10-26
Estado: aceptada
Completa: `ADR-0089` (la tienda solo vende tenis) y `ADR-0090` (el mismo título para el tenis de
cualquier proveedor).

## Contexto

El tenis de Tenis Al Pie llegaba al panel sin descripción, porque su texto es solo el precio. El
de Imperio Wicho la traía en la prosa del extractor, que repetía las tallas del anuncio —las del
proveedor, no las que se venden— y a veces el género. Quien aprobaba la escribía a mano en los dos
casos.

Imperio Wicho manda también chanclas y sandalias en el mismo chat. El extractor no tiene un tipo
para ellas: salían `OTRO` y llegaban al panel como borrador. Las fotos ya lo decían, porque las
lee el lector de calzado, pero solo Tenis Al Pie usaba esa lectura para filtrar.

## Decisión

Decidida por el negocio el 26 de octubre de 2026, para Imperio Wicho y Tenis Al Pie.

1. **La descripción de un tenis se compone** (`DescripcionDeTenis`): el título, para quién es, las
   tallas que se venden y, al final, cómo está hecho.
   - «Tenis importados estilo New Balance 9060 para dama. Disponibles desde la talla 35 hasta la
     38.»
   - «Tenis importados estilo Alexander McQueen unisex. Disponibles desde la talla 35 hasta la 43.»
   - Con una sola talla no hay rango: «Disponibles en la talla 38.»
   - Lo que no se sabe no se escribe: sin género, sin «para»; sin tallas numéricas, sin la frase.
2. **Cómo está hecho sale de la `descripcion` del extractor.** En un tenis, el prompt le pide solo
   eso —la suela, el material, el peso, los cordones— en una frase, o `null`. El campo `material`
   no se usa: «ultralivianos» o «cordones encerados» no son un material.
3. **Se compone sobre el borrador ya resuelto**, con las tallas corridas y el género de cada uno:
   los gemelos de un anuncio unisex («Horma normal Dama, Aumenta 1 Cab») dicen cada uno lo suyo.
4. **El género del texto manda sobre el de la foto** en el proveedor que escribe un texto por
   anuncio (`GeneroDelAnuncio`): «IMPORTADO CABALLERO». En una tanda de Tenis Al Pie el texto es
   de todos sus tenis, y manda la foto de cada uno.
5. **La descripción sigue al modelo que corrige quien revisa.** Si el título o las tallas cambian
   en el panel —al guardar o al aprobar sin guardar— y la descripción sigue siendo la compuesta, se
   recompone con lo nuevo y conserva las características. Si alguien la reescribió, manda lo que
   escribió.
6. **El anuncio de un proveedor de calzado en el que ninguna foto muestra un tenis se descarta**
   (`CalzadoSinTenis`), con `CALZADO_QUE_NO_ES_TENIS` y el calzado que más se ve como dato.
   - Con un tenis y una chancla en las mismas fotos, sigue: no se sabe de cuál es el precio, y lo
     decide quien revisa.
   - **Sin fotos, o con fotos que no se pudieron leer, se descarta** (`CALZADO_SIN_FOTOS_LEIDAS`):
     nadie vio que fuera un tenis.
   - Un «AGOTADO» no se descarta: agota lo que ya se vende.
7. **Antes que las fotos, el texto** (añadida el 28 de octubre de 2026: a Imperio Wicho solo se le
   compran tenis). El anuncio de un proveedor de calzado que se nombra chancla, chola, playera,
   sandalia, Crocs, zueco, bota, botín, pantufla o babucha, y no dice «tenis», se descarta con
   `CALZADO_QUE_NO_ES_TENIS` y el calzado que nombra primero, **sin llamar al extractor ni al
   lector de fotos**.
   - El 27 de octubre de 2026 eran 7 de los 33 anuncios de Imperio Wicho —la chola playera, cuatro
     sandalias de correa, las sandalias Bob y Elmo y una playera—, y cada uno gastaba dos lecturas
     con Claude para que la foto dijera lo que el texto ya decía.
   - «Deportivo» no cuenta como tenis: Imperio Wicho anuncia la «Sandalia Correa Deportiva». Si el
     texto dice «tenis», deciden las fotos, como en la decisión 6.
   - Solo en el calzado: en la ropa, una playera es una camiseta.

## Consecuencias

- **Lo ya ingerido se conserva.** Los borradores en revisión no cambian de descripción, y las
  chanclas que ya estén en revisión se rechazan desde el panel.
- **Con el lector de fotos apagado, un proveedor de calzado no deja borradores.** En un ambiente sin
  la clave de la API, toda su ingesta sale descartada por `CALZADO_SIN_FOTOS_LEIDAS`. Es la
  consecuencia directa de la decisión 6, y es lo que se quiere: sin lectura no hay quien diga que
  es un tenis.
- **Un fallo pasajero del lector descarta el anuncio.** Se ve en el panel de la ingesta con su
  motivo y se recupera volviendo a ingerir la exportación.
- **El prompt del extractor cambió, no su esquema**: no hace falta la prueba de contrato contra la
  API real, pero las descripciones de los tenis ingeridos antes traen la prosa vieja.
- **Si el extractor sigue escribiendo prosa en un tenis**, la frase sale repetida después de la
  apertura. Se corrige en el panel, y es la señal para endurecer el prompt.
