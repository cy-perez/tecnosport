# Imagen del backend. Se construye desde la raíz del repositorio:
#   docker build -f apps/api/Dockerfile -t tecnosport-api .
# El contexto es la raíz y no apps/api por coherencia con la imagen de la web, que sí necesita la
# raíz por los workspaces de npm. Un solo comando y un solo contexto para las dos.

FROM eclipse-temurin:21-jdk AS constructor
WORKDIR /fuente
COPY apps/api ./
# Sin pruebas: ya corrieron en integración continua contra PostgreSQL real con Testcontainers, y
# repetirlas aquí exigiría un Docker dentro del Docker que construye la imagen.
# Sin demonio: en un contenedor efímero solo añade un proceso que nadie va a reutilizar.
RUN chmod +x gradlew && ./gradlew --no-daemon :bootstrap:bootJar -x test

# ── Preparación: jar extraído y archivo CDS ─────────────────────────────────────────────────────
# Existe porque el arranque en frío de esta JVM es el costo dominante del ambiente: con
# `min_instance_count = 0`, el SSR de la portada espera a que arranque el backend antes de servir
# un byte, y en los registros de Cloud Run eso eran 20-23 s de pantalla en blanco.
#
# Dos técnicas, medidas contra este mismo jar (3 corridas cada una, `spring.context.exit=onRefresh`
# contra el PostgreSQL de `docker-compose.yml`):
#
#   fat jar, que es lo que había     14.542 ms
#   jar extraído                     11.364 ms   -22%
#   jar extraído + CDS                8.442 ms   -42%
#
# **El directorio tiene que ser el mismo aquí y en ejecución.** Un archivo CDS guarda la ruta del
# classpath con el que se entrenó; si la etapa de ejecución montara la aplicación en otro
# directorio, la JVM descartaría el archivo en silencio y no se ganaría nada. Por eso las dos
# etapas usan `/app`.
FROM eclipse-temurin:21-jre AS preparacion
WORKDIR /origen
# El comodín evita clavar la versión del artefacto; no cabe ambigüedad porque el otro jar del
# módulo termina en `-plain.jar`.
COPY --from=constructor /fuente/bootstrap/build/libs/*-SNAPSHOT.jar original.jar
# Deshacer el fat jar quita el cargador de clases anidado de Spring Boot, que abre 179 jars dentro
# de un jar en cada arranque. Es la mitad de la ganancia y no tiene ningún riesgo asociado.
RUN java -Djarmode=tools -jar original.jar extract --destination /app --application-filename app.jar

WORKDIR /app
# La corrida de entrenamiento: arranca la aplicación, la deja refrescar el contexto y sale, y al
# salir la JVM escribe el archivo con las clases que cargó.
#
# **Sin base de datos**, que es la parte que hay que explicar. Aquí no hay PostgreSQL —ni puede
# haberlo dentro de un `docker build`— así que Flyway se apaga, Hibernate recibe el dialecto en vez
# de deducirlo de una conexión, y Hikari tiene prohibido fallar por no poder conectarse. Lo que se
# entrena así es un archivo con 17.801 de 19.858 clases en lugar de 18.861, y **no se paga en
# tiempo**: medido contra el mismo arranque real con base, 8.442 ms contra 8.183 ms del archivo
# entrenado con base — dentro del ruido entre corridas. A cambio, esta imagen se sigue
# construyendo con un solo `docker build` y sin servicios alrededor.
#
# Ninguna de estas propiedades sobrevive a la etapa siguiente: son argumentos de esta corrida, no
# configuración de la aplicación.
RUN java -XX:ArchiveClassesAtExit=aplicacion.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
      -jar app.jar

FROM eclipse-temurin:21-jre AS ejecucion
WORKDIR /app
COPY --from=preparacion /app ./

# Usuario propio: si algo se cuela por una vulnerabilidad, que no entre como root.
RUN useradd --system --uid 1001 --create-home tecnosport && chown -R tecnosport:tecnosport /app
USER tecnosport

# Cloud Run inyecta PORT y espera que el proceso escuche ahí; application.yml ya lo lee.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage y no -Xmx: el límite de memoria lo pone Cloud Run por despliegue, y una JVM que
# no lo lee o se queda corta o la matan por exceso. 75 % deja aire para el resto del proceso.
#
# `-Xshare:auto` es explícito a propósito, aunque sea el valor por omisión: dice que si el archivo
# CDS no sirve —porque cambió el JDK, el classpath o la ruta— la JVM lo ignora y arranca como
# siempre, en vez de negarse a arrancar. Un archivo inválido tiene que costar los 6 segundos que
# ahorra, nunca el servicio.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Xshare:auto -XX:SharedArchiveFile=/app/aplicacion.jsa -jar /app/app.jar"]
