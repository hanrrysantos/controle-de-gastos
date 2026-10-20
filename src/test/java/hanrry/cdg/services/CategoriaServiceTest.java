package hanrry.cdg.services;

import hanrry.cdg.dtos.CategoriaCreateDTO;
import hanrry.cdg.dtos.CategoriaResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.CategoriaMapperImpl;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.QuantidadePorCategoria;
import hanrry.cdg.repositories.TransacaoPendenteTelegramRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaServiceTest {

    private final CategoriaRepository repository = mock(CategoriaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransacaoRepository transacaoRepository = mock(TransacaoRepository.class);
    private final TransacaoPendenteTelegramRepository pendenteRepository = mock(TransacaoPendenteTelegramRepository.class);
    private final CategoriaService service = new CategoriaService(repository, userRepository, transacaoRepository, pendenteRepository, new CategoriaMapperImpl());

    private final User usuario = usuario(7L);

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
        when(repository.save(any(Categoria.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listaCategoriasComQuantidadeDeLancamentos() {
        usuario.getCategorias().addAll(List.of(categoria(1L, "Mercado", TipoTransacao.DESPESA), categoria(2L, "Lazer", TipoTransacao.DESPESA)));
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(transacaoRepository.quantidadePorCategoria(7L)).thenReturn(List.of(new QuantidadePorCategoria(1L, 4L)));

        List<CategoriaResponseDTO> categorias = service.listar();

        assertEquals(4, categorias.get(0).getQuantidadeLancamentos());
        assertEquals(0, categorias.get(1).getQuantidadeLancamentos());
    }

    @Test
    void atualizaNomeETipoSemLancamentos() {
        Categoria categoria = categoria(1L, "Mercado", TipoTransacao.DESPESA);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(0L);

        CategoriaResponseDTO resposta = service.atualizar(1L, dto("Freelas", TipoTransacao.RECEITA));

        assertEquals("Freelas", resposta.getNome());
        assertEquals(TipoTransacao.RECEITA, resposta.getTipo());
        assertEquals(0, resposta.getQuantidadeLancamentos());
        assertEquals("Freelas", categoria.getNome());
    }

    @Test
    void atualizaNomeComLancamentosMantendoTipo() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(3L);

        CategoriaResponseDTO resposta = service.atualizar(1L, dto("Supermercado", TipoTransacao.DESPESA));

        assertEquals("Supermercado", resposta.getNome());
        assertEquals(3, resposta.getQuantidadeLancamentos());
    }

    @Test
    void recusaMudarTipoDeCategoriaComLancamentos() {
        Categoria categoria = categoria(1L, "Mercado", TipoTransacao.DESPESA);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(2L);

        BusinessRuleException erro = assertThrows(BusinessRuleException.class, () -> service.atualizar(1L, dto("Mercado", TipoTransacao.RECEITA)));

        assertEquals("Não é possível mudar o tipo de uma categoria que já tem lançamentos.", erro.getMessage());
        assertEquals(TipoTransacao.DESPESA, categoria.getTipo());
        verify(repository, never()).save(any());
    }

    @Test
    void defineERemoveMetaMensalDeDespesa() {
        Categoria categoria = categoria(1L, "Mercado", TipoTransacao.DESPESA);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria));

        CategoriaResponseDTO resposta = service.atualizar(1L, dto("Mercado", TipoTransacao.DESPESA, "800"));
        assertEquals("800.00", resposta.getMetaMensal().toPlainString());
        assertEquals("800.00", categoria.getMetaMensal().toPlainString());

        service.atualizar(1L, dto("Mercado", TipoTransacao.DESPESA, null));
        assertEquals(null, categoria.getMetaMensal());
    }

    @Test
    void criaCategoriaComMetaMensal() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario));

        CategoriaResponseDTO resposta = service.criar(dto("Lazer", TipoTransacao.DESPESA, "150.5"));

        assertEquals("150.50", resposta.getMetaMensal().toPlainString());
    }

    @Test
    void recusaMetaInvalidaOuEmReceita() {
        assertEquals("Metas mensais só podem ser definidas para categorias de despesa.",
                assertThrows(BusinessRuleException.class, () -> service.criar(dto("Salário", TipoTransacao.RECEITA, "100"))).getMessage());
        assertThrows(BusinessRuleException.class, () -> service.criar(dto("Lazer", TipoTransacao.DESPESA, "0")));
        assertThrows(BusinessRuleException.class, () -> service.criar(dto("Lazer", TipoTransacao.DESPESA, "-10")));
        assertThrows(BusinessRuleException.class, () -> service.criar(dto("Lazer", TipoTransacao.DESPESA, "10.001")));
        verify(repository, never()).save(any());
    }

    @Test
    void recusaAtualizarCategoriaDeOutroUsuarioOuInexistente() {
        when(repository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.atualizar(9L, dto("X", TipoTransacao.DESPESA)));
        assertThrows(ResourceNotFoundException.class, () -> service.deletar(9L, null));
    }

    @Test
    void validaAtualizacaoComoNaCriacao() {
        assertThrows(BusinessRuleException.class, () -> service.atualizar(1L, dto(" ", TipoTransacao.DESPESA)));
        assertThrows(BusinessRuleException.class, () -> service.atualizar(1L, dto("Mercado", null)));
    }

    @Test
    void deletaCategoriaSemLancamentos() {
        Categoria categoria = categoria(1L, "Mercado", TipoTransacao.DESPESA);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(0L);

        service.deletar(1L, null);

        verify(pendenteRepository).deletarPorCategoria(1L);
        verify(repository).delete(categoria);
        verify(transacaoRepository, never()).moverCategoria(anyLong(), any());
    }

    @Test
    void recusaDeletarComLancamentosSemDestino() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(1L);

        BusinessRuleException erro = assertThrows(BusinessRuleException.class, () -> service.deletar(1L, null));

        assertEquals("Esta categoria tem 1 lançamento. Escolha para qual categoria movê-los.", erro.getMessage());
        verify(repository, never()).delete(any());
    }

    @Test
    void mensagemNoPluralParaVariosLancamentos() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(5L);

        BusinessRuleException erro = assertThrows(BusinessRuleException.class, () -> service.deletar(1L, null));

        assertEquals("Esta categoria tem 5 lançamentos. Escolha para qual categoria movê-los.", erro.getMessage());
    }

    @Test
    void moveLancamentosEDeleta() {
        Categoria categoria = categoria(1L, "Mercado", TipoTransacao.DESPESA);
        Categoria destino = categoria(2L, "Supermercado", TipoTransacao.DESPESA);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria));
        when(repository.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(destino));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(3L);

        service.deletar(1L, 2L);

        verify(transacaoRepository).moverCategoria(1L, destino);
        verify(pendenteRepository).moverCategoria(1L, 2L, "Supermercado");
        verify(repository).delete(categoria);
    }

    @Test
    void recusaMoverParaCategoriaDeOutroTipo() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(repository.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(categoria(2L, "Salário", TipoTransacao.RECEITA)));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(3L);

        assertThrows(BusinessRuleException.class, () -> service.deletar(1L, 2L));
        verify(transacaoRepository, never()).moverCategoria(anyLong(), any());
        verify(repository, never()).delete(any());
    }

    @Test
    void recusaMoverParaAPropriaCategoria() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(3L);

        assertThrows(BusinessRuleException.class, () -> service.deletar(1L, 1L));
        verify(repository, never()).delete(any());
    }

    @Test
    void recusaMoverParaCategoriaDeOutroUsuario() {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(categoria(1L, "Mercado", TipoTransacao.DESPESA)));
        when(repository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());
        when(transacaoRepository.countByCategoriaId(1L)).thenReturn(3L);

        assertThrows(BusinessRuleException.class, () -> service.deletar(1L, 3L));
        verify(repository, never()).delete(any());
    }

    private static CategoriaCreateDTO dto(String nome, TipoTransacao tipo) {
        return dto(nome, tipo, null);
    }

    private static CategoriaCreateDTO dto(String nome, TipoTransacao tipo, String metaMensal) {
        CategoriaCreateDTO dto = new CategoriaCreateDTO();
        dto.setNome(nome);
        dto.setTipo(tipo);
        dto.setMetaMensal(metaMensal == null ? null : new BigDecimal(metaMensal));
        return dto;
    }

    private Categoria categoria(Long id, String nome, TipoTransacao tipo) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNome(nome);
        categoria.setTipo(tipo);
        categoria.setUser(usuario);
        return categoria;
    }

    private static User usuario(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
