# Listado global de hoteles SUPER_ADMIN — 7.3.1

**Estado:** implementado y aprobado manualmente por el responsable  
**Responsable:** por asignar  
**Actualizado:** 2026-10-16

## Objetivo y permisos

SUPER_ADMIN consulta todos los hoteles, incluidos INACTIVE, y los datos básicos
de su administrador asignado. GET `/api/v1/superadmin/hotels` reutiliza la regla
`/api/v1/superadmin/**` de Spring Security. Sin JWT válido: 401; USER/HOTEL_ADMIN: 403.
No se alteran permisos ni operaciones del hotel propio ni del catálogo público.

## Datos y contrato

- `page`: entero >= 0, predeterminado 0; `page * 20 <= 2147483647`.
- Tamaño fijo de 20; no se ofrece tamaño ni orden configurables.
- `name`: opcional, recortado, máximo 180 caracteres; coincidencia parcial sin
  distinguir mayúsculas. Vacío equivale a ausencia. `%` y `_` son texto literal.
- `status`: opcional; únicamente `ACTIVE` o `INACTIVE`, exactos. Vacío o cualquier
  otro valor devuelve 400. Ambos filtros se combinan con AND.
- Orden fijo: nombre ASC, ID ASC, según la ordenación de PostgreSQL.
- Respuesta: `{content, page, size, totalElements, totalPages}`.
- Cada elemento: `id`, `name`, `address`, `city`, `status`, `adminUserId`,
  `adminName`, `adminEmail`. Sin hashes, contraseñas, tokens ni entidades JPA.
- Proyección JPQL con join por `hotels.admin_user_id`, sin filtrar al administrador
  por estado; consulta de contenido y count con los mismos filtros, sin N+1.
- Sin resultados/página fuera de rango: content vacío, metadatos correctos y 200.

## Flujo e interfaz

Ruta `/superadmin/hotels`, protegida por authGuard; enlace desde el dashboard
y regreso al resumen. SiteHeader conserva sesión y logout; sesión vencida vuelve
a login. Formularios reactivos y filtros/page en URL, como el catálogo existente.
Aplicar o limpiar vuelve a página 0. Reintentar conserva filtros y página.
Cambiar consulta cancela peticiones anteriores para no mostrar resultados obsoletos.
Listado responsive con estados de carga, vacío, error seguro y paginación.
Si una página queda fuera del resultado, permite volver a la primera.

## Criterios de aceptación

- Paginación estable de 20, filtros individuales/combinados y estados válidos.
- Inactivos incluidos y administrador correcto sin información sensible.
- JWT y permisos verificados; sin consultas por cada hotel.
- Angular verifica carga, datos, filtros/limpieza, paginación, errores y guard.
- Solo pruebas focalizadas y build Angular.

## Fuera de alcance y pendientes

Creación, edición, eliminación, reasignación de administradores y cambios de esquema.
La creación se implementa por separado en [7.3.2](superadmin-create-hotel.md).
No requiere dependencias nuevas. Pendiente revisión visual manual del incremento.

## Validación ejecutada

- `mvn -Dtest=SuperadminHotelIntegrationTest,DashboardIntegrationTest,AdminHotelHttpIntegrationTest test`:
  22 pruebas aprobadas sobre PostgreSQL 17.11. Las seis nuevas pruebas del listado
  verifican paginación/orden, filtros, datos del administrador, inactivos, vacío,
  validaciones y permisos. Fixtures propios revertidos mediante rollback.
- Una página completa ejecuta dos sentencias SQL (contenido/count) y carga cero
  entidades JPA, comprobado mediante estadísticas Hibernate solo en pruebas.
- `npm test -- --watch=false --include=src/app/features/superadmin/hotels.spec.ts --include=src/app/features/superadmin/dashboard.spec.ts --include=src/app/features/admin/hotel/admin-layout.spec.ts`:
  36 pruebas aprobadas. Incluye navegación, filtros, paginación, cancelación de
  peticiones obsoletas, vacío, reintento, guard, JWT y logout.
- `npm run build`: correcto. `git diff --check`: sin errores.
- No se ejecutaron suites completas. Validación manual de 7.3.1 aprobada posteriormente por el responsable.
