package hanrry.cdg.services;

import hanrry.cdg.dtos.TransacaoInterpretadaDTO;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.TransacaoPendenteTelegram;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoPendenteTelegramRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramTransactionServiceTest {

    private static final Long TELEGRAM_ID = 555L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final TransacaoRepository transacaoRepository = mock(TransacaoRepository.class);
    private final TransacaoPendenteTelegramRepository pendingRepository = mock(TransacaoPendenteTelegramRepository.class);
    private final GeminiService geminiService = mock(GeminiService.class);
    private final TelegramTransactionService service = new TelegramTransactionService(
            userRepository, categoriaRepository, transacaoRepository, pendingRepository, geminiService);

    private final User usuario = usuario(7L);

    @BeforeEach
    void configurar() {
        usuario.setTelegramUserId(TELEGRAM_ID);
        when(userRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.of(usuario));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    // --- preparar ---

    @Test
    void preparaPendenteComCategoriaExistente() {
        Categoria mercado = categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario);
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of(mercado));
        interpretacao(new BigDecimal("32.5"), " Almoço ", "2026-10-05", "DESPESA", "mercado");

        TelegramTransactionService.Resposta resposta = service.preparar(TELEGRAM_ID, "Gastei 32,50 no almoço");

        TransacaoPendenteTelegram pendente = pendenteSalva();
        assertEquals("confirmar:" + pendente.getId(), resposta.callbackConfirmacao());
        assertEquals("32.50", pendente.getValor().toPlainString());
        assertEquals("Almoço", pendente.getDescricao());
        assertEquals(LocalDate.of(2026, 10, 5), pendente.getData());
        assertEquals(1L, pendente.getCategoriaId());
        assertEquals(TipoTransacao.DESPESA, pendente.getTipo());
        assertEquals(TELEGRAM_ID, pendente.getTelegramUserId());
        assertTrue(pendente.getExpiraEm().isAfter(Instant.now().plusSeconds(590)));
        assertTrue(resposta.texto().contains("05/10/2026"));
        assertTrue(!resposta.texto().contains("(nova)"));
        verify(pendingRepository).deleteAllByExpiraEmBefore(any());
    }

    @Test
    void preparaPendenteMarcandoCategoriaNova() {
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of());
        interpretacao(new BigDecimal("1500"), "Freela", "2026-10-05", "RECEITA", "Freelas");

        TelegramTransactionService.Resposta resposta = service.preparar(TELEGRAM_ID, "Recebi 1500 de freela");

        TransacaoPendenteTelegram pendente = pendenteSalva();
        assertNull(pendente.getCategoriaId());
        assertEquals("Freelas", pendente.getCategoriaNome());
        assertEquals(TipoTransacao.RECEITA, pendente.getTipo());
        assertTrue(resposta.texto().contains("(nova)"));
    }

    @Test
    void preparaComCategoriaOutrosQuandoNaoInformada() {
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of());
        interpretacao(new BigDecimal("10"), "Algo", null, null, null);

        service.preparar(TELEGRAM_ID, "gastei 10");

        TransacaoPendenteTelegram pendente = pendenteSalva();
        assertEquals("Outros", pendente.getCategoriaNome());
        assertEquals(TipoTransacao.DESPESA, pendente.getTipo());
        assertTrue(pendente.getData() != null);
    }

    @Test
    void naoPreparaSemValorValido() {
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of());
        interpretacao(null, "Almoço", null, null, null);
        assertNull(service.preparar(TELEGRAM_ID, "almocei").callbackConfirmacao());

        interpretacao(new BigDecimal("-3"), "Almoço", null, null, null);
        assertNull(service.preparar(TELEGRAM_ID, "almocei").callbackConfirmacao());

        interpretacao(new BigDecimal("3.333"), "Almoço", null, null, null);
        assertNull(service.preparar(TELEGRAM_ID, "almocei").callbackConfirmacao());

        verify(pendingRepository, never()).save(any());
    }

    @Test
    void naoPreparaComTipoOuDataIlegiveis() {
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of());
        interpretacao(new BigDecimal("10"), "Café", "ontem", "DESPESA", null);
        assertNull(service.preparar(TELEGRAM_ID, "café").callbackConfirmacao());

        interpretacao(new BigDecimal("10"), "Café", null, "TRANSFERENCIA", null);
        assertNull(service.preparar(TELEGRAM_ID, "café").callbackConfirmacao());

        verify(pendingRepository, never()).save(any());
    }

    @Test
    void naoPreparaParaTelegramSemConta() {
        when(userRepository.findByTelegramUserId(1L)).thenReturn(Optional.empty());

        TelegramTransactionService.Resposta resposta = service.preparar(1L, "gastei 10");

        assertNull(resposta.callbackConfirmacao());
        verify(geminiService, never()).interpretar(anyString(), anyList());
    }

    // --- confirmar ---

    @Test
    void confirmaPendenteComCategoriaExistente() {
        Categoria mercado = categoria(1L, "Mercado", TipoTransacao.DESPESA, usuario);
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 1L, "Mercado", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(mercado));

        String resposta = service.confirmar("p1", TELEGRAM_ID);

        assertTrue(resposta.startsWith("✅"));
        Transacao salva = transacaoSalva();
        assertEquals("32.50", salva.getValor().toPlainString());
        assertEquals("Almoço", salva.getDescricao());
        assertSame(mercado, salva.getCategoria());
        assertSame(usuario, salva.getUser());
        assertEquals(TipoTransacao.DESPESA, salva.getTipo());
        verify(pendingRepository).delete(pendente);
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    void confirmaCriandoCategoriaNova() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, null, "Delivery", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of(categoria(9L, "Delivery", TipoTransacao.RECEITA, usuario)));

        service.confirmar("p1", TELEGRAM_ID);

        ArgumentCaptor<Categoria> criada = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).save(criada.capture());
        assertEquals("Delivery", criada.getValue().getNome());
        assertEquals(TipoTransacao.DESPESA, criada.getValue().getTipo());
        assertSame(usuario, criada.getValue().getUser());
        assertSame(criada.getValue(), transacaoSalva().getCategoria());
    }

    @Test
    void confirmaReaproveitandoCategoriaCriadaDepoisDaPreparacao() {
        Categoria delivery = categoria(3L, "delivery", TipoTransacao.DESPESA, usuario);
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, null, "Delivery", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(categoriaRepository.findByUserId(7L)).thenReturn(List.of(delivery));

        service.confirmar("p1", TELEGRAM_ID);

        verify(categoriaRepository, never()).save(any());
        assertSame(delivery, transacaoSalva().getCategoria());
    }

    @Test
    void confirmacaoExpiradaEDescartada() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 1L, "Mercado", Instant.now().minusSeconds(1));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));

        String resposta = service.confirmar("p1", TELEGRAM_ID);

        assertTrue(resposta.contains("expirou"));
        verify(pendingRepository).delete(pendente);
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void confirmacaoInexistenteOuDeOutroTelegramNaoSalva() {
        when(pendingRepository.findById("nada")).thenReturn(Optional.empty());
        TransacaoPendenteTelegram alheia = pendente("p1", 999L, 1L, "Mercado", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(alheia));

        assertTrue(service.confirmar("nada", TELEGRAM_ID).startsWith("⏳"));
        assertTrue(service.confirmar("p1", TELEGRAM_ID).startsWith("⏳"));

        verify(pendingRepository, never()).delete(any());
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void confirmacaoComCategoriaDeOutroUsuarioEDescartada() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 5L, "Lazer", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(categoriaRepository.findById(5L)).thenReturn(Optional.of(categoria(5L, "Lazer", TipoTransacao.DESPESA, usuario(8L))));

        assertTrue(service.confirmar("p1", TELEGRAM_ID).startsWith("⚠️"));
        verify(pendingRepository).delete(pendente);
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void confirmacaoComCategoriaApagadaEDescartada() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 5L, "Lazer", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(categoriaRepository.findById(5L)).thenReturn(Optional.empty());

        assertTrue(service.confirmar("p1", TELEGRAM_ID).startsWith("⚠️"));
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void confirmacaoSemContaVinculadaEDescartada() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 1L, "Mercado", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));
        when(userRepository.findByTelegramUserId(TELEGRAM_ID)).thenReturn(Optional.empty());

        assertTrue(service.confirmar("p1", TELEGRAM_ID).startsWith("⚠️"));
        verify(pendingRepository).delete(pendente);
        verify(transacaoRepository, never()).save(any());
    }

    // --- cancelar ---

    @Test
    void cancelaPendenteDoProprioTelegram() {
        TransacaoPendenteTelegram pendente = pendente("p1", TELEGRAM_ID, 1L, "Mercado", Instant.now().plusSeconds(60));
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente));

        assertTrue(service.cancelar("p1", TELEGRAM_ID).startsWith("🗑️"));
        verify(pendingRepository).delete(pendente);
        verify(transacaoRepository, never()).save(any());
    }

    @Test
    void naoCancelaPendenteDeOutroTelegramOuInexistente() {
        when(pendingRepository.findById("p1")).thenReturn(Optional.of(pendente("p1", 999L, 1L, "Mercado", Instant.now().plusSeconds(60))));
        when(pendingRepository.findById("nada")).thenReturn(Optional.empty());

        assertTrue(service.cancelar("p1", TELEGRAM_ID).startsWith("⏳"));
        assertTrue(service.cancelar("nada", TELEGRAM_ID).startsWith("⏳"));
        verify(pendingRepository, never()).delete(any());
    }

    private void interpretacao(BigDecimal valor, String descricao, String data, String tipo, String categoria) {
        when(geminiService.interpretar(anyString(), anyList()))
                .thenReturn(new TransacaoInterpretadaDTO(valor, descricao, data, tipo, categoria, null));
    }

    private TransacaoPendenteTelegram pendenteSalva() {
        ArgumentCaptor<TransacaoPendenteTelegram> captor = ArgumentCaptor.forClass(TransacaoPendenteTelegram.class);
        verify(pendingRepository).save(captor.capture());
        return captor.getValue();
    }

    private Transacao transacaoSalva() {
        ArgumentCaptor<Transacao> captor = ArgumentCaptor.forClass(Transacao.class);
        verify(transacaoRepository).save(captor.capture());
        return captor.getValue();
    }

    private static TransacaoPendenteTelegram pendente(String id, Long telegramUserId, Long categoriaId,
                                                      String categoriaNome, Instant expiraEm) {
        TransacaoPendenteTelegram pendente = new TransacaoPendenteTelegram();
        pendente.setId(id);
        pendente.setTelegramUserId(telegramUserId);
        pendente.setValor(new BigDecimal("32.50"));
        pendente.setDescricao("Almoço");
        pendente.setData(LocalDate.of(2026, 10, 5));
        pendente.setCategoriaId(categoriaId);
        pendente.setCategoriaNome(categoriaNome);
        pendente.setTipo(TipoTransacao.DESPESA);
        pendente.setExpiraEm(expiraEm);
        return pendente;
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
