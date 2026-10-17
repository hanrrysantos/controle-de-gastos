package hanrry.cdg.repositories;

import java.math.BigDecimal;

public record TotalPorCategoria(Long categoriaId, BigDecimal total, Long quantidade) {}
