package hanrry.cdg.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import hanrry.cdg.models.TipoTransacao;

import java.math.BigDecimal;
import java.util.List;

public record ResumoMensalResponseDTO(
        String mes,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalReceitas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalDespesas,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal receitasMesAnterior,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal despesasMesAnterior,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoAcumulado,
        long quantidadeLancamentos,
        List<Categoria> categorias) {

    public record Categoria(
            Long id,
            String nome,
            TipoTransacao tipo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
            long quantidade) {}
}
