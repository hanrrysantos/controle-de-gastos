package hanrry.cdg.services;

import hanrry.cdg.dtos.UserRegisterDTO;
import hanrry.cdg.dtos.UserResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.UserMapperImpl;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(repository, new UserMapperImpl(), passwordEncoder);

    @BeforeEach
    void configurar() {
        when(passwordEncoder.encode(any())).thenAnswer(invocacao -> "hash:" + invocacao.getArgument(0));
        when(repository.save(any(User.class))).thenAnswer(invocacao -> {
            User user = invocacao.getArgument(0);
            user.setId(42L);
            return user;
        });
    }

    @Test
    void cadastraUsuarioComSenhaCriptografada() {
        when(repository.findByEmail("ana@cdg.com")).thenReturn(Optional.empty());

        UserResponseDTO resposta = service.cadastrar(registro("Ana", "ana@cdg.com", "segredo123"));

        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(repository).save(salvo.capture());
        assertEquals("hash:segredo123", salvo.getValue().getSenha());
        assertEquals("Ana", salvo.getValue().getNome());
        assertEquals("ana@cdg.com", salvo.getValue().getEmail());
        assertEquals(42L, resposta.getId());
        assertEquals("Ana", resposta.getNome());
        assertEquals("ana@cdg.com", resposta.getEmail());
    }

    @Test
    void recusaCadastroComEmailJaEmUso() {
        when(repository.findByEmail("ana@cdg.com")).thenReturn(Optional.of(new User()));

        BusinessRuleException erro = assertThrows(BusinessRuleException.class,
                () -> service.cadastrar(registro("Ana", "ana@cdg.com", "segredo123")));

        assertEquals("Este e-mail já está em uso.", erro.getMessage());
        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void recusaCadastroComCamposVaziosOuAusentes() {
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro(null, "ana@cdg.com", "s")));
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro(" ", "ana@cdg.com", "s")));
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro("Ana", null, "s")));
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro("Ana", "", "s")));
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro("Ana", "ana@cdg.com", null)));
        assertThrows(BusinessRuleException.class, () -> service.cadastrar(registro("Ana", "ana@cdg.com", "   ")));

        verify(repository, never()).findByEmail(any());
        verify(repository, never()).save(any());
    }

    @Test
    void buscaUsuarioPorEmail() {
        User user = new User();
        user.setId(3L);
        user.setNome("Bia");
        user.setEmail("bia@cdg.com");
        user.setSenha("hash");
        when(repository.findByEmail("bia@cdg.com")).thenReturn(Optional.of(user));

        UserResponseDTO resposta = service.buscarPorEmail("bia@cdg.com");

        assertEquals(3L, resposta.getId());
        assertEquals("Bia", resposta.getNome());
    }

    @Test
    void falhaAoBuscarEmailInexistente() {
        when(repository.findByEmail("x@cdg.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.buscarPorEmail("x@cdg.com"));
    }

    private static UserRegisterDTO registro(String nome, String email, String senha) {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setNome(nome);
        dto.setEmail(email);
        dto.setSenha(senha);
        return dto;
    }
}
