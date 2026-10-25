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

**Los scripts la aplican desde el 22/10/2026**: sin emojis, `colores_sugeridos`
(`scripts/colores.py`) devuelve la paleta completa, y así la manda la exportación
en `coloresSugeridos`: el panel la premarca y quien revisa el borrador solo quita
lo que no haya. Hasta esa fecha la premisa estaba escrita aquí y ningún script la
aplicaba: en la lista del 22/10 62 de 63 configuraciones llegaban al panel sin un
solo color premarcado. La constancia queda en `supuestos` como «colores
sugeridos: la lista no marca colores…», entre los de la lista y no en la base,
porque es un hecho de esa lista y no del modelo. Con emojis, se sugieren solo los
de la paleta que nombran su familia.

**La paleta sale de la ficha de la marca.** Si `fichas-marca.json` trae
`"paleta": [...]` para el modelo, `redactar_fichas.py` la lleva a
`colores_oficiales` cuando estén vacíos, con el supuesto «colores: la paleta que
publica …». No pisa una paleta que ya exista.

**Y si la marca no la da, se busca hasta tenerla** (ADR-0086, 25/10/2026): un
modelo sin paleta no llega al panel. El orden de fuentes está en el paso 4 de
`SKILL.md`; lo que se escribe con `scripts/paletas.py fijar` lleva su fuente en
`supuestos_paleta` («colores: según …, consultado el …»).

**Un solo acabado es un color.** En la base del 25/10/2026 había cuatro modelos
con la paleta vacía porque «el fabricante no publica variantes de color»: los
AirPods Pro 3, las dos Nintendo Switch 2 y el proyector Xiaomi L1. Los cuatro se
venden en un único acabado, y ese acabado es su paleta —Blanco, Negro, Negro,
Negro—. Con la paleta vacía la ficha no decía el color y cada compra se
preguntaba por WhatsApp; con un color, el sitio sabe que no hay nada que
confirmar.

## Cómo queda en el sitio

Un modelo con cuatro colores es **un producto con cuatro variantes**, no cuatro
productos. El color va en la variante y en `colores_oficiales`, nunca en el
título. Si en la lista un color aparece una sola vez, es una unidad: sirve para
cargar el inventario, no para prometer disponibilidad.

## Emojis que no son colores

Los decorativos de sección (🍎 ✈️ 💥 🔅 🐦‍🔥) y las viñetas de producto no cuentan
como color. El script ya los ignora, pero al revisar a mano conviene tenerlo
presente.
