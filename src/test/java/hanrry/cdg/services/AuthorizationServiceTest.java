package hanrry.cdg.services;

import hanrry.cdg.models.User;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final AuthorizationService service = new AuthorizationService(repository);

    @Test
    void carregaUsuarioPorEmailComSenhaEPapel() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ana@cdg.com");
        user.setSenha("hash-da-senha");
        when(repository.findByEmail("ana@cdg.com")).thenReturn(Optional.of(user));

        UserDetails detalhes = service.loadUserByUsername("ana@cdg.com");

        assertEquals("ana@cdg.com", detalhes.getUsername());
        assertEquals("hash-da-senha", detalhes.getPassword());
        assertEquals(List.of("USER"), detalhes.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void aceitaLoginComEmailEmMaiusculasEEspacos() {
        User user = new User();
        user.setEmail("ana@cdg.com");
        user.setSenha("hash-da-senha");
        when(repository.findByEmail("ana@cdg.com")).thenReturn(Optional.of(user));

        assertEquals("ana@cdg.com", service.loadUserByUsername(" Ana@CDG.com ").getUsername());
    }

    @Test
    void lancaExcecaoParaEmailDesconhecido() {
        when(repository.findByEmail("x@cdg.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x@cdg.com"));
    }
}
