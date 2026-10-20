# Dashboard SUPER_ADMIN — 7.2

**Estado:** implementado y aprobado manualmente por el responsable  
**Responsable:** por asignar  
**Actualizado:** 2026-10-16

## Objetivo y permisos

Consultar estadísticas globales persistidas, exclusivamente con JWT SUPER_ADMIN.
GET `/api/v1/superadmin/dashboard`: 200; sin JWT válido 401; USER/HOTEL_ADMIN 403.
Los endpoints HOTEL_ADMIN conservan su aislamiento y permisos.

## Datos y contrato

Respuesta: `hotels`, `rooms`, `users` (totales incluyendo inactivos),
`reservations` (total, pendingPayment, pendingCurrent, pendingExpired, confirmed,
cancelled, expired), `paymentAttempts` (mapa resultado → cantidad) y `generatedAt` UTC.
Usuarios incluye los tres roles. Pending vigente: expiresAt > generatedAt;
vencida: expiresAt <= generatedAt, incluyendo igualdad. No se escriben estados.
EXPIRED persistido se informa separado de PENDING_PAYMENT vencido.
APPROVED y REJECTED siempre aparecen, incluso en cero; otros resultados persistidos
se conservan. Los intentos son simulados, no ingresos ni pagos reales.

Cinco consultas agregadas, sin cargar entidades ni recorrer reservas en Java;
transacción de lectura REPEATABLE_READ para una instantánea consistente.

## Flujo y estados de interfaz

Login SUPER_ADMIN conduce a `/superadmin/dashboard`, protegida por authGuard.
Reutiliza SiteHeader, AuthService e interceptor; navegación al catálogo y logout.
Tarjetas responsive, carga anunciada, error seguro con reintento, ceros y mensaje
sin actividad cuando todos los totales son cero. Logout/expiración vuelve a login.

## Criterios de aceptación

- Totales reales, estados y frontera de vencimiento verificados con PostgreSQL.
- JWT y roles verificados; SUPER_ADMIN sigue excluido del hotel propio.
- Angular verifica carga, datos, vacío, error/reintento, navegación y guards.
- Solo pruebas focalizadas y build Angular.

## Fuera de alcance

Gestión de hoteles/usuarios, ingresos, proveedores externos, cambios de esquema,
dependencias nuevas y actualización automática de estados vencidos.

## Validación ejecutada

- Backend: `mvn -Dtest=DashboardIntegrationTest,SecurityAuthorizationIntegrationTest test`:
  16 pruebas aprobadas sobre PostgreSQL 17.11. Fixtures nuevos con rollback transaccional.
  Incluye estadísticas, inactivos, todos los roles, vencimiento anterior/exacto/posterior,
  conservación de estados, DTO y rechazo de accesos sin permiso.
- Angular: `npm test -- --watch=false --include=src/app/features/superadmin/dashboard.spec.ts --include=src/app/features/auth/login/login.spec.ts --include=src/app/features/admin/hotel/admin-layout.spec.ts --include=src/app/core/auth/auth.spec.ts`:
  77 pruebas aprobadas; datos HTTP simulados para verificar interfaz, reintento, vacío,
  Bearer, navegación, logout y límites entre roles.
- `npm run build`: correcto. No se ejecutaron suites completas.
- Validación manual aprobada por el responsable antes de 7.5; no se repitió en esta etapa.
