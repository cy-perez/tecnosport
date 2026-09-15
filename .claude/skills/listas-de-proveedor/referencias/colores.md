# De emojis a colores publicables

En las listas el color se marca con emojis al final de la línea: `🩵🖤` o
`🖤💙 💚⚪`. Cada emoji es una unidad disponible en ese color.

## Traducción base

| Emoji | Familia |
|---|---|
| ⚫ 🖤 | Negro |
| ⚪ 🤍 | Blanco |
| 🔵 💙 | Azul |
| 🩵 | Azul claro |
| 🩶 | Gris |
| 💜 | Morado |
| 💚 | Verde |
| 💛 | Amarillo / dorado |
| ❤️ | Rojo |
| 🩷 | Rosado |
| 🧡 | Naranja |
| 🤎 | Café / bronce |

## La familia no es el nombre comercial

"Negro" no se publica como Negro si el fabricante lo llama distinto. El paso que
falta es cruzar la familia con la paleta oficial del modelo y usar el nombre real:

- `🩶` en un iPhone Pro suele ser Titanio Natural o Titanio Plata, no "Gris".
- `💛` en un iPhone puede ser Oro, Titanio Dorado o Amarillo según la generación.
- `🩵` en un Galaxy S puede ser Titanium Icy Blue o Light Blue.

Busca la paleta oficial del modelo y asigna cada emoji al color más cercano. Si la
paleta tiene dos tonos que caen en la misma familia y no hay cómo distinguirlos
—dos azules, por ejemplo— no adivines: deja los dos como opción y pregúntale al
proveedor cuál está mandando. Publicar el tono equivocado es una devolución.

## Cuando la lista no trae ningún color

Es lo normal: en una lista real de 121 productos solo 2 líneas traían emojis.
La premisa del negocio (15/09/2026) es **asumir disponibles todos los colores de
la ficha oficial del producto**, en vez de dejar el producto sin variantes o
esperar una respuesta del proveedor que bloquea la publicación.

Dos condiciones para que la premisa no se convierta en una mentira:

- **Los emojis mandan sobre la premisa.** Si la línea dice `🖤💙`, el producto
  tiene dos variantes y no las seis de la paleta. La premisa solo cubre el vacío.
- **Queda dicho de dónde salió.** En `supuestos` del producto se anota que los
  colores vienen de la ficha y no de la lista, para que quien carga el
  inventario sepa que esas cantidades hay que confirmarlas antes de prometer
  entrega.

## Cómo queda en el sitio

Un modelo con cuatro colores es **un producto con cuatro variantes**, no cuatro
productos. El color va en la variante y en `colores_oficiales`, nunca en el
título. Si en la lista un color aparece una sola vez, es una unidad: sirve para
cargar el inventario, no para prometer disponibilidad.

## Emojis que no son colores

Los decorativos de sección (🍎 ✈️ 💥 🔅 🐦‍🔥) y las viñetas de producto no cuentan
como color. El script ya los ignora, pero al revisar a mano conviene tenerlo
presente.
