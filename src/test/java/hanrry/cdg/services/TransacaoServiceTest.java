package hanrry.cdg.services;

import hanrry.cdg.dtos.DashboardResponseDTO;
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
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private final TransacaoRepository repository = mock(TransacaoRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransacaoService service = new TransacaoService(repository, categoriaRepository, userRepository, new TransacaoMapperImpl());

    private final User usuario = usuario(7L);

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(repository.save(any(Transacao.class))).thenAnswer(invocacao -> {
            Transacao transacao = invocacao.getArgument(0);
            transacao.setId(99L);
            return transacao;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criaTransacaoComTipoDaCategoriaEUsuarioAutenticado() {
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria(2L, "Salário", TipoTransacao.RECEITA, usuario)));

        TransacaoResponseDTO resposta = service.criar(request("1500.00", "Pagamento", 2L));

        ArgumentCaptor<Transacao> salva = ArgumentCaptor.forClass(Transacao.class);
        verify(repository).save(salva.capture());
        assertSame(usuario, salva.getValue().getUser());
        assertEquals(TipoTransacao.RECEITA, salva.getValue().getTipo());
        assertEquals(99L, resposta.getId());
        assertEquals("1500.00", resposta.getValor().toPlainString());
        assertEquals("Pagamento", resposta.getDescricao());
        assertEquals(LocalDate.of(2026, 10, 5), resposta.getData());
        assertEquals(2L, resposta.getCategoriaId());
        assertEquals("Salário", resposta.getNomeCategoria());
        assertEquals("RECEITA", resposta.getTipo());
    }

    @Test
    void recusaCriarComValorInvalido() {
        assertThrows(BusinessRuleException.class, () -> service.criar(request(null, "Café", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("0", "Café", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("-5.00", "Café", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("1.999", "Café", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("123456789012345678.00", "Café", 1L)));

        verify(repository, never()).save(any());
    }

    @Test
    void aceitaValorComZerosAMaisNasCasasDecimais() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario)));

        service.criar(request("10.5000", "Café", 1L));

        verify(repository).save(any());
    }

    @Test
    void recusaCriarSemDescricaoDataOuCategoria() {
        assertThrows(BusinessRuleException.class, () -> service.criar(request("10.00", null, 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("10.00", "  ", 1L)));
        assertThrows(BusinessRuleException.class, () -> service.criar(request("10.00", "Café", null)));
        TransacaoRequestDTO semData = request("10.00", "Café", 1L);
        semData.setData(null);
        assertThrows(BusinessRuleException.class, () -> service.criar(semData));

        verify(categoriaRepository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void recusaCriarComCategoriaInexistente() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.criar(request("10.00", "Café", 1L)));
        verify(repository, never()).save(any());
    }

    @Test
    void recusaCriarComCategoriaDeOutroUsuario() {
        when(categoriaRepository.findById(5L)).thenReturn(Optional.of(categoria(5L, "Lazer", TipoTransacao.DESPESA, usuario(8L))));

        assertThrows(AccessDeniedException.class, () -> service.criar(request("10.00", "Café", 5L)));
        verify(repository, never()).save(any());
    }

    @Test
    void listaApenasTransacoesDoUsuarioAutenticadoNaOrdemDaColecao() {
        Categoria mercado = categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario);
        usuario.getTransacoes().add(transacao(10L, "30.00", usuario, mercado));
        usuario.getTransacoes().add(transacao(11L, "12.50", usuario, mercado));

        List<TransacaoResponseDTO> lista = service.listar();

        assertEquals(List.of(10L, 11L), lista.stream().map(TransacaoResponseDTO::getId).toList());
        verify(userRepository).findById(7L);
    }

    @Test
    void listaVaziaSemTransacoes() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void deletaTransacaoDoProprioUsuario() {
        Transacao transacao = transacao(10L, "30.00", usuario, categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario));
        when(repository.findById(10L)).thenReturn(Optional.of(transacao));

        service.deletar(10L);

        verify(repository).delete(transacao);
    }

    @Test
    void recusaDeletarTransacaoInexistente() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deletar(10L));
        verify(repository, never()).delete(any());
    }

    @Test
    void recusaDeletarTransacaoDeOutroUsuario() {
        User outro = usuario(8L);
        when(repository.findById(10L)).thenReturn(Optional.of(transacao(10L, "30.00", outro, categoria(5L, "Lazer", TipoTransacao.DESPESA, outro))));

        assertThrows(AccessDeniedException.class, () -> service.deletar(10L));
        verify(repository, never()).delete(any());
    }

    @Test
    void dashboardSomaReceitasDespesasECalculaSaldo() {
        Categoria salario = categoria(1L, "Salário", TipoTransacao.RECEITA, usuario);
        Categoria mercado = categoria(2L, "Mercado", TipoTransacao.DESPESA, usuario);
        usuario.getTransacoes().add(transacao(10L, "3000.00", usuario, salario));
        usuario.getTransacoes().add(transacao(11L, "200.50", usuario, salario));
        usuario.getTransacoes().add(transacao(12L, "450.25", usuario, mercado));
        usuario.getTransacoes().add(transacao(13L, "3000.00", usuario, mercado));

        DashboardResponseDTO dashboard = service.getDashboard();

        assertEquals(0, new BigDecimal("3200.50").compareTo(dashboard.getTotalReceitas()));
        assertEquals(0, new BigDecimal("3450.25").compareTo(dashboard.getTotalDespesas()));
        assertEquals(0, new BigDecimal("-249.75").compareTo(dashboard.getSaldo()));
    }

    @Test
    void dashboardZeradoSemTransacoes() {
        DashboardResponseDTO dashboard = service.getDashboard();

        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.getTotalReceitas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.getTotalDespesas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.getSaldo()));
    }

    private static TransacaoRequestDTO request(String valor, String descricao, Long categoriaId) {
        TransacaoRequestDTO dto = new TransacaoRequestDTO();
        dto.setValor(valor == null ? null : new BigDecimal(valor));
        dto.setDescricao(descricao);
        dto.setData(LocalDate.of(2026, 10, 5));
        dto.setCategoriaId(categoriaId);
        return dto;
    }

    private static Transacao transacao(Long id, String valor, User user, Categoria categoria) {
        Transacao transacao = new Transacao();
        transacao.setId(id);
        transacao.setValor(new BigDecimal(valor));
        transacao.setDescricao("Lançamento " + id);
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
