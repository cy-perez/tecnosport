# ADR 0030. La foto de catálogo se uniforma por estilo, no por píxeles

Fecha: 2026-09-15. Estado: aceptada.

## Contexto

`fotos-estudio-degradado` nació con tres cifras clavadas: lienzo de 2000×2000,
producto al 85 % —1700 px de lado mayor— y un resplandor blanco sobre un fondo
que iba de `#F4F4F4` a `#D2D2D2`. La regla que lo sostenía es buena y no se
discute aquí: **se cambia el entorno, nunca el producto**, porque el Estatuto del
Consumidor (Ley 1480 de 2011) exige que lo que se muestra corresponda a lo que se
entrega.

Lo que nadie había comprobado es qué pasaba cuando el material no daba para esas
cifras. Al medir las 122 fotos de `catalogo/fotos/crudas/` con el listón de 1700
px, el resultado fue que **solo 15 de los 39 productos con fotos tenían alguna
foto publicable**: 46 fotos quedaban en REPETIR por resolución y 38 más en
REVISAR. Open Icecat sirve la miniatura, no el original —se comprobó volviendo a
descargar la URL de `honor-2i-01` y llega otra vez a 276×294 px—, así que no era
un fallo del descargador y no había nada que volver a bajar.

Es decir: dos de cada tres productos se quedaban fuera del catálogo por no
alcanzar un número que nadie había justificado contra la web que los muestra.

Y esa web no pide ese número. `--ancho-max` es 1200 px y la ficha de producto es
de dos columnas, así que la imagen se pinta a unos **570 px CSS** —unos 1140 px
reales en una pantalla de densidad 2×—. Los 2000 px solo servirían para un zoom
que la galería no tiene. Encima no hay `IMAGE_LOADER` configurado, así que hoy el
navegador se descarga la maestra entera para pintar una tarjeta de 300 px.

En paralelo apareció un segundo problema, de otra naturaleza: los renders de
fabricante traen adornos que no son el producto. Los destellos de «Galaxy AI» del
`samsung-galaxy-s25-ultra` entraban en la máscara del recorte, salían en la foto
final y además estiraban la caja del producto, encogiendo el encuadre.

## Decisión

**La uniformidad del catálogo es de estilo —fondo, encuadre, sombra—, no de
número de píxeles.** De ahí salen tres decisiones:

1. **El lienzo lo decide el material de cada producto**, entre los escalones 2000,
   1600, 1200, 1000, 800, 600, 480, 400 y 320 px: el mayor que su mejor foto
   alcance sin pasar de 1,25× de ampliación. Es posible porque el degradado, la
   sombra y el enfoque ya se escalan con el lienzo, así que un producto a 1200 se
   ve idéntico a uno a 2000 dentro del `aspect-square` de la ficha.

   **Un producto sin material no se descarta**: la escala baja hasta 320 px y se
   publica al máximo que ese material permita, con todos los criterios aplicados
   y sus avisos intactos. Vale más una foto pequeña bien encuadrada, con el mismo
   fondo que las demás, que un hueco en el catálogo.

2. **El estilo se mide, no se estima.** El fondo pasa a `#FFFFFF → #A5A5A5` con
   rampa lineal desde el 26 % del radio, y el resplandor blanco se sustituye por
   una sombra de contacto negra al 63 %, σ 58 px, desplazada 14 px hacia abajo.
   Los valores salen de medir una imagen de referencia —ajuste del degradado con
   rms de 1,0 niveles sobre 28 puntos, y de la sombra con rms de 5,2
   reconstruyéndola desde la máscara—, no de mirarla y aproximar. Esto revierte
   el «sin sombra por diseño del catálogo» que el `config.json` daba por firme.

3. **De una foto se quita lo que está al lado del producto, nunca lo que está
   encima.** Un adorno es una isla que cumple tres condiciones a la vez: separada
   del cuerpo principal, menor del 15 % de su área, y a 20 o más de distancia en
   el plano a\*b\* —un color que no aparece en el cuerpo—. Los umbrales salen de
   medir el catálogo y los dos grupos no se solapan ni de lejos: los destellos
   quedan a 36–72 de distancia ocupando el 0,04–0,41 %; las piezas legítimas —el
   segundo audífono, el estuche, el JBL— quedan a 0,3–2,4 ocupando del 38 al
   100 %.

   El texto incrustado **en la pantalla del equipo** —«Galaxy S25 Ultra» sobre el
   propio producto— no se toca. Borrarlo sería alterar el producto, que es la
   línea que esta skill no cruza. Si ese texto no es publicable, la salida es otra
   foto, no un retoque.

## Alternativas

**Mantener el listón de 1700 px y pedir las fotos que faltan.** Es la solución de
raíz y sigue siendo la buena para los 10 productos sin material real —7 de ellos
JBL, o sea dos proveedores a los que pedirles el paquete—. Pero como política
general dejaba 24 de 39 productos sin publicar mientras tanto, a cambio de una
resolución que la ficha no usa.

**Ampliar con superresolución.** Descartada. Un modelo de SR inventa textura,
afila logos que estaban borrosos y puede deformar un texto del producto: es
exactamente lo que la regla de la skill prohíbe, y bajo la Ley 1480 un detalle
«mejorado» es una devolución en potencia. El escalado Lanczos que ya se hace
interpola sin inventar, y hasta ahí llega lo defendible.

**Dejar el producto a su tamaño real dentro de un lienzo de 2000.** No amplía
nada, pero rompe el encuadre: un producto diminuto en un lienzo grande se ve
distinto de todos los demás, que es justo lo que la uniformidad quiere evitar.

**Quitar los adornos a mano con `--excluir`.** Ya se podía, y funciona. Pero
obliga a mirar foto por foto un problema que tienen todos los renders de una
marca, y el flujo de inventario procesa lotes de más de cien.

## Consecuencias

- **De 15 a 28 los productos publicables a 1200 px o más**, de 39. Los 11
  restantes salen igual, al máximo que su material permita.
- **El reporte guarda el lienzo foto a foto**, y `verificar.py` mide el encuadre
  contra ese lienzo y no contra uno global. Una carpeta de salida deja de exigir
  un lienzo único, que era lo que impedía mezclar productos.
- **El catálogo ya procesado con el estilo anterior no es compatible con el
  nuevo.** No hay ninguno: cuando se tomó esta decisión no había fotos
  publicadas. Si las hubiera, habría que reprocesarlas todas, porque medio
  catálogo con resplandor blanco y medio con sombra es peor que cualquiera de los
  dos.
- **La detección de adornos distingue por color, así que puede equivocarse** con
  un producto que tenga una pieza pequeña de un color que no aparece en su
  cuerpo. Por eso el reporte anota siempre cuántos elementos se quitaron, con su
  distancia y su área, y `adornos_quitar=false` lo apaga. Una limpieza automática
  que no deja rastro no es aceptable en algo que se publica.
- **Las variantes por ancho siguen sin servir de nada hasta que se configure un
  `IMAGE_LOADER`** en Angular. Generar 480, 800 y 1200 px no cambia lo que
  descarga el navegador mientras `NgOptimizedImage` reciba la URL tal cual. Esa
  decisión queda abierta y es independiente de esta.
- El paso `organizar_imagenes.py` de `listas-de-proveedor`, que reordenaba la
  salida plana en una carpeta por producto, **queda solapado** con la salida
  nativa `--por-producto`. Las dos estructuras difieren en dónde va la maestra
  (`<producto>/maestra/` contra `<producto>/2000/`). Unificarlas es una decisión
  pendiente, no cerrada aquí.
