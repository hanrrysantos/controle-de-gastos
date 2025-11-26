package hanrry.cdg.dtos;

public class UserLoginDTO {

    private String email;
    private String senha;

    public UserLoginDTO() {}

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
