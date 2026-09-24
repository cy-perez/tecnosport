# Investigación del precio de mercado colombiano

## Qué se busca

El precio al que un cliente en Colombia compra hoy ese mismo producto, **nuevo,
con IVA incluido y en pesos**. No es el precio del proveedor ni el del exterior.

## Fuentes

Sirven, en orden de confianza: el sitio oficial de la marca en Colombia (Apple,
Samsung, Xiaomi), los grandes retail (Alkosto, Ktronix, Falabella, Éxito), las
tiendas de los operadores (Claro, Movistar, Tigo) y Mercado Libre Colombia
**solo** cuando el vendedor es tienda oficial.

No sirven: publicaciones de usados o reacondicionados, importadores informales,
precios de otro país convertidos, preventas, precios "desde" de combos con plan,
ni listados sin stock.

### La vitrina no es el marketplace, y mezclarlos falsea la categoría entera

Éxito, Olímpica y Jumbo venden en el mismo sitio dos cosas distintas: su
**inventario propio** —la vitrina, con garantía de la tienda— y los productos de
**vendedores del marketplace**, que son terceros. Los del marketplace tiran el
precio muy por debajo de la vitrina.

Usar un precio de marketplace como "precio de mercado" hace que el producto
parezca sin margen cuando sí lo tiene. En la lista del 12/09/2026 eso hundió los
parlantes JBL completos: con precios de marketplace la categoría daba **mediana
−1 %**, y contra la vitrina de Alkosto daba **+14 %**. El JBL Charge 6 pasó de
−20 % a +14 % sin que cambiara nada del producto.

La regla:

1. Si hay **retail de vitrina**, ese precio manda. Punto.
2. El marketplace se usa **solo** cuando ninguna vitrina tiene la referencia, y
   el producto queda marcado diciendo de dónde salió el número.
3. Los dos niveles **nunca se promedian juntos**.

`asignar_precios.py` ya aplica esta preferencia y escribe el nivel
(`inventario propio` o `marketplace`) en cada fuente. Lo que hay que cuidar es
que el corpus tenga vitrina de verdad, y ahí está el problema del punto
siguiente.

## Qué tienda se consulta cómo

| Tienda | Cómo se consulta |
|---|---|
| Éxito, Olímpica, Jumbo | `scripts/precios.py` — exponen su catálogo VTEX sin credenciales |
| **Alkosto** | Solo por navegador: no es VTEX y su buscador pinta los resultados desde el cliente |
| Ktronix, Falabella, Samsung | Requieren autorizar el dominio en la extensión del navegador |

Las tres de `precios.py` son cómodas porque responden JSON, pero **las tres
traen sobre todo marketplace** en tecnología. Por eso una corrida que solo use
`precios.py` termina con precios flojos: en la lista real, 51 de 83 precios
salieron de vendedores del marketplace. Alkosto en el navegador corrigió eso.

### Receta de Alkosto

La búsqueda es una URL directa, y los resultados se leen del DOM ya pintado:

```
https://www.alkosto.com/search?text=<consulta+con+espacios+en+mas>
```

```js
// título y precios de cada tarjeta; el primer precio es el vigente
for (const a of document.querySelectorAll('a[href*="/p/"]')) { … }
```

Hay que esperar a que rendericen (unos 3 o 4 segundos): recién cargada, la
página solo trae la navegación. Alkosto muestra **precio vigente y precio
antes**; el de mercado es el vigente, porque es lo que paga hoy un cliente, y el
de lista se anota al lado.

**No extraigas las llaves de API embebidas del sitio para consultar su buscador
por detrás.** Aunque estén a la vista en el HTML, usarlas es entrar por una
puerta que la tienda no abrió. Se navega el sitio como lo navega una persona.

#### Cómo sacar el lote sin morir en el intento

Las tarjetas son `.product__item`; su `innerText` trae el nombre en la primera
línea y los precios como `$1.699.900` (el primero es el vigente). Tres cosas que
cuestan una tarde si se descubren sobre la marcha:

- **Una consulta por navegación.** Los resultados los pinta Algolia en el
  cliente, así que no sirve pedir el HTML: hay que navegar y esperar. Con
  `browser_batch` caben unas diez consultas por llamada; si la llamada se pasa
  de tiempo, los pasos igual se ejecutaron y basta con mirar el acumulado.
- **Acumular en `localStorage`.** Cada consulta guarda su resultado bajo la
  llave de la búsqueda y devuelve solo un contador. Así una navegación no borra
  lo anterior y la salida de cada paso queda corta.
- **La salida de JavaScript se trunca cerca de los 1.000 caracteres.** Para
  sacar los ~30 KB del lote hay que leerlos por trozos con `slice`, doce trozos
  por `browser_batch`. No intentes el portapapeles (la pestaña no tiene foco) ni
  un POST a un servidor local: la CSP de Alkosto lo bloquea y congela la
  pestaña.

### El buscador de Alkosto es difuso, y eso no se arregla con código

Devuelve vecinos con mucha soltura: al pedir «POCO X8 Pro Max» contesta iPhones.
El emparejamiento final lo hace una persona, producto por producto, y queda
escrito en `catalogo/alkosto-vitrina.json` con el nombre exacto de la tarjeta.
Cuatro trampas que aparecieron en la lista del 12/09/2026:

| Lo que devuelve | Por qué no sirve |
|---|---|
| `Note 15 Pro 256GB 5G + Power Bank 165W` | es un combo; el precio no es el del celular |
| `Celular Reacondicionado REDMI Note 15 Pro` | reacondicionado, que el mercado paga menos |
| `Juego NINTENDO SWITCH 2 Mario Kart World` | es el **juego**, no la consola |
| `PARTY BOX ON THE GO 2` | es la generación siguiente del On-The-Go Essential |

## Las tiendas VTEX tienen dos manías

- **Responden 400 si la búsqueda trae `"` o `+`.** Las pulgadas (`8.7"`) y los
  modelos con plus (`A11+`) tumbaban la consulta de las tres tiendas a la vez, y
  el síntoma es un montón de `HTTP Error 400` sin explicación. `sanear()` los
  quita sin tocar el número.
- **Devuelven una fila por color.** El mismo producto en negro y en gris, al
  mismo precio y en la misma tienda, es **una** observación. Contarlas aparte le
  daba a Éxito dos votos contra uno de Alkosto y movía la mediana.

## Dos errores de emparejamiento que se ven como precios normales

Ninguno de los dos hace ruido: el número sale, parece razonable y está mal.

1. **La capacidad que se lee es la RAM.** En `POCO F8 Pro 5G 12GB RAM 256GB` la
   primera capacidad del título son los 12 GB de RAM, no los 256 de disco, así
   que un listado de 512GB —que también dice 12GB— pasaba el filtro. Hay que
   quitar la RAM de los dos lados antes de comparar.
2. **La potencia no es una capacidad.** `Xiaomi Power Bank 10.000 mAh 165W`
   emparejaba con una Awei de 10000 mAh 22.5W y con una Xiaomi Magnetic de
   5000 mAh: la mediana daba 119.900 cuando la vitrina la vende a **249.900**.
   Donde el título trae vatios o miliamperios, mandan ellos y no la capacidad.
   Las power bank dejaron de publicarse el 24/09/2026, pero la regla se quedó:
   nunca fue de esa categoría —los vatios identifican un parlante igual que
   identificaban un cargador—.

## El precio de Alkosto ancla, porque se verificó a mano

`asignar_precios.py` empareja los de VTEX con una expresión regular y los de
Alkosto los puso una persona. Cuando hay precio de Alkosto, las ofertas de
vitrina que se alejen más del 35 % de él se dejan fuera: casi siempre son otro
producto. Y lo que una revisión a mano rechaza entero va a
`catalogo/precios-descartados.json` con el motivo, para que el producto quede
**sin** precio de mercado en vez de con uno inventado.

## Señal de alarma: generación saliente

Si el retail masivo **no tiene la referencia pero sí tiene la siguiente**, el
proveedor está ofreciendo modelo saliente. Casi siempre eso viene con precio de
proveedor por encima de lo que el mercado ya paga.

Pasó con cuatro productos en la misma lista: el JBL Grip, el JBL Xtreme 4, el
JBL PartyBox 320 y el Motorola Edge 50 Fusion. Alkosto ya solo vendía el
Xtreme 5 y los Edge 60 y 70 Fusion. Los cuatro quedaron por debajo del costo.

Cuando lo detectes, no basta con dejar el precio de marketplace y seguir:
anótalo diciendo que la vitrina ya cambió de generación, porque cambia la
decisión del negocio.

## Método

1. Busca el título ya confirmado más "precio Colombia". Para variantes de
   capacidad, busca la capacidad exacta: un 256GB y un 512GB no comparten precio.
2. Reúne **al menos 3 precios** de tiendas distintas. Si solo aparecen 1 o 2,
   márcalo como precio con poca evidencia en `revisar`.
3. Usa la **mediana**, no el promedio aritmético: una promoción agresiva o un
   listado inflado mueven el promedio y la mediana los resiste.
4. Descarta los valores que se salgan más de un 35% de la mediana y anota por qué.
5. Registra cada fuente en `fuentes_precio` con tienda, precio y enlace. Un precio
   sin fuente no se puede defender cuando el negocio pregunte de dónde salió.

## Verificaciones antes de cerrar

- **Promedio por debajo del precio de lista**: no hay margen. Márcalo y avisa; no
  lo publiques sin que el negocio lo decida.
- **Margen mayor al 60%**: casi siempre es un error de lectura del precio de lista
  (recuerda el ×1.000) o una referencia distinta a la que se comparó. Revísalo.
- **Equipos activados o sin garantía de importador**: el mercado los paga menos
  que a uno de vitrina. Anótalo aunque el precio de referencia sea el de uno nuevo.
- **Modelos recién salidos**: el precio se mueve rápido, deja anotada la fecha de
  consulta.

## Lo que no decide esta skill

El precio de venta del sitio. La skill entrega el referente de mercado y la
ganancia que resultaría de vender a ese valor. Fijar el precio final —igual, por
debajo para competir, o por encima porque hay servicio y garantía local— es
decisión del negocio.
