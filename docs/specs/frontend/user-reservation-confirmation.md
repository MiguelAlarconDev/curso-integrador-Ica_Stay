# 5.3 — Selección y confirmación de reserva

Estado: en revisión. Disponibilidad permite seleccionar habitación conservando
criterios de la consulta exitosa; editar invalida selección. Resumen con hotel,
habitación, fechas, huéspedes, noches e importe estimado. Confirmación explícita
antes del POST `/api/v1/reservations`, exclusivamente USER, payload de cuatro
campos: roomId, checkIn, checkOut, guests. Identidad/precio exclusivamente backend.

Visitantes van a login con returnUrl limitado a `/hotels/{id}` y parámetros
conocidos de catálogo/fechas/huéspedes. Sin identidad, JWT ni selección persistida.
USER vuelve, reconsulta y debe seleccionar/confirmar otra vez. Otros roles mantienen
su destino habitual. Retorno externo, segmentos adicionales y fragmentos rechazados.

201 presenta ReservationView real, PENDING_PAYMENT y expiresAt UTC: no es una
reserva confirmada ni pago realizado. Retención 15 minutos sin cambios. Se bloquean
duplicados y cambios de selección durante envío. Tras éxito/resultado incierto se
bloquean nuevas reservas en esa vista. No hay reintentos automáticos de escrituras.
400 permite revisar datos; 401 requiere login; 403 indica rol; 404/409 ofrecen
reconsulta. Red/otros errores se consideran resultado incierto, sin afirmar que
la creación falló. Sin idempotencia ni consulta de historial, verificar con el hotel.

Backend rechaza entrada pasada según UTC y mantiene bloqueo/transacción/precio
histórico. Sin pagos, cancelaciones, historial, migraciones ni cambios administrativos.
Pruebas: payload, roles, confirmación, duplicados, retorno, error incierto, resultado
real y regresión de concurrencia. Pendiente revisión manual de móvil/teclado y flujo
login → reconsulta → selección → creación con cuenta USER de desarrollo.
