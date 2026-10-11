# ADR-0080: la ingesta corre en tramos por Cloud Tasks

Fecha: 2026-10-11
Estado: aceptada
Reemplaza: la cola en memoria de `ADR-0067` en los ambientes desplegados. Deja sin efecto el cambio
del mismo día a la API de dev (`cpu_siempre_asignada = true`, `instancias_maximas = 1`), que nunca
llegó a aplicarse.

## Contexto

El 11 de octubre de 2026 tres lotes de ingesta subidos a la 1:17 a. m. —Violeta, Imperio Wicho y
D'Osman— terminaron en `ERROR` con cero mensajes leídos. Los registros de Cloud Run dieron dos
causas, y ninguna estaba en el parser ni en la extracción:

1. **El hilo de fondo no tenía CPU.** La subida responde `202` y el lote se procesaba después, en
   un hilo de la aplicación. La API de dev tiene CPU solo durante la petición, así que el hilo se
   congeló ocho horas (Hikari avisó `Thread starvation… delta=1m16s`) y la escala a cero lo dejó a
   medias.
2. **El arranque cerraba lotes vivos.** Cada instancia que arrancaba cerraba con error todo lote en
   `PROCESANDO`, incluido el que una vecina viva estaba leyendo: cinco arranques en cuarenta
   segundos cerraron los tres. El PR #221 lo corrigió con un latido en el lote (`V102`).

Para la primera causa se propuso dar CPU entre peticiones a la API de dev y limitarla a una
instancia. Eso no lo resuelve del todo: con cero instancias mínimas, Cloud Run apaga una instancia
que no recibe peticiones, y el trabajo de fondo no cuenta como actividad. Una ingesta larga sin
nadie en el panel podía seguir cortándose.

## Decisión

**Cada tramo de un lote es una tarea de Cloud Tasks** que llama a la API por HTTP (`POST
/api/v1/internal/ingestas/{id}/tramo`). Mientras la petición dura, la instancia tiene CPU y Cloud
Run no la apaga. La cola vive fuera de la aplicación: sobrevive a reinicios y despliegues, y
reintenta lo que falla.

1. **Por tramos.** Una tarea HTTP de Cloud Tasks espera como mucho 30 minutos, y en los registros un
   chat de 159 mensajes ya tardaba entre 11 y 14 minutos antes de que existiera la lectura de fotos.
   Un tramo trabaja hasta un presupuesto de **10 minutos**, dentro de una tarea con plazo de **15**.
   Si queda trabajo, devuelve el lote a la cola (`RECIBIDO`) con su avance, y quien lo procesó
   encola el tramo siguiente (`AvanzarIngesta`).
2. **Retomar es posible porque cada publicación guarda su estado.** El tramo siguiente sigue con
   las que están en `PENDIENTE_EXTRACCION`. El avance se anota en el lote después de cada
   publicación. La lectura —leer el zip, registrar mensajes, armar publicaciones— se hace una vez:
   armar va dentro de la transacción que bloquea el lote y anota el avance, y si otro tramo ya armó,
   este no vuelve a armar.
3. **De quién es el lote lo dicen los reintentos, no solo el estado.** Un trabajador congelado
   puede despertar con el lote en `PROCESANDO` después de que otro lo retomara. El tramo recuerda
   los reintentos del lote al tomarlo, y en cada punto de control y en cada escritura comprueba que
   sigan siendo los mismos; si cambiaron, sale sin escribir nada.
4. **Un lote abandonado se retoma, no se cierra.** Si su trabajador deja de latir tres minutos,
   vuelve a la cola hasta tres veces (`lote_ingesta.reintentos`, `V103`); a la cuarta queda en
   `ERROR` con el último fallo en el motivo. El que se estaba deteniendo queda detenido. Lo hace el
   propio tramo que encuentra el lote abandonado —la tarea reintentada—, y como red de seguridad
   `RecuperarIngestasAbandonadas`, al arrancar y cada minuto. Decidido al aprobar el plan, el mismo
   día.
5. **Un fallo pasajero se reintenta; uno que se repetiría, no.** Un zip ilegible o una regla del
   dominio cierran el lote con su motivo y la tarea responde `204`: reintentar no cambiaría nada.
   Cualquier otro fallo —la base, el bucket, la red, un `OutOfMemoryError`— devuelve el lote a la
   cola contando un reintento, la tarea responde `503` y Cloud Tasks la reintenta.
6. **El lote de otro trabajador vivo responde `503`.** Cloud Tasks reintenta con espera creciente
   (10, 20, 40, 80, 160 s y luego 300, hasta 10 intentos); si ese trabajador murió, para el sexto
   intento su latido ya venció y el tramo retoma el lote. Sin esto, la tarea se daba por cumplida y
   el lote dependía de la tarea periódica, que en dev solo corre con tráfico.
7. **La pausa suelta el lote pero retiene a los del mismo proveedor.** El trabajador lo suelta en
   su próximo punto de control con su avance; los lotes de otros proveedores siguen. Los del mismo
   proveedor esperan, porque el orden importa: el chat de caballero de Meraki tiene que leerse antes
   que el general, que descarta lo que aquel trae. Reanudar devuelve el lote a la cola y lo encola.
8. **Un lote espera al anterior de su mismo proveedor** (`ColaDelProveedor`). Cloud Tasks no
   garantiza el orden de entrega. El orden es la fecha de subida y, a igual fecha, el id. El que
   espera no se reprograma: lo encola quien suelte al anterior —al terminar, fallar o detenerse—.
9. **La cola despacha un tramo a la vez** (`max_concurrent_dispatches = 1`), como el hilo único de
   antes.
10. **El endpoint interno solo acepta el token de la cuenta de las tareas.** La API de dev es pública
    (`allUsers` la invoca), así que la aplicación verifica el token OIDC que firma Cloud Tasks:
    emisor Google, nuestra audiencia, vigente, y el correo verificado de
    `tecnosport-ingesta-tareas`. Con el decodificador de Nimbus que ya usa «Entrar con Google». No
    sale en el contrato publicado (`springdoc.paths-to-exclude`).
11. **Sin cola configurada, la ingesta corre en un hilo con la cola en memoria**, que es lo de
    local y las pruebas. El mismo `AvanzarIngesta`, el mismo latido; lo único distinto es quién
    encola. Dentro de Cloud Run, sin cola configurada, el arranque lo avisa con un `WARN`: es el
    fallo del 11 de octubre.
12. **La red de seguridad.** Un lote que lleva 20 minutos en la cola sin moverse —un tramo que no se
    pudo encolar, una tarea perdida— se vuelve a encolar, salvo que esté esperando a uno anterior de
    su proveedor.

Se crea la tarea con la API REST v2 y el `HttpClient` del JDK, como los demás clientes, y no con la
librería de Cloud Tasks: es una sola llamada. Las credenciales salen de
`google-auth-library-oauth2-http` 1.50.0, que ya llegaba con Storage y ahora se declara.

## Consecuencias

- **Costo en dev, casi nada.** Cloud Tasks da el primer millón de operaciones al mes gratis, y un
  lote son unas pocas. En Cloud Run se cobra la CPU mientras dura un tramo, no la vida de la
  instancia: por eso la API de dev se queda con CPU solo durante la petición y hasta tres
  instancias.
- **La API de dev espera 30 minutos por petición** (`timeout = "1800s"`), y eso vale para todas sus
  rutas: Cloud Run no puede cortar un tramo antes que Cloud Tasks.
- **Un duplicado no hace daño.** Las tareas no llevan nombre, porque el mismo lote se encola muchas
  veces y Cloud Tasks reserva por horas el nombre de una tarea borrada. El tramo que llega segundo
  encuentra el lote tomado —responde `503` y se reintenta— o cerrado, y no hace nada.
- **Entre tramos, el panel muestra el lote «En cola» unos segundos**, y en ese momento no se puede
  pausar: el panel solo pausa lo que se está procesando.
- **Una publicación cortada a la mitad se extrae otra vez**: una llamada más a la API de Claude. Lo
  que ya quedó resuelto no se repite.
- **Las cifras pueden quedar cortas en un caso**: si la lectura se repite —el trabajador murió entre
  registrar y armar—, `mensajesNuevos` cuenta cero, porque esos mensajes ya estaban registrados.
- **El motivo del error es un código desde el mismo día** (`MotivoDeFalloDeLote`, `V104`): el
  lote guarda el código y, si hace falta, el dato que el texto interpola, y el panel lo traduce
  (regla dura 4). La frase en español queda para el registro. Las publicaciones todavía guardan su
  motivo como texto: es el mismo problema en otra pantalla, y va aparte.

## Lo que hay que verificar con el primer lote real

- **Que el token de Cloud Tasks traiga `email` y `email_verified`.** El verificador los exige. Si
  no vinieran, todas las tareas responderían `401` y ningún lote avanzaría; el registro de la API lo
  diría en el primer tramo.
- **Cuánto dura un tramo.** El presupuesto de 10 minutos y el plazo de 15 dejan cinco de holgura
  para la publicación en curso. La lectura del primer tramo no tiene presupuesto: un zip con miles
  de fotos que suba más de 15 minutos al bucket haría que Cloud Tasks cortara y reintentara. El
  registro de la API dice cuánto duró cada tramo.
- **Si la API dejara de ser pública** (`publico = false`), la cuenta de las tareas necesitaría
  `roles/run.invoker`; hoy no hace falta.
