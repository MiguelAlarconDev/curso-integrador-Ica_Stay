# Modelo de datos

**Estado:** por diseñar.

PostgreSQL será la fuente de verdad para usuarios, hoteles, habitaciones, disponibilidad, reservas y estados de pago relacionados. El modelo entidad-relación, restricciones y migraciones se definirán antes de implementar entidades JPA.

## Entidades candidatas

- Usuario y rol/asignación a hotel.
- Hotel y habitación o tipo de habitación: definir si se reserva unidad física o inventario por tipo.
- Reserva, fechas, estado y relación con el huésped.
- Referencia de pago y eventos de confirmación, sin almacenar datos sensibles de tarjeta.

## Decisiones pendientes

1. ¿Cada `HOTEL_ADMIN` administra exactamente un hotel o varios?
2. ¿Cómo se expresa inventario y precio por fecha?
3. ¿Qué restricción transaccional impide reservas solapadas?
4. ¿Cuál es el ciclo de vida de reserva y qué sucede ante pago tardío, fallido o cancelado?
5. ¿Cómo se registran las referencias cruzadas con los servicios sin acoplar sus bases?

Redis mantendrá copias temporales de consultas; se invalidarán al cambiar oferta o reservas y toda reserva se verificará en PostgreSQL. MongoDB, si se incorpora, contendrá conversaciones, con política de retención y acceso por usuario por definir.
