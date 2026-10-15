# Diagnóstico de 404 en Mi hotel

Fecha: 2026-10-15. Rama: `feature/hotel-management`.

## Evidencia comprobada

- `AuthService.login` obtiene la entidad de usuario por email y estado ACTIVE.
  `JwtTokenService.createToken` escribe `user.getId()` en `userId`.
- `JwtAuthenticationFilter` valida el token y convierte `userId` a UUID para
  construir `AuthenticatedUser`. El controlador usa ese UUID, no un ID del cliente.
- `findByAdminUserId` se traduce a la columna `hotels.admin_user_id`; el mapeo es
  correcto y las pruebas HTTP con PostgreSQL devuelven el hotel propio.
- Consulta SQL de solo lectura en localhost:5433/ica_stay: la asociación de los
  identificadores indicados por el usuario existe y ambos registros están ACTIVE.
  No se modificaron esos registros.
- Durante la inspección, 8080 pertenecía al PID 6128, iniciado a las 09:14:01.
  Health devolvió 200. `jcmd VM.class_hierarchy` confirmó AdminHotelController
  y AuthController cargados. `user.dir` y classpath apuntaban al backend de este
  repositorio y target/classes. Esto no certifica que toda clase cargada sea
  idéntica al fuente actual, pero descarta la ausencia del controlador.
- El proceso mantenía conexiones TCP a 127.0.0.1:5433. No se encontraron overrides
  de datasource/configuración entre las propiedades JVM inspeccionadas. Esto no
  permite afirmar que se hayan leído todas las variables heredadas del proceso.
- El diagnóstico de login ejecutado por el usuario devolvió 200, rol HOTEL_ADMIN
  y el UUID esperado. La primera salida no mostró de forma fiable HotelHTTP por
  el formato tabular de PowerShell; el script fue corregido para usar Format-List.
- Al repetir el script, el usuario confirmó HotelHTTP 200 y HotelId
  `22222222-2222-2222-2222-222222222222`: el caso demo funciona en el proceso
  real con un JWT obtenido mediante login reciente.

## Regresión automatizada

Se amplió AdminHotelHttpIntegrationTest: credenciales aleatorias solo de fixtures,
BCrypt real, POST de login HTTP y validación del claim userId antes de consultar.
Los UUID de fixtures se conocen antes de insertar y no coinciden con los datos
demo. Se eliminan exclusivamente las filas creadas por cada prueba.

Los 12 casos incluyen dos administradores aislados, hotel propio 200, sin hotel
404, USER/SUPER_ADMIN 403 y ausencia/token inválido 401. `mvn test` terminó con
137 pruebas exitosas, sin fallos, errores ni omitidas. `git diff --check` correcto.

## Verificación local sin divulgar secretos

Desde la raíz, con el backend activo:

```powershell
.\backend\scripts\Test-AdminHotel.ps1 -Email 'admin.demo@icastay.local'
```

La contraseña se introduce en Get-Credential, no en argumentos ni archivos.
El script solo consulta login y hotel; no ejecuta PUT ni cambia datos. Muestra
UUID, rol, expiración, códigos HTTP e ID de hotel. No compartir contraseña ni JWT.

Identificar el proceso activo:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  Select-Object LocalAddress, LocalPort, OwningProcess
```

Comprobar la asociación sin leer hashes (psql pide contraseña localmente):

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -X -W -h localhost -p 5433 -U ica_stay -d ica_stay -c "SELECT current_database(),current_user,current_schema(); SELECT u.id,u.role,u.status,h.id AS hotel_id,h.admin_user_id FROM users u LEFT JOIN hotels h ON h.admin_user_id=u.id WHERE u.id='11111111-1111-1111-1111-111111111111';"
```

Si el proceso no corresponde a la compilación deseada, detenerlo con Ctrl+C en
su terminal y arrancar allí el backend actual, conservando DB_PASSWORD y JWT_SECRET
como variables de entorno y verificando los overrides antes de ejecutar:

```powershell
Set-Location .\backend
$env:DB_URL = 'jdbc:postgresql://localhost:5433/ica_stay'
$env:DB_USER = 'ica_stay'
mvn.cmd spring-boot:run
```

No es una corrección demostrada del incidente: es un arranque explícito para
comparar entornos. Maven no carga .env automáticamente. No borrar volúmenes,
reasignar propietarios ni cambiar UUID para hacer desaparecer el 404.

## Conclusión

No se reprodujo un defecto de JWT/JPA en el código actual. La petición real demo
devuelve 200 y el hotel esperado. No se dispone del token/proceso/traza de la
petición original fallida, por lo que no puede atribuirse con certeza a sesión
anterior, proceso desactualizado, otra configuración o ruta distinta.

Si reaparece, observar en ese proceso si se alcanza `AdminHotelController.get`
y `HotelService.assignedHotel`, el UUID y datasource/esquema efectivos. Un 404
por recurso sin mapping no es equivalente a uno lanzado por assignedHotel.
No se aplicó una corrección especulativa ni se debilitó la seguridad.
