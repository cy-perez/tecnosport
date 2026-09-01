# Entrega — kit de interfaz web de Tecno Sport

Lo marcado con ⚠️ hay que confirmarlo antes de programar.

---

## Resumen

**Marca:** Tecno Sport · **Negocio:** distribuidor de tecnología y ropa y calzado
deportivo en Medellín, al por mayor y al detal.
**Acción principal del sitio:** pedir cotización. No hay carrito ni pago en línea.
**Personalidad:** seria · sobria con un solo golpe de color · tecnológica.
**Plataforma:** Angular 21 en el frontend, Spring Boot 3.5 y PostgreSQL detrás.

### Cómo interpreté lo que pediste

Dijiste **«vitrina + cotización»** y, al preguntarte por las secciones,
**«las que hacen realmente atractivo a un e-commerce»**. Esas dos cosas se
concilian de una manera concreta, y así está diseñado el kit:

> El sitio tiene **todas las piezas que hacen agradable una tienda** —catálogo con
> filtros, ficha de producto con galería, precios visibles, etiquetas de stock,
> buscador, destacados— pero **el cierre no es un carrito, es una lista de
> cotización**. Donde una tienda dice «Comprar», aquí dice «Agregar a la
> cotización», y el contador del header cuenta esa lista.

Es la conversión que mejor le sirve a un negocio que vende a dos públicos con
precios distintos: el que compra uno y el que compra cien no pueden ver el mismo
precio, y una cotización resuelve eso sin montar un portal con login.
**Si preferías precios ocultos también al detal, dímelo y lo ajusto**: es un
cambio de contenido, no de sistema. ⚠️

### Archivos de marca recibidos

| Archivo | Recibido | Observación |
|---|---|---|
| Logo principal SVG | sí | Vectorial y limpio |
| Horizontal | sí | Es la variante del header |
| Isotipo | sí | Header en móvil y favicon |
| Mono negativo + isotipo negativo | sí | Footer, modo oscuro y fondos de color |
| Manual de marca + `tokens.json` | sí | Se extendió, no se rehízo |

**Sin limitaciones.** Llegó todo lo que hacía falta, así que ninguna decisión de
color o de forma quedó aproximada.

### Datos de la empresa ya incorporados

| Dato | Valor | Estado |
|---|---|---|
| NIT | 1054994043-1 | Puesto en el footer |
| Teléfono y WhatsApp | 310 420 9655 | Enlazado como `wa.me` y `tel:` |
| Dirección | Cra. 26C #38B-31, Medellín, Antioquia | Puesta en el footer |
| Correo | contact@tecnosport.co | Enlazado como `mailto:` |
| Figura jurídica | Persona natural | El pie no lleva sigla societaria |

---

## Decisiones de diseño

**Origen de la paleta:** el manual de marca y su `tokens.json`. **No se
reinventó nada**: los HEX son los mismos. Lo que se añadió es lo que una interfaz
necesita y una identidad no tiene.

| Rol | HEX | Por qué |
|---|---|---|
| `primario` | `#1B1F26` | Grafito de marca. Botón principal, titulares, header |
| `acento` | `#F5B301` | Ámbar señal. **Una sola cosa por pantalla**, y solo como relleno |
| `marca` | `#1B1F26` | **Nuevo.** La superficie de las franjas grandes: hero, ejes, menú móvil, footer |
| `superficie-alt` | `#E9ECF0` | **Nuevo.** Neutro de encabezados de tabla, código y marcos de imagen |
| `borde-control` | `#7C8595` | **Nuevo.** El borde de un campo necesita 3:1; el divisor decorativo no |
| `aviso` | `#A15C00` | **Nuevo.** Ámbar oscurecido hasta ser legible como texto (5.19:1) |
| `foco` | `#1B1F26` | **Nuevo.** El anillo de teclado, que no se elimina nunca |

Los estados —hover, pressed, deshabilitado, texto sobre cada fondo— **no se
eligieron**: los calcula el generador desde el color base. Por eso siguen siendo
coherentes si mañana cambias un color.

### Tres decisiones donde me aparté del manual, y por qué ⚠️

1. **El hover del botón principal aclara en vez de oscurecer.** El manual proponía
   `#0E1217`. El grafito ya es casi negro: oscurecerlo no se percibe y el botón
   parece roto. El hover sube a `#3B3E44` y el *pressed* sí se hunde a `#121419`,
   que es prácticamente el valor del manual. La regla la decide la luminancia del
   color base, no el gusto.

2. **En modo oscuro, las franjas grandes NO se vuelven ámbar.** El manual dice que
   sobre fondo oscuro «manda el ámbar», y como color de texto y de botón es
   correcto (10.14:1 sobre `#0E1217`). Pero aplicarlo también al hero, a los ejes,
   al menú móvil y al footer teñía media página de ámbar y mataba la regla de una
   sola cosa por pantalla. Por eso existe el rol `marca`, que en oscuro se queda
   en grafito elevado. El ámbar sigue mandando en botones y enlaces.

3. **Consecuencia práctica de lo anterior:** en modo oscuro los botones
   secundarios pasan a contorno neutro. Si fueran ámbar, volveríamos al mismo
   problema.

Si alguna de las tres no te convence, se cambia en `tokens.json` y se regenera.

**Tipografía** — las tres del manual, sin cambios:

| Rol | Familia | Pesos | Licencia | Origen |
|---|---|---|---|---|
| Titulares | Archivo | 500, 700 | SIL OFL 1.1 | **Autoalojada en el kit** |
| Texto e interfaz | IBM Plex Sans | 400, 500, 700 | SIL OFL 1.1 | **Autoalojada en el kit** |
| Precios y SKU | IBM Plex Mono | 400, 500 | SIL OFL 1.1 | **Autoalojada en el kit** |

Las tres son libres: **no hay que comprar ninguna licencia**. Van dentro del kit en
`fuentes/`, así que el sitio no depende de Google Fonts. Las licencias OFL viajan
con los archivos porque distribuirlas es obligatorio.

**Geometría:** radio 0 en todo. La firma es el **chaflán a 45°** en la esquina
superior izquierda y la inferior derecha, resuelto en la clase `.chaflan`.

---

## Entregables

```
kit/
├── index.html      la guía visual — ábrela en el navegador, es lo primero que hay que ver
├── tokens.css      variables para el proyecto — GENERADO, no editar
├── tokens.json     las decisiones — ESTE es el archivo que se edita
├── tipografia.md   familias, pesos, licencia e instalación
├── contraste.md    informe WCAG de los dos modos
├── fuentes.css     los @font-face de las tipografías autoalojadas
├── fuentes/        los .woff2 y sus licencias OFL
├── logo/           las variantes del logo que usa la guía
├── generador/      kit_ui.py, fuentes.py y la plantilla
├── ENTREGA.md      este documento
└── LEEME.md        cómo regenerar
```

**Para cambiar cualquier decisión:** edita `tokens.json` y ejecuta

```bash
python3 generador/kit_ui.py tokens.json --out . --marca "Tecno Sport" \
  --logo logo/logo-horizontal.svg --logo-negativo logo/logo-mono-negativo.svg \
  --isotipo logo/isotipo.svg --isotipo-negativo logo/isotipo-negativo.svg
```

Los estados, el modo oscuro y el informe de contraste se recalculan solos. **No
edites `tokens.css` a mano:** el siguiente regenerado borra el cambio.

**Informe de contraste: sin fallas**, en modo claro y en modo oscuro. Verificado
también a mano en el navegador: 0 px de desbordamiento horizontal a 380 px de
ancho, foco visible en los 45 elementos enfocables, jerarquía de encabezados sin
saltos, y la maqueta aguanta el texto al 200 % sin romperse.

---

## Para quien programa

1. Copia `fuentes/`, `fuentes.css` y `tokens.css` a `src/assets/marca/` y
   enlázalos **en ese orden**, antes de tus estilos:

   ```json
   "styles": [
     "src/assets/marca/fuentes.css",
     "src/assets/marca/tokens.css",
     "src/styles.scss"
   ]
   ```

   Los tokens son CSS puro, así que atraviesan el encapsulamiento de estilos de
   los componentes de Angular: dentro de cualquier componente,
   `var(--color-primario)` funciona sin `::ng-deep`.

2. **Ningún HEX ni píxel suelto en el código.** Si hace falta un valor que no está
   en el sistema, el sistema está incompleto: añádelo a `tokens.json` con nombre.
3. Modo oscuro: `data-tema="oscuro"` en el `<html>`.
4. **Header:** 72 px en escritorio, 60 px en móvil, fijo, logo a 34 px. En móvil,
   isotipo y menú a pantalla completa tras un botón de 44×44 px.
5. **El logo va en sus dos versiones** —positiva y negativa— y el tema decide cuál
   se ve. Un logo monocromo oscuro desaparece sobre fondo oscuro; la clase
   `.logo-pos` / `.logo-neg` ya resuelve el cambio.
6. **Ancho máximo de contenido:** 1200 px. **Puntos de quiebre:** 640 / 1024 / 1280.
7. **El chaflán y el foco:** `clip-path` recorta también el anillo de foco, así que
   `tokens.css` desactiva el chaflán en `:focus-visible`. Es deliberado: el foco
   visible pesa más que la esquina. No lo «arregles».
8. **Imágenes que debe entregar el cliente:** ⚠️
   - Producto: **1:1, 1000×1000 px**, fondo claro y uniforme.
   - Hero: **4:3, 1200×900 px**.
   - Ejes de categoría: **16:9, 1200×675 px**.
   Si las proporciones no se respetan, la rejilla de tarjetas queda desigual.

---

## Cómo quedó el pie siendo persona natural

El sitio lo opera una **persona natural**, así que en el pie **no va ninguna sigla
societaria**: nada de «S.A.S.», «Ltda.» ni «S.A.». Lo que hay es el nombre
comercial y el NIT, que en este caso es la cédula con su dígito de verificación:

```
Tecno Sport
NIT 1054994043-1
Cra. 26C #38B-31
Medellín, Antioquia, Colombia
```

Eso identifica al responsable y es lo que se ve en la mayoría de sitios de
establecimientos de comercio en Colombia. **Si además quieres el nombre completo
del titular** —algunos abogados lo recomiendan cuando el sitio capta datos
personales, porque el titular es el responsable del tratamiento— se añade una
línea encima: «Nombre Apellido, propietario». Dímelo y lo pongo; no lo puse por mi
cuenta porque el nombre registrado tiene que ser exactamente el de la cédula, y
ese dato no me lo diste. ⚠️

Ese mismo nombre completo tendrá que aparecer sí o sí en la **política de
tratamiento de datos**, donde la ley pide identificar al responsable.

---

## Textos de muestra

En `index.html`, **todo lo que aparece subrayado con puntos es texto de muestra**
y hay que reemplazarlo: titulares, nombres de producto y precios. Los precios y
referencias son inventados y están puestos solo para comprobar que las columnas
alinean. El NIT, la dirección, el teléfono y el correo **ya son los reales** y por eso no
llevan esa marca.

Los textos definitivos —portada, propuesta de valor, servicios, preguntas
frecuentes, microcopy de botones y formularios— salen de la skill
`copywriter-web`, que es la siguiente pieza del encargo.

---

## Pendientes antes de publicar

- [x] NIT **1054994043-1**, dirección **Cra. 26C #38B-31, Medellín**, teléfono y
      WhatsApp **310 420 9655**, correo **contact@tecnosport.co** — puestos en la
      maqueta, sin marca de muestra
- [x] Figura jurídica: **persona natural**. El pie quedó sin sigla societaria
- [ ] ⚠️ Decidir si el pie lleva además el **nombre completo del titular**
      (ver «Cómo quedó el pie siendo persona natural»)
- [ ] ⚠️ Confirmar que la conversión es cotización y no venta en línea, y si el
      precio al detal se muestra públicamente
- [ ] ⚠️ Confirmar las tres decisiones donde me aparté del manual de marca
- [ ] Redactar y enlazar la **política de tratamiento de datos**: es obligatoria en
      Colombia porque el sitio captura datos personales en el formulario
- [ ] Reemplazar los textos de muestra (skill `copywriter-web`)
- [ ] Entregar las imágenes en las proporciones de arriba
- [ ] Sumar `dist/web/` del kit de marca a la raíz del sitio (favicon, manifest) y
      pegar `head-snippet.html` en el `<head>`
