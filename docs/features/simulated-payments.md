# Pagos simulados y confirmación (6.2 y 6.3)

**Estado:** en revisión  
**Responsable:** por asignar  
**Actualizado:** 2026-10-16

## Objetivo y permisos

Registrar resultados simulados y confirmar atómicamente reservas propias. Solo USER autenticado; identidad exclusivamente del JWT. Sin proveedor externo ni datos de tarjetas.

## Contrato

`POST /api/v1/payments/simulate`: cuerpo `{ "reservationId": "UUID", "result": "APPROVED" | "REJECTED" }`.
Respuesta 201: `{ "id": "UUID", "result": "APPROVED" | "REJECTED", "reference": "sim-UUID" }`.
Campos adicionales, campos obligatorios ausentes o resultado inválido: 400. Sin autenticación: 401; otro rol: 403; reserva ajena/inexistente: 404.

## Flujo y estados

Una transacción bloquea primero habitación y después reserva; el lookup inicial devuelve solo el ID de habitación para evitar cargar una reserva obsoleta antes del bloqueo. Después se valida propiedad, PENDING_PAYMENT y expiresAt estrictamente posterior al reloj del servidor. La retención es de **15 minutos**.

APPROVED comprueba solapamientos y guarda intento y CONFIRMED juntos. REJECTED guarda el intento sin cambiar estado ni vencimiento. Los bloqueos se mantienen hasta commit; cualquier fallo revierte ambos cambios. Estados no pendientes, vencimiento exacto/pasado, solapamiento o aprobación previa: 409 sin nuevo intento.

Un nuevo rechazo puede reintentarse mientras la retención siga vigente; cada solicitud aceptada crea un intento con referencia única y timestamps. Después de aprobar, cualquier reintento devuelve 409. No se ofrece reproducción idempotente de respuestas: el cliente puede consultar su reserva si pierde la respuesta.

## Criterios de aceptación

- Aprobación y confirmación atómicas; fallo de persistencia revierte ambas.
- Rechazo conserva vencimiento y permite reintentar durante la retención.
- Propiedad, roles, expiración exacta y estados terminales verificados.
- Dos aprobaciones concurrentes producen una sola confirmación y un intento aprobado.
- Pago y creación concurrentes serializan por habitación y no generan overbooking.

## Decisiones y límites

El alcance autorizado para 6.2/6.3 reemplaza la restricción anterior de no exponer simulación y la retención documental de 10 minutos. Se reutiliza payment_attempts sin migración ni dependencias. Fuera de alcance: pagos reales, reembolsos, frontend y administración de hotel.

## Validación

2026-10-16: 11 pruebas de PaymentIntegrationTest aprobadas sobre PostgreSQL 17.11, incluyendo concurrencia real, expiración al esperar el bloqueo y rollback ante violación de restricción después de escribir ambos registros. También pasaron 36 pruebas existentes seleccionadas: ReservationServiceTest, ReservationControllerTest, UserReservationIntegrationTest, SecurityAuthorizationIntegrationTest y AvailabilityIntegrationTest. No se ejecutó la suite completa. Un fallo inicial en la limpieza del fixture fue corregido; se retiraron los datos sintéticos de esa ejecución.
