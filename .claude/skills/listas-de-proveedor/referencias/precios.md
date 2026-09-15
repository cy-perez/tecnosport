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
