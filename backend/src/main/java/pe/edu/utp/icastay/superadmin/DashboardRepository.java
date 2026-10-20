package pe.edu.utp.icastay.superadmin;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class DashboardRepository {
    private final EntityManager entityManager;

    DashboardRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    DashboardView read(Instant now) {
        long hotels = entityManager.createQuery("select count(h) from HotelEntity h", Long.class).getSingleResult();
        long rooms = entityManager.createQuery("select count(r) from RoomEntity r", Long.class).getSingleResult();
        long users = entityManager.createQuery("select count(u) from UserEntity u", Long.class).getSingleResult();
        Object[] counts = (Object[]) entityManager.createNativeQuery("""
                select count(*),
                    count(*) filter (where status = 'PENDING_PAYMENT'),
                    count(*) filter (where status = 'PENDING_PAYMENT' and expires_at > :now),
                    count(*) filter (where status = 'PENDING_PAYMENT' and expires_at <= :now),
                    count(*) filter (where status = 'CONFIRMED'),
                    count(*) filter (where status = 'CANCELLED'),
                    count(*) filter (where status = 'EXPIRED')
                from reservations
                """).setParameter("now", now).getSingleResult();
        var reservations = new DashboardView.Reservations(number(counts[0]), number(counts[1]),
                number(counts[2]), number(counts[3]), number(counts[4]), number(counts[5]), number(counts[6]));
        Map<String, Long> payments = new LinkedHashMap<>();
        payments.put("APPROVED", 0L);
        payments.put("REJECTED", 0L);
        for (Object[] row : entityManager.createQuery(
                "select p.status, count(p) from PaymentAttemptEntity p group by p.status order by p.status",
                Object[].class).getResultList()) {
            payments.put((String) row[0], number(row[1]));
        }
        return new DashboardView(hotels, rooms, users, reservations, payments, now);
    }

    private long number(Object value) { return ((Number) value).longValue(); }
}
