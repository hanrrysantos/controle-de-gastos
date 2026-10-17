package hanrry.cdg.dtos;

import java.math.BigDecimal;

public record TransacaoInterpretadaDTO(BigDecimal valor, String descricao, String data, String tipo,
                                       String categoria, Boolean categoriaNovaInformada) {
}
