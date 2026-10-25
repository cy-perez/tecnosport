# ADR-0087 — La IP del cliente solo se cree de quien la puede probar

**Fecha:** 2026-10-25
**Estado:** aceptado.

## Contexto

`IpDelCliente` tomaba el **primer** valor de `X-Forwarded-For`, y ese valor lo
escribe el cliente. Lo levantó el `revisor-pagos` el 25 de septiembre de 2026 y
se dejó para un cambio propio, porque tocarlo sin conocer la topología podía
romper el límite o corromper la constancia legal.

El 25 de octubre de 2026 se midió contra dev. Primero el control: once consultas
a `POST /api/v1/pedidos/seguimiento` desde una sola IP dieron diez 404 y un 429,
así que el límite existe. Luego, once consultas con un `X-Forwarded-For` distinto
en cada una, **ninguna frenada**: ni directo a `tecnosport-api-….run.app` ni por
el proxy de `/api` de la web. Los cuatro perfiles de `FiltroLimiteIntentos`
—auth, pedidos, cotización y seguimiento— no limitaban a nadie que quisiera
evadirlos, y la IP guardada en `autorizacion_datos` era un dato que escribía el
titular.

La nota original decía que el arreglo era "la penúltima IP, porque hay un
balanceador". **No hay balanceador.** `docs/07` lo planea, pero lo desplegado
son dos servicios de Cloud Run publicados en `run.app`. La API no declara
`ingress`, así que está abierta a internet, y por ahí llegan además los webhooks
de Wompi y de Skydropx y los tramos de Cloud Tasks. Hay dos caminos y la IP real
queda en un sitio distinto en cada uno:

| Camino | Lo que ve la API |
|---|---|
| Directo a la API | `[inventado], IP real` |
| Navegador → web → proxy → API | `[inventado], IP real, IP de salida de la web` |

Con estos dos caminos ninguna posición fija sirve: "la penúltima" le devuelve al
atacante su propio valor si llama a la API directo, y "la última" mete a todos los
que pasan por la web en un solo cubo.

## Decisión

1. **La IP se resuelve una vez, en un filtro** (`FiltroIpDelCliente`, el primero
   de la cadena), y `IpDelCliente.de()` solo lee lo que ese filtro dejó. Los
   cinco que la usan no cambian.
2. **El proxy de la web deja de reenviar** `X-Forwarded-For`, `X-Ip-Cliente` y
   `X-Clave-Proxy` del visitante. En su lugar manda `X-Ip-Cliente` con el último
   valor de su propio `X-Forwarded-For` —el que agregó el frente de Cloud Run— y
   `X-Clave-Proxy` con una clave compartida que vive en Secret Manager
   (`proxy-web-clave`).
3. **La API cree `X-Ip-Cliente` solo si la clave coincide**, comparada en tiempo
   constante. Si no viene o no coincide, toma el valor que está
   `saltos-confiables` posiciones antes del final de `X-Forwarded-For`, que hoy es
   1. Sin cabecera, `getRemoteAddr()`.
4. **Sin clave configurada, la cabecera del proxy no se cree nunca.** Es el valor
   por omisión: el entrenamiento CDS del Dockerfile arranca la API sin secretos, y
   una propiedad obligatoria nueva habría roto el despliegue y no el PR.
5. Solo se acepta un valor con forma de IP, de hasta 45 caracteres. Va a la
   constancia legal y a la llave del limitador.
6. **Una IPv6 se cuenta por su /64** en la llave del limitador (`CuboDeIp`); la
   constancia guarda la dirección completa. La API publica registros AAAA, y
   cualquier servidor alquilado trae un /64: contada entera, cada dirección nueva
   del mismo rango estrenaba cubo, que es lo mismo que cambiar la cabecera.
7. **Las varias líneas de `X-Forwarded-For` se unen** antes de contar desde el
   final. `getHeader` devuelve solo la primera, y con dos líneas la primera
   puede ser entera del cliente.
8. El proxy tampoco reenvía `Forwarded`: hoy nadie la lee, pero es la misma
   cabecera escrita de otra forma.

Los puntos 6 a 8 los levantó la revisión de seguridad del mismo PR.

## Alternativas descartadas

- **Cerrar la API ahora** con `ingress = internal`. Exige darle salida por la VPC
  a la web y comprobar que Cloud Tasks y los webhooks siguen llegando a un
  servicio cerrado. Es más infraestructura que el arreglo entero, y el día del
  balanceador se resuelve con `internal-and-cloud-load-balancing`, sin VPC.
- **Leer la IP con `forward-headers-strategy` de Spring.** Resuelve el formato,
  no la confianza: seguiría creyendo lo que agregó un cliente que llamó directo.

## Consecuencias

- **El orden del despliegue importa.** Si el código llega antes que la clave, el
  proxy ya no reenvía la cabecera del visitante y la API ve a todos los que pasan
  por la web con la misma IP. No es solo más estricto: quince peticiones a
  `/auth/sesion` dejarían a todo el sitio sin poder entrar durante quince
  minutos. La secuencia es aplicar con `proxy_web_listo=false`, cargar la clave,
  aplicar con `true` y solo entonces fusionar. **Y aplicar con `true` antes de
  cargar la clave deja dev sin poder desplegar**: Cloud Run no arranca una
  revisión que monta un secreto sin versiones.
- **Pasa lo mismo, por minutos, en cada despliegue**: la API sale antes que la
  web, y mientras conviven una nueva y una vieja, la API ve la IP de salida de
  la web. También al rotar la clave, hasta que las dos instancias la relean.
- **Que el frente de Cloud Run agrega exactamente un valor está medido de forma
  indirecta, no leído.** Se comprueba después de desplegar: diez consultas
  directas y una por la web, cada una con un `X-Forwarded-For` inventado
  distinto, tienen que compartir cubo. Si el último valor fuera de Google y no
  del cliente, los dos caminos no coincidirían.
- **El día del balanceador**, `/api` va de él a la API sin pasar por el proxy.
  Ese día `IP_CLIENTE_SALTOS_CONFIABLES` pasa a 2 y la API deja de aceptar
  tráfico que no venga del balanceador. Si alguno de los dos cambios se hace sin
  el otro, se vuelve a abrir el hueco.
- **Las filas de `autorizacion_datos` anteriores al cambio** pueden tener una IP
  escrita por el titular. No hay forma de saber cuáles ni de corregirlas. Queda
  dicho aquí.
- El seguimiento por número tenía un hueco que el límite solo encarecía, y se
  cierra aparte en ADR-0088.
