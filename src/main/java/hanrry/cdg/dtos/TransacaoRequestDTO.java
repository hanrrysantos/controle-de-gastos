package hanrry.cdg.dtos;

import java.time.LocalDate;
import java.math.BigDecimal;

public class TransacaoRequestDTO {
    private BigDecimal valor;
    private String descricao;
    private LocalDate data;
    private Long categoriaId;

    public TransacaoRequestDTO() {}

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
}
