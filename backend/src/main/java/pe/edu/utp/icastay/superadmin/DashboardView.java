package pe.edu.utp.icastay.superadmin;

import java.time.Instant;
import java.util.Map;

public record DashboardView(long hotels, long rooms, long users,
        Reservations reservations, Map<String, Long> paymentAttempts, Instant generatedAt) {
    public record Reservations(long total, long pendingPayment, long pendingCurrent,
            long pendingExpired, long confirmed, long cancelled, long expired) {}
}
