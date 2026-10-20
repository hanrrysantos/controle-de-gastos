package hanrry.cdg.dtos;

import hanrry.cdg.models.TipoTransacao;

import java.math.BigDecimal;

public class CategoriaCreateDTO {
    private String nome;
    private TipoTransacao tipo;
    private BigDecimal metaMensal;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoTransacao getTipo() { return tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
    public BigDecimal getMetaMensal() { return metaMensal; }
    public void setMetaMensal(BigDecimal metaMensal) { this.metaMensal = metaMensal; }
}