# Núcleo Spring Boot

Primera etapa: proyecto Maven, PostgreSQL y migración Flyway inicial. El esquema tiene entidades JPA y un primer endpoint público de lectura; `ddl-auto: validate` verifica su correspondencia sin cambiar tablas. La carga de datos, autenticación y reservas se implementarán después.

## Requisitos

- JDK 21 y Maven compatible (3.6.3 o posterior).
- Docker con Compose para la base local, o una instancia PostgreSQL propia.

## Arranque local en PowerShell (Windows)

Desde la raíz del repositorio:

```powershell
Copy-Item .env.example .env
# Edita .env y cambia DB_PASSWORD por una contraseña local.
docker compose --env-file .env up -d postgres
cd backend
$linea = Get-Content ..\.env | Where-Object { $_ -match '^DB_PASSWORD=' } | Select-Object -First 1
if (-not $linea) { throw 'Falta DB_PASSWORD en .env' }
$env:DB_PASSWORD = ($linea -split '=', 2)[1]
mvn spring-boot:run
```

La aplicación usa por defecto `localhost:5433` y el usuario `ica_stay`. Si cambias el puerto en `.env`, asigna también `$env:DB_URL = 'jdbc:postgresql://localhost:<puerto>/ica_stay'` antes de Maven. Docker Compose lee `.env`, pero Maven no lo carga automáticamente. En otra terminal, verifica con `Invoke-RestMethod http://localhost:8080/actuator/health`.

## Arranque local en Bash

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

En Linux o macOS, `source .env` requiere una terminal Bash; en PowerShell usa las instrucciones de arriba.

Verificación manual: `http://localhost:8080/actuator/health` debe devolver `{"status":"UP"}`. Al arrancar, Flyway aplica `V1__initial_schema.sql`; si falla la migración o PostgreSQL no responde, la aplicación no queda lista. Revisa con `mvn test` y `docker compose exec postgres psql -U ica_stay -d ica_stay -c '\dt'`.

## Primera consulta de catálogo

CORS está centralizado en `SecurityConfig` e integrado con la cadena de Spring Security para `/api/**`. El único origen local permitido es `http://localhost:4200`. Se permiten GET, POST, PUT, PATCH, DELETE y OPTIONS, con headers Content-Type, Authorization y Accept. No se habilitan credenciales basadas en cookies; Authorization queda disponible para Bearer JWT. El preflight no requiere JWT, pero la petición real conserva todas las reglas de autorización. CSRF ya estaba deshabilitado en la configuración stateless y permanece sin cambios.

`icastay.cors.allowed-origin` en `application.yml` permite configurar otro origen exacto mediante `CORS_ALLOWED_ORIGIN` al cambiar de ambiente; no usar comodines. Reinicia Spring Boot para aplicar esta configuración a un proceso que ya estaba levantado.

Con el backend levantado, abre `http://localhost:8080/api/v1/hotels`. La respuesta inicial tiene `content: []` porque aún no hay datos de prueba ni endpoints administrativos para crear hoteles. Acepta `page` (desde 0) y `size` (1–100); solo muestra hoteles `ACTIVE` y no expone identificadores ni datos de administradores.

Para comprobar esta etapa desde la raíz del repositorio, detén y reinicia Spring Boot tras actualizar la rama y ejecuta `mvn test` dentro de `backend`; con PostgreSQL disponible, `ddl-auto: validate` verifica las entidades al iniciar. Si Hibernate indica un desajuste de tipo o columna, conserva el error completo para corregir el mapeo sin modificar la migración aplicada.

La base del contenedor queda en un volumen Docker. `docker compose down` detiene los servicios; `down -v` borra los datos y solo debe ejecutarse si quieres reiniciar la base deliberadamente.
