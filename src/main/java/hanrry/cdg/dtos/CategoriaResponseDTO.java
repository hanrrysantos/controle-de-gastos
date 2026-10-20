package hanrry.cdg.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import hanrry.cdg.models.TipoTransacao;

import java.math.BigDecimal;

public class CategoriaResponseDTO {
    private Long id;
    private String nome;
    private TipoTransacao tipo;
    private long quantidadeLancamentos;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal metaMensal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoTransacao getTipo() { return tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
    public long getQuantidadeLancamentos() { return quantidadeLancamentos; }
    public void setQuantidadeLancamentos(long quantidadeLancamentos) { this.quantidadeLancamentos = quantidadeLancamentos; }
    public BigDecimal getMetaMensal() { return metaMensal; }
    public void setMetaMensal(BigDecimal metaMensal) { this.metaMensal = metaMensal; }
}