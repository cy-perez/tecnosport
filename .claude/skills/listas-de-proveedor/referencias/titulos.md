# Estándar de títulos

Un título sirve para dos cosas: que el cliente entienda qué está comprando en tres
segundos y que el producto aparezca en las búsquedas. Por eso lleva marca, modelo
oficial y los datos que diferencian una variante de otra, en ese orden, sin
adornos.

## Fórmula

```
{Marca} {Modelo comercial oficial} {Capacidad} {Atributo diferenciador}
```

- Máximo **70 caracteres**.
- Sin mayúsculas sostenidas, sin signos de admiración, sin "oferta", "nuevo",
  "original", "envío gratis" ni emojis.
- **El color no va en el título**: es una variante del mismo producto.
- La capacidad se escribe `128GB`, `256GB`, `512GB`, `1TB`.
- La RAM solo cuando distingue variantes: `12GB RAM 256GB`, y siempre la
  **física**: de `(8+8+256)` se publica 8GB RAM, nunca 16.
- La red (`4G` / `5G` / `WiFi`) va en el título cuando el mismo modelo se vende en
  las dos versiones, que es lo normal en gama media.

## Por categoría

| Categoría | Patrón | Ejemplo |
|---|---|---|
| Celulares | Marca + modelo + red + RAM física + capacidad | `Samsung Galaxy A17 5G 8GB RAM 256GB` |
| Celulares Apple | Marca + modelo + capacidad + eSIM si aplica | `Apple iPhone 17 Pro Max 256GB eSIM` |
| Tablets | Marca + línea + pantalla + conectividad + RAM + capacidad | `Xiaomi Pad 2 11" WiFi 8GB RAM 256GB` |
| Tablets | Marca + modelo + pantalla + capacidad + conectividad | `Apple iPad Air 11" 256GB Wi-Fi` |
| Relojes | Marca + modelo + tamaño de caja | `Samsung Galaxy Watch 8 Classic 46mm` |
| Audífonos | Marca + línea + modelo | `Samsung Galaxy Buds Core` |
| Consolas | Marca + consola + capacidad o bundle | `Sony PlayStation 5 1TB` |
| Computadores | Tipo + marca + pantalla + procesador + RAM + disco | `Portátil Asus 15.6" Ryzen 5 7520U 8GB RAM 512GB SSD` |
| Proyectores | Marca + modelo + resolución | `Xiaomi Proyector L1 Full HD` |
| Parlantes | Marca + línea + modelo | `JBL Charge 6` |

Las categorías que el parser reconoce pero **no se publican** —cargadores,
cables, power bank y accesorios de consola— no llevan patrón de título: nunca
llegan a una ficha. Su nombre se deja tal como lo escribió el proveedor, que es
como se le muestra en la hoja de descartados para que sepa de qué línea se está
hablando. Ver las reglas 5, 14 y 17 del `SKILL.md`.

## Confirmar el nombre antes de titular

Las listas abrevian y se equivocan. Verifica siempre contra el sitio del
fabricante o de un retail grande:

| En la lista | Título correcto |
|---|---|
| `SAMSUNG BAND FIT 3` | `Samsung Galaxy Fit3` |
| `WACH 8 40MM` | `Samsung Galaxy Watch 8 40mm` |
| `HONOR 2i` | `Honor Watch Choice 2i` |
| `PS5 1T` | `Sony PlayStation 5 1TB` |
| `PROYECTOR L1` | `Xiaomi Smart Projector L1 Full HD` |
| `NOTE 15 4G` bajo *XIAOMI* | `Xiaomi Redmi Note 15 4G` (se asume la línea Redmi) |
| `X8 PRO MAX` bajo *XIAOMI* | `Xiaomi POCO X8 Pro Max` (se asume la línea POCO) |
| `SAMSUNG A11 7" WIFI` | `Samsung Galaxy Tab A11 8.7" WiFi` |
| `CUBO BECLAD (SAMSUNG)` | es un cubo Beclad **compatible** con Samsung, no Samsung |
| `TIPO C - LIGHTNING` | `Cable USB-C a Lightning`, agregando longitud y potencia |

### La sección "XIAOMI" mezcla Xiaomi y Redmi

El proveedor encabeza toda la sección con la marca madre, pero el fabricante
publica varias de esas referencias en la línea Redmi, con otro nombre comercial
y otra ficha. El parser ya corrige las conocidas (`SUBMARCA_XIAOMI` en
`parsear_lista.py`) y deja la nota en `supuestos`. La tabla, verificada contra
mi.com/co el 15/09/2026:

| En la lista | Referencia real | ¿Cambia de línea? |
|---|---|---|
| `XIAOMI WATCH 5 ACTIVE` | `Xiaomi Redmi Watch 5 Active` | sí, a Redmi |
| `XIAOMI WATCH 5 LITE` | `Xiaomi Redmi Watch 5 Lite` | sí, a Redmi |
| `XIAOMI BUDS 6 PLAY` | `Xiaomi Redmi Buds 6 Play` | sí, a Redmi |
| `XIAOMI BUDS 6 ACTIVE` | `Xiaomi Redmi Buds 6 Active` | sí, a Redmi |
| `XIAOMI BUDS 8 ACTIVE` | `Xiaomi Redmi Buds 8 Active` | sí, a Redmi |
| `XIAOMI PAD 2` (9.7", 11", Pro) | `Xiaomi Redmi Pad 2` | sí, a Redmi |
| `XIAOMI BAND 10` | `Xiaomi Smart Band 10` | no, solo faltaba «Smart» |
| `XIAOMI BAND 11 ACTIVE` | `Xiaomi Smart Band 11 Active` | no, solo faltaba «Smart» |

**No generalices la regla.** En el mismo catálogo, las Smart Band, el Watch S4,
los Buds 6 a secas y las power bank **sí** son Xiaomi. Ante una referencia que
no esté en la tabla, manda el catálogo oficial: ver `fichas-tecnicas.md`.

### Nombres que la ficha del fabricante corrigió

Ninguno de estos se adivina; salieron de la ficha oficial o de la vitrina:

| En la lista | Título correcto | Dónde se confirmó |
|---|---|---|
| `JBL PARTYBOX 320` | `JBL PartyBox Stage 320` | Icecat |
| `JBL EXTREME 4` / `5` | `JBL Xtreme 4` / `JBL Xtreme 5` | Icecat (la marca escribe Xtreme) |
| `HONOR CHOISE X7E` | `Honor Choice Earbuds X7e` | Icecat |
| `JBL DIADEMA TUNE 730` | `JBL Tune 730BT` | Icecat |
| `JBL BARRA DE SONIDO CINEMA SB 180` | `JBL Cinema SB180` | Icecat |
| `SAMSUNG BUDS 4` | `Samsung Galaxy Buds4` | vitrina de Alkosto |
| `HONOR X8B WiFi 11"` | `Honor Pad X8b 11" WiFi` | vitrina de Alkosto |
| `JBL ON THE GO ESSENTIAL + MIC` | `JBL PartyBox On-The-Go Essential` | vitrina de Alkosto |

### Las pulgadas de la lista mienten seguido

Es el error más caro de las tablets y ya apareció cuatro veces. El tamaño se
confirma **siempre** contra la ficha oficial antes de titular:

| La lista dice | El fabricante dice |
|---|---|
| `SAMSUNG A11 7"` | 8,7" |
| `LENOVO TAB ONE 7"` | 8,7" |
| `LENOVO TAB PLUS 11"` | 11,5" |
| `XIAOMI PAD 2 PRO 11"` | 12,1" |

Cuando el proveedor no da para identificar la referencia exacta —pasa con
proyectores, parlantes y relojes económicos— no inventes el modelo:
deja el producto marcado y pregunta. Un título inventado genera devoluciones.
