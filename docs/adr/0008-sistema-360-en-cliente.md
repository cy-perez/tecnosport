# ADR 0008. Rotación 360 con captura y procesamiento en el cliente

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
Se quiere mostrar los productos girando. Las opciones eran: contratar fotografía
360 profesional, comprar un plato giratorio con software propietario, procesar las
imágenes en el servidor, o guiar la captura desde el teléfono y procesar en el
navegador.

## Decisión
Captura asistida desde el teléfono y procesamiento en el cliente. El backend solo
emite URL firmadas, valida el resultado y guarda metadatos. El visor es una
secuencia de imágenes controlada por el desplazamiento del dedo o del ratón.

Número de fotogramas parametrizable: 4 como mínimo publicable, 8 como objetivo,
16 como máximo. Orden antihorario desde el frontal, fijado para todo el catálogo.

## Alternativas
Fotogrametría o modelo 3D: desproporcionado para el problema y caro de producir
por SKU. Procesamiento en el servidor: agrega costo de cómputo, latencia y una
dependencia más para algo que el navegador hace bien.

## Consecuencias
El resultado depende de las condiciones de captura: fondo claro y uniforme y luz
pareja. El recorte automático falla con fondo desordenado, y por eso existe el
recorte manual como salida.

El nivelador depende de sensores que iOS puede negar, así que el flujo tiene un
modo degradado obligatorio sin nivel.

La escala común a todo el set es la parte crítica: si cada fotograma se escala por
separado, el producto crece y encoge al girar. Se calcula una sola vez por set y
tiene prueba propia.

Un set de 8 fotogramas son alrededor de 1,6 MB, que no pueden competir con el
contenido principal por el ancho de banda inicial. Solo el fotograma 0 es
prioritario.
