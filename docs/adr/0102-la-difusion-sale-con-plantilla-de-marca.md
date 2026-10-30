# ADR-0102: lo que se publica en redes sale con plantilla de marca, en el texto y en la portada

Fecha: 2026-10-30
Estado: aceptada.
Relacionados: `adr/0069` (el carrusel entero), `adr/0098` (la plantilla de los correos),
`adr/0025` (los textos fuera del código), `docs/04-ui-marca.md`, `packages/marca/tokens.json`.

## Contexto

Una publicación en Facebook o Instagram salía así:

```
JBL Flip 6 - $449.900

Parlante portátil resistente al agua…

https://tecnosport.co/es/productos/jbl-flip-6

#parlantes #jbl #tecnosport
```

con las fotos de la ficha detrás, tal cual. Nada en el post —ni en la miniatura del perfil— decía
que era de Tecno Sport, y el texto no daba ninguna de las razones para comprar aquí: envío,
contraentrega, a quién escribir. Es lo mismo que les pasaba a los correos antes de `adr/0098`.

Y la misma falta de fondo: «desde» y «Enlace en la bio 🔗» estaban escritos dentro de
`ArmadorDePieDeFoto`, en `application`, en un solo idioma. La regla dura #4 vale para el backend
desde que se sacaron los textos de los correos, y la difusión se había quedado fuera.

## Decisión

### El texto

1. **Una plantilla de seis bloques**, en este orden y separados por una línea en blanco: la entrada
   (`✨ nombre · precio`, lo único que se ve antes del «ver más»), el primer párrafo de la
   descripción, hasta cuatro viñetas (`✔️`) sacadas de las de la descripción, las variantes
   (`🎨` el color, `▪️` lo demás), el bloque de confianza y el llamado; al final, los hashtags.
2. **El bloque de confianza** dice envío a todo Colombia, cómo se paga y el WhatsApp, y no
   promete nada que el checkout desmienta. **El envío** solo aparece si todas las variantes que
   se venden están medidas: sin medidas no hay envío a domicilio. **La contraentrega** solo si
   `PoliticaContraentrega.podriaAdmitirse` lo dice para ese producto —habilitada, línea no
   excluida, la variante más cara dentro del techo—: es la misma clase que decide en el checkout,
   no una copia de su regla. El flete y la cobertura de la ciudad no se conocen al publicar; por
   eso el texto la condiciona a la ciudad, y un producto a pocos miles del techo puede quedar
   fuera con el flete. Solo cuentan las variantes que se venden (`seVende`): ni una inactiva ni
   una agotada por el proveedor bajan el «desde» o ponen un color.
3. **Los textos viven en `difusion_es.properties` y `difusion_en.properties`**, con sus llaves en
   el enum `TextoDeDifusion` y el puerto `TextosDeDifusion`. `TextosDeDifusionMessageSource` los
   resuelve todos al arrancar, en los dos idiomas, y no levanta si falta uno o si un apóstrofe se
   come un marcador. Se publica en castellano: la cuenta es de Medellín y el enlace va a `/es/`.
4. **El pie no pasa de 2.200 caracteres**, el tope de Instagram: si no cabe, se van las viñetas
   desde la última, después el párrafo y por último las etiquetas. El precio, la confianza y el
   llamado se quedan. El panel no deja mandar a Instagram un texto escrito a mano más largo.
5. **Sin «negritas» Unicode** (𝗮𝘀í): los lectores de pantalla las deletrean o se las saltan.
6. El teléfono entra en `npm run datos-negocio`, como el del pie de los correos.

### La portada

7. **El carrusel abre con una portada de marca**, 1080 × 1350 (4:5, lo más alto que Instagram
   admite): la foto principal entera, sin recortar, sobre el color de su propio borde; debajo la
   banda grafito con la categoría, el nombre del producto (Archivo 700, hasta dos renglones con
   elipsis por palabra) y el logo con el dominio; al fondo la franja ámbar, el único acento. El
   chaflán a 45° va arriba a la izquierda, en la banda, y abajo a la derecha, en la franja, que es
   donde el manual lo pone. Detrás siguen todas las fotos de siempre, la principal incluida.
8. **La portada no lleva el precio.** Una imagen publicada no se edita: si el precio cambia, el
   post seguiría anunciando el viejo, y el precio anunciado obliga (Ley 1480). El texto sí se
   corrige desde Meta.
9. **La dibuja `PortadaJava2D`** con `Graphics2D`, del JDK, como `AjustadorDeImagenesJava2D`, y la
   deja en el bucket bajo `productos/{id}/redes/portada-{hash}.jpg`: la llave sale del contenido,
   así que pedirla otra vez no sube nada nuevo. En Instagram su proporción manda sobre el resto del
   carrusel, porque Instagram recorta todo a la del primer elemento.
10. **Una portada que no se puede dibujar no tumba la publicación**: sale sin ella y queda en el
    registro. Eso incluye el bucket caído y una imagen que ImageIO no sabe abrir, no solo el
    catálogo sembrado con fotos de picsum.photos. Si la llave ya existe no se vuelve a subir: el
    panel la pide cada vez que se abre la ficha y el bucket guarda versiones. El doble clic se
    rechaza antes de dibujarla. Un nombre con una letra que Archivo no tiene se escribe con la
    sans del sistema en vez de con cuadros.
11. **Las tipografías y el logo salen del kit.** Java2D no lee woff2 ni elige el peso de una fuente
    variable, así que `packages/marca/generador/fuentes_servidor.py` saca de las mismas woff2 del
    sitio tres instancias TTF estáticas (Archivo 700, IBM Plex Sans 400 y 500, unos 250 KB en el
    jar) y copia `logo-mono-negativo`. Se cargan al construir el bean: si faltan, no arranca.
    La imagen de producción (`eclipse-temurin:21-jre`) trae `libfreetype` y `fontconfig`.
12. **Los colores van escritos en `PortadaJava2D`** —Java2D no lee variables CSS— y
    `PortadaJava2DTest` falla si alguno deja de estar en `tokens.json`.
13. **Se elige por publicación.** El panel enseña la portada antes de publicar
    (`GET …/difusion/portada`) con una casilla marcada por omisión. En la API, `conPortada`
    ausente vale `false`: la publicación sale como salía antes.

## Lo que queda fuera

- **Plantillas por línea de negocio** (otra banda para ropa que para tecnología): una sola
  plantilla hasta ver cómo se ve el perfil con ella.
- **Historias** (9:16). La portada es para el feed.
- **El precio en la imagen**, por lo dicho en el punto 8.

## Cómo se verificó

Pruebas del armador con un doble de textos que pinta llaves —el orden y los datos—, y del pie
completo en castellano con los textos de verdad. La portada, por píxeles: medidas, banda, franja,
los dos chaflanes, una foto alta que conserva su borde inferior, nombres largos y colores contra
`tokens.json`. A ojo, cuatro portadas con fotos reales del catálogo (Echo Dot, AirPods, iPad y un
celular con nombre de 82 caracteres). No se publicó nada en Meta: la primera publicación real la
hace una persona desde el panel.
