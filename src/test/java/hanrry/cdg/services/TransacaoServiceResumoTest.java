package hanrry.cdg.services;

import hanrry.cdg.dtos.ResumoMensalResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TotalPorCategoria;
import hanrry.cdg.repositories.TransacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransacaoServiceResumoTest {

    private final TransacaoRepository repository = mock(TransacaoRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final TransacaoService service = new TransacaoService(repository, categoriaRepository, null, null);

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resumeMesComComparativoECategoriasOrdenadas() {
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        LocalDate fim = LocalDate.of(2026, 10, 31);
        when(repository.totalPorTipo(7L, TipoTransacao.RECEITA, inicio, fim)).thenReturn(new BigDecimal("1000.00"));
        when(repository.totalPorTipo(7L, TipoTransacao.DESPESA, inicio, fim)).thenReturn(new BigDecimal("250.50"));
        when(repository.totalPorTipo(7L, TipoTransacao.RECEITA, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(BigDecimal.ZERO);
        when(repository.totalPorTipo(7L, TipoTransacao.DESPESA, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(new BigDecimal("100"));
        when(repository.totalPorTipo(7L, TipoTransacao.RECEITA)).thenReturn(new BigDecimal("3000.00"));
        when(repository.totalPorTipo(7L, TipoTransacao.DESPESA)).thenReturn(new BigDecimal("400.00"));
        when(repository.totalPorCategoria(7L, inicio, fim)).thenReturn(List.of(
                new TotalPorCategoria(1L, new BigDecimal("50.50"), 2L),
                new TotalPorCategoria(2L, new BigDecimal("200.00"), 1L)));
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of(
                categoria(1L, "Mercado"), categoria(2L, "Aluguel"), categoria(3L, "Saúde")));

        ResumoMensalResponseDTO resumo = service.getResumoMensal("2026-10");

        assertEquals("2026-10", resumo.mes());
        assertEquals("749.50", resumo.saldo().toPlainString());
        assertEquals("0.00", resumo.receitasMesAnterior().toPlainString());
        assertEquals("100.00", resumo.despesasMesAnterior().toPlainString());
        assertEquals("2600.00", resumo.saldoAcumulado().toPlainString());
        assertEquals(3, resumo.quantidadeLancamentos());
        assertEquals(List.of("Aluguel", "Mercado", "Saúde"), resumo.categorias().stream().map(ResumoMensalResponseDTO.Categoria::nome).toList());
        assertEquals("0.00", resumo.categorias().get(2).total().toPlainString());
        assertEquals(0, resumo.categorias().get(2).quantidade());
    }

    @Test
    void recusaMesInvalido() {
        assertThrows(BusinessRuleException.class, () -> service.getResumoMensal("2026-13"));
    }

    private static Categoria categoria(Long id, String nome) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNome(nome);
        categoria.setTipo(TipoTransacao.DESPESA);
        return categoria;
    }
}
