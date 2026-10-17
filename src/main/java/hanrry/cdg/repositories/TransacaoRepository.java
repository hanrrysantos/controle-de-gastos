package hanrry.cdg.repositories;

import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    List<Transacao> findByUserId(Long userId);

    @Query("""
            select new hanrry.cdg.repositories.TotalPorCategoria(t.categoria.id, sum(t.valor), count(t))
            from Transacao t
            where t.user.id = :userId and t.data between :inicio and :fim
            group by t.categoria.id
            """)
    List<TotalPorCategoria> totalPorCategoria(@Param("userId") Long userId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(t.valor), 0)
            from Transacao t
            where t.user.id = :userId and t.tipo = :tipo and t.data between :inicio and :fim
            """)
    BigDecimal totalPorTipo(@Param("userId") Long userId, @Param("tipo") TipoTransacao tipo, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("select coalesce(sum(t.valor), 0) from Transacao t where t.user.id = :userId and t.tipo = :tipo")
    BigDecimal totalPorTipo(@Param("userId") Long userId, @Param("tipo") TipoTransacao tipo);
}
