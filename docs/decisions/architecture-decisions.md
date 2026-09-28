# Registro de decisiones arquitectónicas

**Actualizado:** 2026-09-28. Las decisiones nuevas deben incluir fecha, motivo, consecuencias y estado.

| Decisión | Estado | Motivo y consecuencia |
|---|---|---|
| Núcleo modular Spring Boot y frontend Angular | Acordada | Concentrar el dominio hotelero y permitir una interfaz separada |
| PostgreSQL como fuente transaccional | Acordada | Integridad de datos de reservas y usuarios |
| Tres roles: `USER`, `HOTEL_ADMIN`, `SUPER_ADMIN` | Acordada | Permisos según actor; validación de pertenencia en backend |
| Un `HOTEL_ADMIN` administra un hotel | Acordada 2026-09-28 | Relación uno a uno, `hotels.admin_user_id` único |
| Reserva de habitación física concreta | Acordada 2026-09-28 | Disponibilidad por habitación y fechas, salida exclusiva |
| Retención pendiente de pago por 10 minutos | Acordada 2026-09-28 | Vence por reloj del servidor y libera lógicamente la habitación |
| Servicios de pago, comprobantes y notificaciones | Objetivo acordado | Distribuir responsabilidades especializadas entre el equipo; integración progresiva |
| Redis para caché | Planeada | Acelerar consultas; invalidación y TTL por diseñar |
| Ollama con modelo configurable | Planeada | Respuestas naturales locales sobre contexto autorizado |
| MongoDB para conversaciones | Planeada | Historial documental en etapa posterior |
| Un repositorio y specs ligeras | Acordada | Facilitar colaboración y trazabilidad |
| Facturación electrónica SUNAT | Pendiente | Evaluar viabilidad y alcance real antes de prometer emisión válida |

Ver [modelo ER](../architecture/database.md), [spec de reservas](../features/reservations.md) y [API mínima](../architecture/api.md). Pendientes: cancelación y reembolso, precios variables y contrato de pago tardío.

Las decisiones conceptuales y estados de trabajo se mantienen en [Notion](https://app.notion.com/p/3e82918d49f4817f94f1c329d2ff194f?pvs=204). Este registro explica su traducción técnica. Cuando cambie una decisión, actualizar ambos lugares y la spec afectada.
