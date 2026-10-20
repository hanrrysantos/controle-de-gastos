package hanrry.cdg.services;

import hanrry.cdg.dtos.TransacaoInterpretadaDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiServiceTest {

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String RESPOSTA = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"valor\\":30,\\"descricao\\":\\"Mercado\\",\\"data\\":\\"2026-10-19\\",\\"tipo\\":\\"DESPESA\\",\\"categoria\\":\\"Alimentação\\",\\"categoriaNovaInformada\\":false}"}]}}]}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

    private GeminiService service(String modelos) {
        return new GeminiService("chave", modelos, JsonMapper.builder().build(), builder);
    }

    @Test
    void usaOModeloPrincipalQuandoEleResponde() {
        GeminiService service = service("principal,reserva");
        servidor.expect(requestTo(URL.formatted("principal"))).andExpect(header("x-goog-api-key", "chave"))
                .andRespond(withSuccess(RESPOSTA, MediaType.APPLICATION_JSON));

        TransacaoInterpretadaDTO dto = service.interpretar("gastei 30 no mercado", List.of());

        assertEquals(0, new BigDecimal("30").compareTo(dto.valor()));
        assertEquals("Alimentação", dto.categoria());
        servidor.verify();
    }

    @Test
    void tentaOModeloReservaQuandoOPrincipalEstaSobrecarregado() {
        GeminiService service = service("principal, reserva");
        servidor.expect(requestTo(URL.formatted("principal"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo(URL.formatted("reserva"))).andRespond(withSuccess(RESPOSTA, MediaType.APPLICATION_JSON));

        TransacaoInterpretadaDTO dto = service.interpretar("gastei 30 no mercado", List.of());

        assertEquals("Mercado", dto.descricao());
        servidor.verify();
    }

    @Test
    void falhaQuandoTodosOsModelosEstaoIndisponiveis() {
        GeminiService service = service("principal,reserva");
        servidor.expect(requestTo(URL.formatted("principal"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        servidor.expect(requestTo(URL.formatted("reserva"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class, () -> service.interpretar("gastei 30", List.of()));
        servidor.verify();
    }

    @Test
    void naoTentaOutroModeloQuandoOErroNaoETemporario() {
        GeminiService service = service("principal,reserva");
        servidor.expect(requestTo(URL.formatted("principal"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(HttpClientErrorException.class, () -> service.interpretar("gastei 30", List.of()));
        servidor.verify();
    }
}
