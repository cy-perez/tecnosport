# ADR-0050 — La existencia sale del libro, y el catálogo deja de guardar una

**Fecha:** 2026-09-20
**Estado:** aceptado. Supera a `adr/0017` y a la decisión 1 de `adr/0049`.

## Contexto

`ADR-0049` evaluó tres salidas a un defecto que llevaba desde la Fase 2 y describió la tercera como
la correcta:

> **C. Se borra la columna y la respuesta calcula el disponible leyendo el libro.** Cambia el
> contrato público, el mapeador, la vitrina y el OpenAPI, y mete una lectura del inventario en las
> consultas del catálogo.

Eligió la A —el ajuste escribe en los dos sitios— porque lo que había que resolver ese día era el 5
inventado de los doce productos reales, y meter C dentro habría convertido "el panel corrige la
existencia" en "se rediseña cómo la vitrina sabe si hay existencia".

Lo que dejó pendiente no era una limpieza. **La columna se seguía separando del libro con cada
venta**, y lo único que A añadió fue verlo: la pantalla de existencias marcaba el descuadre y el
panel lo contaba. Un día después había dos variantes descuadradas en la base local, una de ellas por
una venta real del 17 de septiembre.

Dos cosas que se supieron al ir a hacerlo y que abarataron C respecto de lo que `ADR-0049` temía:

1. **La vitrina nunca usó el número.** `hayExistencia`, `variantePorDefecto`, la etiqueta de stock y
   el `availability` de schema.org lo comparan con cero y nada más.
2. **`AgregarVariante` ya escribía la `ENTRADA` en el libro** desde que se creó. La columna no
   guardaba ningún dato que el libro no tuviera, salvo en variantes escritas por fuera de ese camino.

## Decisión

### 1. `variante.existencia` se borra

Del dominio, de la entidad JPA, del puerto (`actualizarExistencia` desaparece) y de la tabla
(`V59`). Quién tiene existencia lo dice `Inventario` y solo él.

La invariante de "no puede ser negativa" no se pierde: la sostiene `Inventario.registrarAjuste`,
que se niega a dejar el saldo total bajo cero. Estaba duplicada.

### 2. La API publica un booleano, no un número

`VarianteRespuesta.existencia: int` pasa a `disponible: boolean`. Dos razones:

- **Es lo único que la vitrina usa.** Publicar el conteo exacto era darle el nivel de inventario a
  cualquiera que mirase la red, sin que ninguna pantalla lo necesitara.
- **Un número envejece peor.** Entre el render y el clic, tanto el número como el sí/no pueden
  quedar viejos; la diferencia es que el número *aparenta* una precisión que no tiene. Lo que de
  verdad protege la venta es que el servidor revalida al reservar, con bloqueo pesimista, y eso no
  cambia.

El costo está aceptado y escrito: el día que se quiera "¡solo quedan 2!" hay que volver a tocar el
contrato. Es un cambio aditivo, no una reescritura.

### 3. Se calcula al leer, y no se materializa

`BuscarProductos` y `VerFichaDeProducto` hacen dos lecturas: los productos de la página y, con los
ids de sus variantes, sus libros (`RepositorioInventario.buscarPorVarianteIds`, sin bloqueo).

**Una proyección materializada no sirve aquí**, y conviene tener escrito por qué, porque es el
argumento que alguien va a querer repetir: **el disponible depende de `ahora`**. Una reserva vence
sola, y en ese instante la unidad vuelve a estar a la venta sin que nadie escriba nada. Una columna
se quedaría vieja exactamente igual que la que se está borrando, solo que por otro motivo.

El precio es el mismo que `ADR-0049` ya dejó escrito para el panel: la consulta trae el histórico de
movimientos de las variantes de la página. Acotado a una página es pagable; el día que una variante
acumule miles de movimientos, lo que hace falta es un corte de saldo en el libro, no una columna en
el catálogo.

### 4. La migración abre libro donde no lo había, y no cuadra hacia arriba donde sí

`V59` crea el `inventario` y una `ENTRADA` con la cifra de la columna **solo** para las variantes
sin libro, porque para ésas la columna era el único sitio donde estaba el dato.

**No toca** las que ya tienen libro con un saldo menor. Esa diferencia no es un dato perdido: es la
venta que el libro registró y la columna no vio. Cuadrar hacia arriba sería resucitar el error.

### 5. El descuadre muere, y el aviso del panel se reapunta

La pantalla de existencias pasa de tres cifras a dos y pierde la marca de descuadre: ya no hay dos
números que puedan discrepar. El aviso del panel, que contaba descuadradas, ahora cuenta lo que sí
le puede pasar a un comprador — variantes publicadas sin una sola unidad en el libro.

Es una decisión y no una consecuencia mecánica: la alternativa era quitar el aviso. Se conserva
porque el hueco que deja no es el mismo que llenaba, pero es real y nadie más lo vigila.

### 6. `SembradorInventario` desaparece

Abría el libro de cada variante leyendo su columna. Sin columna, el único que sabe cuántas unidades
siembra es quien las siembra, así que el trabajo se hace dentro de `SembradorCatalogo`, en el mismo
método que escribe la variante.

## Consecuencias

- La vitrina dice la verdad por primera vez desde la Fase 2. Comprobado con datos reales:
  `TS-CEL-AUR-128` declaraba 3 en el catálogo con el libro en 0, y ahora responde agotada.
- El contrato público cambia (`existencia` → `disponible`), con el cliente TypeScript regenerado.
- Dos lecturas por página de catálogo en vez de una.
- Once dobles de prueba tocados por el método nuevo del puerto, el mismo peaje de siempre.

## Alternativas rechazadas

- **La opción B de `ADR-0049`** —la columna como proyección que recalculan todos los que mueven
  inventario—, por lo dicho en la decisión 3 y porque obliga a tocar el camino del pago, donde un
  error se paga con un pedido.
- **Publicar el número desde el libro**, manteniendo `existencia: int` con el saldo disponible. Es
  la opción más conservadora con el contrato y la que peor envejece: sigue exponiendo el inventario
  y sigue prometiendo una precisión que la siguiente compra invalida.
