# Panel HOTEL_ADMIN — incremento 4.1

**Estado:** implementado, pendiente de revisión visual del equipo.
**Actualizado:** 2026-10-15.

## Objetivo y alcance

Layout y navegación administrativa standalone con carga diferida. Reutiliza
la paleta y tipografía globales, AuthService, sessionStorage y authGuard.
No hay consultas HTTP administrativas, métricas inventadas, tablas ni formularios.
La integración de perfil, habitaciones y reservas corresponde a 4.2, 4.3 y 4.4.

## Rutas y permisos

| Ruta | Sección |
|---|---|
| `/admin/hotel` | Resumen: bienvenida y cuatro indicadores «Próximamente» |
| `/admin/hotel/profile` | Mi hotel: aviso del incremento 4.2 |
| `/admin/hotel/rooms` | Habitaciones: aviso del incremento 4.3 |
| `/admin/hotel/reservations` | Reservas: aviso del incremento 4.4 |

El padre usa `canActivate` y `canActivateChild` con el authGuard existente.
Padre e hijas declaran `data.roles: ['HOTEL_ADMIN']`. Sin sesión se navega a
`/login`; USER y SUPER_ADMIN regresan a `/` conservando sesión. No se introduce
returnUrl: se conserva la estrategia existente. Login exitoso envía HOTEL_ADMIN
a `/admin/hotel`; USER y SUPER_ADMIN a `/`. Las tres rutas públicas siguen abiertas.

El catálogo ofrece «Mi panel» solo a HOTEL_ADMIN. El layout llama al logout
existente; al quedar vacía la sesión navega a `/login`, también ante expiración
o invalidación de sesión por el interceptor. Los guards comprueban cada navegación
hija. La autorización real continúa siendo responsabilidad del backend.

## Composición y arquitectura

`features/admin/hotel/` contiene:

- `admin-layout.ts/html/css`: navegación lateral, encabezado único con título,
  correo y rol; main con router-outlet y enlace para saltar al contenido.
- `admin.routes.ts`: rutas hijas lazy y metadatos de sección.
- `overview.ts`: cuatro tarjetas informativas sin cifras ficticias.
- `section-placeholder.ts`: presentación compartida de las tres secciones futuras.
- `admin-layout.spec.ts`: navegación real de RouterTestingHarness, permisos,
  sesión, enlace activo, rutas públicas y ausencia de HTTP administrativo.

App conserva su outlet. El layout no incorpora SiteHeader: ese encabezado sigue
en las páginas públicas. En móvil la navegación es compacta, visible en dos
columnas, sin drawer ni menú superpuesto. Correos largos pueden partirse y las
columnas usan minmax(0, 1fr). Controles de al menos 44 px, foco global visible y
aria-current para la ruta activa. Se conserva prefers-reduced-motion global.

## Validación y criterios de aceptación

- Las cuatro rutas muestran su título y una única ruta activa.
- HOTEL_ADMIN accede; otros roles y visitantes son redirigidos sin bucles.
- Logout limpia la sesión y sale del panel.
- Login distingue los tres roles; catálogo, login y registro siguen públicos.
- Ninguna pantalla administrativa solicita datos ni presenta métricas reales.
- `npm test -- --watch=false`: 85 pruebas exitosas.
- `npm run build`: correcto, panel y pantallas en chunks lazy.
- Chrome headless sobre build de producción: las cuatro rutas a 320, 375, 768
  y 1440 px sin scroll horizontal, títulos/aria-current correctos y encabezado
  único. Capturas de Resumen revisadas a 320 y 1440 px. Movimiento reducido
  comprobado con scroll-behavior auto y correo largo de prueba.
- `git diff --check`: sin errores.

Las pruebas usan JWT sintéticos y HTTP simulado, sin cuentas ni escrituras reales.
No se validó un login real contra Spring Boot para este incremento.

## Pendientes

Integración y formularios 4.2/4.3, consulta 4.4 y métricas cuando existan contratos.
Sin cambios en backend, base de datos, roles, interceptor ni formato JWT.
