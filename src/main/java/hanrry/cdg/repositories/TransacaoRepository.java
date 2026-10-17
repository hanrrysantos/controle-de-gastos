package hanrry.cdg.repositories;

import hanrry.cdg.models.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    List<Transacao> findByUserId(Long userId);
}
