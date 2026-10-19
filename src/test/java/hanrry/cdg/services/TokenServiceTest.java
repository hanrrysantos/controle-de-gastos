package hanrry.cdg.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import hanrry.cdg.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SEGREDO = "segredo-de-teste";

    private final TokenService service = new TokenService();

    @BeforeEach
    void configurarSegredo() {
        // O segredo é injetado via @Value em campo privado, sem construtor.
        ReflectionTestUtils.setField(service, "secret", SEGREDO);
    }

    @Test
    void tokenGeradoValidaComIdDoUsuario() {
        String token = service.generateToken(usuario(5L, "ana@cdg.com"));

        assertEquals("id:5", service.validateToken(token));
    }

    @Test
    void tokenDeContaTelegramSemEmailUsaSujeitoTelegram() {
        String token = service.generateToken(usuario(9L, null));

        assertEquals("telegram:9", JWT.decode(token).getSubject());
        assertEquals("id:9", service.validateToken(token));
    }

    @Test
    void tokenExpiraEmUmDia() {
        Instant expiracao = JWT.decode(service.generateToken(usuario(5L, "ana@cdg.com"))).getExpiresAtAsInstant();

        Instant esperado = Instant.now().plus(1, ChronoUnit.DAYS);
        assertTrue(Math.abs(expiracao.getEpochSecond() - esperado.getEpochSecond()) < 60);
    }

    @Test
    void tokenSemClaimUserIdRetornaSujeito() {
        String token = JWT.create().withIssuer("cdg-api").withSubject("ana@cdg.com")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(SEGREDO));

        assertEquals("ana@cdg.com", service.validateToken(token));
    }

    @Test
    void tokenInvalidoRetornaVazio() {
        assertEquals("", service.validateToken("nao-e-um-jwt"));
    }

    @Test
    void tokenExpiradoRetornaVazio() {
        String token = JWT.create().withIssuer("cdg-api").withClaim("userId", 5L)
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(SEGREDO));

        assertEquals("", service.validateToken(token));
    }

    @Test
    void tokenAssinadoComOutroSegredoRetornaVazio() {
        String token = JWT.create().withIssuer("cdg-api").withClaim("userId", 5L)
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256("outro-segredo"));

        assertEquals("", service.validateToken(token));
    }

    @Test
    void tokenDeOutroEmissorRetornaVazio() {
        String token = JWT.create().withIssuer("outra-api").withClaim("userId", 5L)
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(SEGREDO));

        assertEquals("", service.validateToken(token));
    }

    private static User usuario(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
