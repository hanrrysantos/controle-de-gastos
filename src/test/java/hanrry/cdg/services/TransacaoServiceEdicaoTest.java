package hanrry.cdg.services;

import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.exceptions.AccessDeniedException;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.TransacaoMapperImpl;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceEdicaoTest {

    private final TransacaoRepository repository = mock(TransacaoRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransacaoService service = new TransacaoService(repository, categoriaRepository, userRepository, new TransacaoMapperImpl());

    private final User usuario = usuario(7L);

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(repository.save(any(Transacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void atualizaTransacaoETrocaCategoria() {
        Transacao transacao = transacao(10L, usuario, categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario));
        when(repository.findById(10L)).thenReturn(Optional.of(transacao));
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria(2L, "Salário", TipoTransacao.RECEITA, usuario)));

        TransacaoResponseDTO resposta = service.atualizar(10L, request("1500.00", "Pagamento", 2L));

        assertEquals(10L, resposta.getId());
        assertEquals("1500.00", resposta.getValor().toPlainString());
        assertEquals("Pagamento", resposta.getDescricao());
        assertEquals(2L, resposta.getCategoriaId());
        assertEquals("Salário", resposta.getNomeCategoria());
        assertEquals("RECEITA", resposta.getTipo());
        assertEquals(TipoTransacao.RECEITA, transacao.getTipo());
        assertEquals(usuario, transacao.getUser());
    }

    @Test
    void recusaAtualizarTransacaoInexistente() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.atualizar(10L, request("10.00", "Café", 1L)));
    }

    @Test
    void recusaAtualizarTransacaoDeOutroUsuario() {
        User outro = usuario(8L);
        when(repository.findById(10L)).thenReturn(Optional.of(transacao(10L, outro, categoria(5L, "Lazer", TipoTransacao.DESPESA, outro))));

        assertThrows(AccessDeniedException.class, () -> service.atualizar(10L, request("10.00", "Café", 5L)));
        verify(repository, never()).save(any());
    }

    @Test
    void recusaAtualizarParaCategoriaDeOutroUsuario() {
        when(repository.findById(10L)).thenReturn(Optional.of(transacao(10L, usuario, categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario))));
        when(categoriaRepository.findById(5L)).thenReturn(Optional.of(categoria(5L, "Lazer", TipoTransacao.DESPESA, usuario(8L))));

        assertThrows(AccessDeniedException.class, () -> service.atualizar(10L, request("10.00", "Café", 5L)));
        verify(repository, never()).save(any());
    }

    @Test
    void validaAtualizacaoComoNaCriacao() {
        assertThrows(BusinessRuleException.class, () -> service.atualizar(10L, request("10.001", "Café", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.atualizar(10L, request("10.00", " ", 1L)));
        verify(repository, never()).findById(any());
    }

    private static TransacaoRequestDTO request(String valor, String descricao, Long categoriaId) {
        TransacaoRequestDTO dto = new TransacaoRequestDTO();
        dto.setValor(new BigDecimal(valor));
        dto.setDescricao(descricao);
        dto.setData(LocalDate.of(2026, 10, 5));
        dto.setCategoriaId(categoriaId);
        return dto;
    }

    private static Transacao transacao(Long id, User user, Categoria categoria) {
        Transacao transacao = new Transacao();
        transacao.setId(id);
        transacao.setValor(new BigDecimal("20.00"));
        transacao.setDescricao("Antiga");
        transacao.setData(LocalDate.of(2026, 10, 1));
        transacao.setUser(user);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());
        return transacao;
    }

    private static Categoria categoria(Long id, String nome, TipoTransacao tipo, User user) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNome(nome);
        categoria.setTipo(tipo);
        categoria.setUser(user);
        return categoria;
    }

    private static User usuario(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
