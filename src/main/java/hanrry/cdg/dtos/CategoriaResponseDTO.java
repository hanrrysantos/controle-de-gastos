package hanrry.cdg.dtos;

import hanrry.cdg.models.TipoTransacao;

public class CategoriaResponseDTO {
    private Long id;
    private String nome;
    private TipoTransacao tipo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoTransacao getTipo() { return tipo; }
    public void setTipo(TipoTransacao tipo) { this.tipo = tipo; }
}