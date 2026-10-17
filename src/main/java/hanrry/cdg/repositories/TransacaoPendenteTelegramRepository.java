package hanrry.cdg.repositories;

import hanrry.cdg.models.TransacaoPendenteTelegram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TransacaoPendenteTelegramRepository extends JpaRepository<TransacaoPendenteTelegram, String> {
    void deleteAllByExpiraEmBefore(Instant instante);
}
