package hanrry.cdg.dtos;

import hanrry.cdg.models.TipoTransacao;

public class CategoriaCreateDTO {
    private String nome;
    private TipoTransacao tipo;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoTransacao getTipo() { return tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
}