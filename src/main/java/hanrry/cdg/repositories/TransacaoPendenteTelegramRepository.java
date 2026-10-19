package hanrry.cdg.repositories;

import hanrry.cdg.models.TransacaoPendenteTelegram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TransacaoPendenteTelegramRepository extends JpaRepository<TransacaoPendenteTelegram, String> {
    void deleteAllByExpiraEmBefore(Instant instante);

    @Modifying
    @Query("update TransacaoPendenteTelegram p set p.categoriaId = :destinoId, p.categoriaNome = :destinoNome where p.categoriaId = :origemId")
    int moverCategoria(@Param("origemId") Long origemId, @Param("destinoId") Long destinoId, @Param("destinoNome") String destinoNome);

    @Modifying
    @Query("delete from TransacaoPendenteTelegram p where p.categoriaId = :categoriaId")
    int deletarPorCategoria(@Param("categoriaId") Long categoriaId);
}
