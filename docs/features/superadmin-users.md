# Listado global de usuarios: 7.4.1

Estado: implementado y aprobado manualmente por el responsable. Actualizado: 2026-10-19.

SUPER_ADMIN consulta GET `/api/v1/superadmin/users`; JWT obligatorio. USER y HOTEL_ADMIN reciben 403; sin autenticacion, 401. No cambia el registro publico.

Parametros: `page` entero desde cero, con page*20 <= 2147483647; `q` opcional, recortado, maximo 254 caracteres, busqueda parcial literal sin distinguir mayusculas sobre nombre O correo; `role` opcional USER/HOTEL_ADMIN/SUPER_ADMIN; `status` opcional ACTIVE/INACTIVE. Valores invalidos producen 400. Filtros combinados con AND.

Respuesta: `{content,page,size,totalElements,totalPages}`, tamano fijo 20, orden nombre/ID. Cada fila contiene exclusivamente id, name, email, role y status. Proyeccion DTO y paginacion/count en PostgreSQL, sin contrasenas ni hashes ni entidades cargadas. Pagina fuera de rango devuelve contenido vacio.

Angular: `/superadmin/users`, guard SUPER_ADMIN, acceso desde dashboard y retorno; tabla responsive con filtros en URL, limpiar, paginacion, carga/vacio/error/reintento. switchMap cancela solicitudes anteriores; destruccion cancela solicitudes al navegar. Encabezado compartido conserva sesion/cierre de sesion.

Aceptacion: pruebas focalizadas de paginacion, orden, filtros, permisos, proteccion de datos, navegacion y cancelacion; build Angular. Revision manual aprobada por el responsable antes de 7.5.

Fuera de alcance: detalle, edicion y desactivacion de usuarios. Sin dependencias nuevas.


## 7.4.2: detalle de solo lectura
GET `/api/v1/superadmin/users/{id}` exclusivo SUPER_ADMIN. UUID invalido: 400; inexistente: 404. Incluye usuarios ACTIVE e INACTIVE. Respuesta: id, name, email, role, status, hotel. Hotel es null salvo HOTEL_ADMIN con hotel asignado; entonces contiene exclusivamente id, name, status (incluye hotel inactivo). Una consulta con LEFT JOIN y proyeccion DTO, sin cargar entidades ni secretos.

Ruta protegida `/superadmin/users/:id` desde el listado, conserva filtros al regresar. Muestra datos y enlace al detalle administrativo del hotel, carga, error/404 y reintento; cancela solicitudes obsoletas. No permite modificar usuarios. Pruebas focalizadas backend/Angular y build; revision visual manual aprobada por el responsable antes de 7.5.


## 7.4.3: estado y revocacion de sesiones
PATCH `/api/v1/superadmin/users/{id}/status`, SUPER_ADMIN, cuerpo exclusivamente `{ "status": "ACTIVE" | "INACTIVE" }`. Devuelve detalle seguro. UUID/cuerpo/estado invalidos y autodesactivacion/ultimo SUPER_ADMIN: 400; inexistente: 404; rol insuficiente: 403; sesion revocada: 401. Repetir el estado actual es idempotente.

Migracion V2 agrega `users.token_version BIGINT NOT NULL DEFAULT 0`. Cada transicion efectiva incrementa version; JWT nuevos incluyen `ver`. Filtro verifica firma, expiracion, existencia, estado ACTIVE, rol/correo y version actual con proyeccion segura por solicitud. JWT anteriores sin ver equivalen a cero: siguen validos solo en cuentas activas sin cambios. Reactivar nunca rehabilita JWT anteriores. Login ya filtra ACTIVE; una carrera con cambio de estado puede emitir un token que el filtro rechazara, nunca uno que recupere validez.

Bloqueo advisory transaccional PostgreSQL exclusivo 741003 serializa todos los cambios de estado entre instancias. Dentro del bloqueo se revalida actor/version/rol/estado y se cuenta SUPER_ADMIN activos antes de modificar. No se cambian roles, hashes, reservas ni hotel. Solicitudes que ya pasaron el filtro pueden terminar; nuevas autenticaciones posteriores al commit se rechazan. Auditoria en logger dedicado: actor UUID, objetivo UUID, resultado; exito solo tras commit, sin correo, tokens ni cuerpos.

UI: confirmacion explicita inline, cancelar, bloqueo de doble envio, mensajes especificos, detalle actualizado con respuesta y listado recargado al regresar conservando filtros. Autodesactivacion deshabilitada; backend sigue siendo autoridad.

Aceptacion: login inactivo, revocacion permanente y compatibilidad legacy, permisos, validaciones, carreras reales en PostgreSQL, conservacion de datos, confirmacion/errores/doble envio Angular; solo suites focalizadas y build. Pendiente despliegue coordinado/reinicio para aplicar V2; todas las instancias deben actualizarse para aplicar revocacion.

Validacion automatizada 7.4.3: 52 pruebas backend focalizadas aprobadas (41 de usuarios/autenticacion y 11 de auditoria/permisos afectados); 34 pruebas Angular de listado/detalle aprobadas; build Angular correcto. V2 validada sobre PostgreSQL local. Se adaptaron fixtures de permisos a cuentas persistidas: los JWT de identidades inexistentes o roles distintos al almacenado se rechazan. Recorrido manual con sesiones reales aprobado por el responsable antes de 7.5; mantener reinicio coordinado en futuros despliegues.


## 7.5: validacion integral y preparacion de cierre

Validacion manual de 7.2, 7.3.1-7.3.3 y 7.4.1-7.4.3 aprobada por el responsable; no repetida en 7.5.

- Backend: 188 casos relevantes en 20 clases sobre PostgreSQL. Primera ejecucion: 186 aprobados, 2 fallidos por un fixture de AdminRoomHttpIntegrationTest que firmaba un rol distinto al persistido. Se corrigio solamente ese fixture y se repitieron los dos casos: 2 aprobados, BUILD SUCCESS. Resultado acumulado: 188 aprobados, sin fallos pendientes. No se ejecuto la suite completa.
- Angular: 360 casos aprobados en 18 archivos seleccionados de SUPER_ADMIN, administracion, autenticacion, reservas, catalogo y core. Build Angular correcto.
- Contratos y seguridad: permisos por rol y pertenencia preservados; DTO seguros; BCrypt; cuentas inactivas y JWT revocados rechazados, incluso tras reactivar; compatibilidad de JWT sin ver mientras version=0. Autodesactivacion y ultimo SUPER_ADMIN protegidos, cambios concurrentes serializados y actor revalidado.
- Integridad: creacion hotel/administrador atomica; unicidad PostgreSQL y rollback comprobados; bloqueos de hotel/reserva preservados; cambios de estado solo actualizan sus recursos, sin reasignar administrador ni modificar reservas o intentos de pago. Intentos de pago no se presentan como ingresos.
- Git: diff --check correcto; revision de archivos nuevos y modificados sin secretos locales ni temporales incluidos. .env, codex-resultado.txt, target y dist excluidos. Sin commit/push/merge. Referencias locales main y origin/main coinciden con HEAD de la rama; comprobar cambios remotos antes del merge efectivo.
- Archivos actualizados en 7.5: README.md, specs superadmin-*.md y fixture de AdminRoomHttpIntegrationTest. Sin cambios funcionales ni dependencias.

Preparado tecnicamente para commit y revision de merge. En despliegue, aplicar V2 con Flyway y reiniciar todas las instancias para que usen la validacion de version JWT. No se verificaron proveedores de pagos externos (fuera del alcance implementado).
