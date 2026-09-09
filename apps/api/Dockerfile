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

FROM eclipse-temurin:21-jre AS ejecucion
WORKDIR /app
# El comodín evita clavar la versión del artefacto; no cabe ambigüedad porque el otro jar del
# módulo termina en `-plain.jar`.
COPY --from=constructor /fuente/bootstrap/build/libs/*-SNAPSHOT.jar app.jar

# Usuario propio: si algo se cuela por una vulnerabilidad, que no entre como root.
RUN useradd --system --uid 1001 --create-home tecnosport
USER tecnosport

# Cloud Run inyecta PORT y espera que el proceso escuche ahí; application.yml ya lo lee.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage y no -Xmx: el límite de memoria lo pone Cloud Run por despliegue, y una JVM que
# no lo lee o se queda corta o la matan por exceso. 75 % deja aire para el resto del proceso.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar"]
