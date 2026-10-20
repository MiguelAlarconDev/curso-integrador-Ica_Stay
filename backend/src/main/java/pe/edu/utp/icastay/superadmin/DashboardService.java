package pe.edu.utp.icastay.superadmin;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardRepository repository;
    private final Clock clock;

    @Autowired
    public DashboardService(DashboardRepository repository) { this(repository, Clock.systemUTC()); }

    DashboardService(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardView get() { return repository.read(Instant.now(clock)); }
}
