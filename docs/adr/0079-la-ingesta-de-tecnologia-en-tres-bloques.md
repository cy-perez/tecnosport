# ADR-0079 — La ingesta de tecnología en tres bloques

**Fecha:** 2026-10-10
**Estado:** aceptado. Extiende ADR-0075 (la tecnología entra por la lista de
precios), que importaba la lista entera de una vez y subía las fotos después de
aprobar.

## Contexto

Con ADR-0075 una sola corrida de la skill `listas-de-proveedor` hacía todo: leía
la lista, investigaba lo nuevo, retocaba las fotos, importaba la lista —renovar,
retirar y proponer en la misma llamada— y, después de aprobar en el panel, subía
las fotos y publicaba. Tres cosas no encajaban:

- **El inventario quedaba atado a lo más lento.** Las fotos de un modelo nuevo
  se consiguen a mano y pueden tardar días; mientras tanto la lista del día no
  entraba, y el sitio seguía vendiendo lo que el proveedor ya no tenía, o con el
  costo de ayer.
- **Un borrador sin fotos no es una ficha que se pueda revisar.** Quien aprueba
  decidía colores y precio sin ver el producto, y el producto nacía en borrador
  hasta que una segunda corrida subiera las fotos.
- **Una foto mala se descubría tarde.** Un modelo con dos fotos utilizables
  llegaba al panel y se quedaba ahí, o se publicaba con una galería pobre.

Decisión del negocio del 10/10/2026: partir la ingesta en tres bloques
independientes, cada uno con su skill, que se entregan el trabajo por archivos.

## Decisión

1. **Bloque 1, `/listas-de-proveedor`: entregables e inventario.** Lee la lista,
   la compara, investiga, arma los entregables y la base de conocidos, y cierra
   con la importación **de inventario**: renueva lo que ya se vende y retira lo
   desaparecido, el mismo día. No propone nada ni toca fotos.
2. **La importación elige su tiempo** (`ImportarListaDeTecnologiaComando.Modo`):
   - `INVENTARIO` renueva, repone y retira. Entra una vez y nunca hacia atrás,
     como antes. Devuelve lo que espera borrador: los modelos que no se venden
     (`modelosPorProponer`, los que necesitan fotos) y las configuraciones nuevas
     de los que sí (`configuracionesPorProponer`, que no las necesitan).
   - `BORRADORES` solo propone. No toca costo ni existencia, así que la misma
     lista puede entrar varias veces —las fotos de un modelo llegan después que
     las de otro—; pero nunca la de una fecha anterior a la última, que le
     pondría a un borrador el costo de antes. No retira nada: un cuerpo que traiga
     desaparecidos se rechaza, porque quien lo manda creería que se retiró.

   **Partir la lista lo decide la API, no la skill.** La base de conocidos de la
   skill dice qué se investigó, no qué se vende: un modelo investigado y nunca
   aprobado no tiene producto. Solo la API sabe qué hay en el catálogo.
3. **Bloque 2, `/fotos-estudio-degradado`: fotos publicables o espera.** Procesa
   solo los modelos de `por-proponer.json`. Cada modelo necesita **4 fotos
   LISTA**; una REVISAR no cuenta aunque se apruebe a ojo. Antes de gastar el
   retoque se revisa que haya 4 originales con resolución suficiente. Si un modelo
   no llega, el bloque **se detiene**, dice qué foto falló y por qué, y espera a
   que la persona confirme que dejó fotos nuevas; comprueba que la carpeta cambió
   antes de reprocesar, y reprocesa solo lo nuevo. No entrega hasta que todos los
   modelos pasen. La única salida es de la persona: sacar un modelo de la tanda,
   que queda registrado sin fotos publicables para una tanda siguiente.
4. **Bloque 3, `/fichas-de-tecnologia`: el borrador con sus fotos.** Importa en
   modo `BORRADORES` los modelos que pasaron el bloque 2 y sube sus fotos **al
   borrador**. Aprobar en el panel pasa las fotos al producto y **lo publica**:
   quien aprueba ya vio fotos, colores y precio. El servidor exige 4 fotos para
   aprobar un modelo nuevo, para que la compuerta no dependa solo de la skill.

## Consecuencias

- El inventario de lo publicado ya no espera a nadie.
- Un modelo nuevo tarda lo que tarden sus fotos, y eso queda a la vista: el
  bloque 2 dice qué falta y a cuál.
- "Solo LISTA" frena modelos: una foto que haya que ampliar 1,5× o más sale
  REVISAR, y buena parte de lo que trae Icecat lo es. Esos modelos piden fotos
  de mejor resolución.
- El borrador de tecnología gana fotos propias, con el mismo patrón que el de
  prendas (`FotoSubida`), pero ya procesadas: el servidor no las retoca.
