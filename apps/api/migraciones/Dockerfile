# Las migraciones de la base, empaquetadas para correr como un Cloud Run Job (ADR-0105).
#
# En producción la base no tiene IP pública, así que el runner de GitHub no llega a ella y no puede
# migrar como lo hace en dev. El job corre dentro de la red privada, con la cuenta que lee la clave
# de la base, y la cuenta de despliegue no la conoce.
#
# La misma versión de Flyway que migra dev, y los mismos archivos: es el SHA el que dice qué
# esquema lleva cada imagen. La URL, el usuario y la clave entran por FLYWAY_URL, FLYWAY_USER y
# FLYWAY_PASSWORD, que fija el job.
#
# Se construye desde la raíz del repositorio:
#   docker build -f apps/api/migraciones/Dockerfile .
FROM flyway/flyway:12.4.0

COPY apps/api/infrastructure/src/main/resources/db/migration /flyway/sql

CMD ["-connectRetries=10", "-connectRetriesInterval=6", "migrate"]
