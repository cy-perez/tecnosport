# Captura asistida y visor de rotación 360

Dos piezas independientes que comparten un contrato: un **asistente de captura**
que produce N fotogramas consistentes desde un teléfono, y un **visor** que los
convierte en una rotación al arrastrar.

Todo el procesamiento ocurre en el cliente. El backend solo emite URL firmadas,
valida el resultado y guarda metadatos. No hay recorte, ni alineación, ni
composición en el servidor.

## Decisiones previas

**Cuántos fotogramas.** Con 4 tomas cada arrastre salta 90 grados y se percibe
como un carrusel, no como un giro. El número es parametrizable por producto:

| Fotogramas | Paso | Uso |
|---|---|---|
| 4 | 90 grados | Mínimo publicable. Frontal, lateral derecho, posterior, lateral izquierdo |
| 8 | 45 grados | Objetivo. Es donde el arrastre empieza a sentirse continuo |
| 16 | 22,5 grados | Solo para productos de alto valor. Cuadruplica el trabajo de captura |

El orden de captura es **antihorario visto desde arriba**, empezando por el
frontal, y es el mismo orden del arreglo que consume el visor. Esa convención se
fija una vez y no se discute por producto: si la mitad de los sets gira al revés,
el catálogo se ve roto.

**El nivelador tiene un límite real.** `DeviceOrientationEvent` requiere HTTPS y,
en Safari de iOS, un permiso explícito solicitado desde un gesto del usuario. Si
el permiso se niega, o el navegador no expone el sensor, **no hay nivel**. El
asistente debe funcionar igual, con la guía visual sola y una advertencia. Un
flujo que se bloquea sin sensor es un flujo que no se puede usar en medio
teléfono del mercado.

**Esto no es fotogrametría.** No se reconstruye un modelo 3D ni se interpola
entre fotogramas. Es una secuencia de imágenes que se muestra según el
desplazamiento del dedo. Simple, robusto y suficiente.

## Asistente de captura

Vive en `apps/web/src/app/features/captura360`, detrás de autenticación de
administrador, y se usa desde el teléfono. Es parte de la PWA: se puede instalar
y abrir como una app.

### Flujo

1. **Preparación.** Se elige producto y variante, y el número de fotogramas. Se
   muestran las condiciones mínimas: fondo claro y uniforme, luz pareja, producto
   centrado, teléfono a la misma distancia en todas las tomas.
2. **Permisos.** Cámara primero. Sensores después, con un botón explícito, porque
   iOS lo exige desde un gesto. Si alguno se niega, se explica qué se pierde y se
   continúa en modo degradado.
3. **Captura secuencial.** Por cada paso: nombre de la toma (frontal, 45 grados,
   lateral derecho, y así), silueta guía, indicador de nivel y barra de progreso.
   El obturador se deshabilita si el teléfono está fuera de tolerancia.
4. **Revisión por toma.** Cada fotograma se acepta o se repite antes de pasar al
   siguiente. Repetir uno no reinicia la secuencia.
5. **Procesamiento local.** Recorte, normalización y compresión en el navegador.
6. **Revisión del set completo.** Se previsualiza la rotación con el mismo visor
   del sitio. Aquí se detecta el fotograma torcido, no después.
7. **Carga.** Subida directa a Cloud Storage con las URL firmadas, con progreso,
   reintento por fotograma y reanudación si se corta la conexión.

### Superposición de guía

- Silueta o cuadrícula central translúcida, alineada al centro del encuadre.
- La silueta **se conserva idéntica entre tomas**: es lo que garantiza que el
  producto ocupe el mismo espacio en todos los fotogramas.
- **Fantasma del fotograma anterior** a baja opacidad. Es la ayuda más útil de
  todas: alinear contra la toma previa es mucho más preciso que alinear contra
  una silueta genérica.
- Marco de proporción 1:1, que es la proporción final.
- La guía se dibuja con los tokens del sistema visual. Sobre la vista de cámara
  el contraste no está garantizado, así que las líneas llevan sombra o contorno
  de contraste; el ámbar no se usa como línea fina sobre imagen.

### Nivelador digital

- Lectura de `DeviceOrientationEvent` con suavizado, porque el dato crudo tiembla
  y un indicador nervioso es inutilizable.
- Tolerancia por omisión: 3 grados en `beta` y en `gamma`, configurable.
- Estado visible en tres niveles: fuera de rango, cerca, en rango. Nunca solo por
  color: también por texto y por forma, porque el color solo no es accesible.
- Fuera de tolerancia, el obturador se deshabilita y se dice por qué, en texto.
- **Modo degradado** sin sensor: obturador siempre habilitado, aviso permanente de
  que no hay nivel, y la guía visual sigue funcionando.

### Procesamiento local

Sobre `<canvas>`, antes de subir:

1. **Detección del recorte.** Se estima el color del fondo con las esquinas, se
   binariza por umbral de luminancia y se calcula el rectángulo que contiene al
   producto.
2. **Recorte con margen.** Se aplica el mismo margen relativo en todos los
   fotogramas.
3. **Encuadre uniforme.** El rectángulo se lleva a 1:1 centrado. **El factor de
   escala se calcula una sola vez para todo el set**, tomando el fotograma más
   ancho como referencia. Si cada fotograma se escala por separado, el producto
   crece y encoge al girar, que es el defecto más visible de un 360 casero.
4. **Salida.** 1000 x 1000 px, WebP con calidad 82, y JPEG de respaldo. Por debajo
   de 200 KB por fotograma.
5. **Verificación.** Si la detección de fondo falla, el asistente lo dice y ofrece
   recorte manual. Nunca sube un recorte que sabe que salió mal.

Límite honesto: el recorte automático funciona con fondo claro y uniforme. Con
fondo desordenado va a fallar, y por eso el paso 1 del flujo insiste en las
condiciones de captura y por eso existe el recorte manual.

### Consideraciones de dispositivo

- Trabajar sobre imágenes grandes en canvas consume memoria; se procesa un
  fotograma a la vez y se liberan los objetos intermedios.
- El procesamiento pesado va a un Web Worker para no congelar la interfaz.
- Wake Lock mientras dura la sesión de captura, para que la pantalla no se apague
  entre tomas.
- El set en curso se guarda localmente: cerrar el navegador por accidente no debe
  costar quince fotos.

## Visor de rotación

Componente `ts-visor-360` en `shared/`. Recibe un arreglo ordenado de imágenes y
nada más. No sabe de HTTP, no sabe de productos.

### Comportamiento

- Muestra el fotograma 0 inmediatamente. Esa imagen es la que cuenta para el LCP y
  es la única con `priority`.
- Arrastre con `PointerEvent`, que cubre ratón, dedo y lápiz con un solo camino de
  código. Nada de escuchar `mouse` y `touch` por separado.
- El desplazamiento horizontal se mapea a índice de fotograma. Sensibilidad
  relativa al ancho del contenedor: un giro completo es aproximadamente un ancho
  de arrastre, para que se sienta igual en teléfono y en escritorio.
- El índice es circular: pasado el último vuelve al primero.
- El arrastre vertical no gira, y no debe bloquear el desplazamiento de la página.
  `touch-action: pan-y` en el contenedor.
- Pista visual de que se puede girar: un indicador breve la primera vez, no un
  texto permanente encima de la imagen.

### Accesibilidad

Un visor de arrastre es inaccesible por naturaleza si no se resuelve a propósito:

- El contenedor es enfocable, con rol e instrucciones en texto traducido.
- **Flechas izquierda y derecha giran un fotograma**, Inicio y Fin van al frontal
  y al opuesto.
- Botones visibles de girar a izquierda y derecha, de 44 x 44 px. No todo el
  mundo puede arrastrar con precisión.
- Los fotogramas llevan `alt=""` porque son decorativos en conjunto; la
  descripción del producto vive en el texto de la ficha, no repetida ocho veces.
- Con `prefers-reduced-motion`, no hay giro automático de demostración.
- El visor nunca es la única forma de ver el producto: la galería normal sigue
  ahí.

### Carga

- Fotograma 0 con `priority`. El resto se precarga en segundo plano, después del
  evento de carga, y en orden de cercanía al fotograma actual.
- Durante la precarga el arrastre ya funciona: se muestra el fotograma más cercano
  disponible en vez de bloquear.
- Con `navigator.connection` en modo ahorro de datos o conexión lenta, no se
  precarga: se espera a que el usuario arrastre.
- Un set de 8 fotogramas a 200 KB son 1,6 MB. **Eso no se descarga antes del
  contenido principal.** Si el visor empuja el LCP por encima de 2,5 segundos,
  está mal implementado.

### En SSR

El componente se renderiza en el servidor con el fotograma 0 como imagen normal.
Los listeners y la precarga se activan solo después de hidratar, detrás de un
guardia de plataforma.

## Contrato entre las dos piezas

El asistente produce lo que el visor consume:

- N fotogramas, ordenados de 0 a N-1, orden antihorario desde el frontal.
- 1:1, 1000 x 1000 px, WebP con respaldo JPEG.
- Misma escala del producto en todos.
- Set completo o nada.

Si esa lista se cumple, el visor funciona. Si no, no hay lógica de visualización
que lo arregle.

## Pruebas

- Recorte y escala: funciones puras con imágenes de prueba conocidas, verificando
  el rectángulo y el factor de escala común. Es lo que más se rompe.
- Mapeo de desplazamiento a índice: función pura, incluidas las vueltas
  circulares y los valores negativos.
- Nivelador: función pura sobre lecturas de sensor simuladas, incluidos los datos
  ruidosos y el caso de sensor ausente.
- Componente del visor: teclado, botones, que el arrastre vertical no gire.
- Un recorrido de Playwright que captura con cámara simulada, sube y publica.
