# Núcleo Spring Boot

Primera etapa: proyecto Maven, PostgreSQL y migración Flyway inicial. El esquema todavía no incorpora entidades JPA ni endpoints de negocio; `ddl-auto: validate` evita cambios automáticos del esquema.

## Requisitos

- JDK 21 y Maven compatible (3.6.3 o posterior).
- Docker con Compose para la base local, o una instancia PostgreSQL propia.

## Arranque local (terminal Bash)

Desde la raíz del repositorio:

```bash
cp .env.example .env
# Edita .env y asigna una contraseña local a DB_PASSWORD.
docker compose --env-file .env up -d postgres
```

En otra terminal, desde la raíz, carga las variables locales antes de iniciar Maven:

```bash
set -a
source .env
set +a
cd backend
mvn spring-boot:run
```

En PowerShell puedes asignar `$env:DB_PASSWORD` y, si cambias los valores por defecto, `$env:DB_URL` y `$env:DB_USER` antes de ejecutar Maven. La conexión por defecto usa `localhost:5433`.

Verificación manual: `http://localhost:8080/actuator/health` debe devolver `{"status":"UP"}`. Al arrancar, Flyway aplica `V1__initial_schema.sql`; si falla la migración o PostgreSQL no responde, la aplicación no queda lista. Revisa con `mvn test` y `docker compose exec postgres psql -U ica_stay -d ica_stay -c '\dt'`.

La base del contenedor queda en un volumen Docker. `docker compose down` detiene los servicios; `down -v` borra los datos y solo debe ejecutarse si quieres reiniciar la base deliberadamente.
