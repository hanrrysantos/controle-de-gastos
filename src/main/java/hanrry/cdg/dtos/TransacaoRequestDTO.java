package hanrry.cdg.dtos;

import java.time.LocalDate;

public class TransacaoRequestDTO {
    private Double valor;
    private String descricao;
    private LocalDate data;
    private Long categoriaId;

    public TransacaoRequestDTO() {}

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
}