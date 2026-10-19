package hanrry.cdg.services;

import hanrry.cdg.dtos.TelegramLinkResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.models.Categoria;
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
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramLinkServiceTest {

    private static final String BOT_TOKEN = "123456:token-de-teste";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final TransacaoRepository transacaoRepository = mock(TransacaoRepository.class);

    private final User usuario = usuario(7L, "ana@cdg.com");

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
        when(userRepository.findById(7L)).thenReturn(Optional.of(usuario));
        when(userRepository.save(any(User.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void geraCodigoComExpiracaoELinkDoBot() {
        Instant antes = Instant.now();

        TelegramLinkResponseDTO resposta = service("cdg_bot").gerarCodigo();

        assertFalse(resposta.codigo().isBlank());
        assertEquals("https://t.me/cdg_bot?start=" + resposta.codigo(), resposta.link());
        assertTrue(resposta.expiraEm().isAfter(antes.plusSeconds(590)));
        assertTrue(resposta.expiraEm().isBefore(Instant.now().plusSeconds(601)));
        assertEquals(sha256(resposta.codigo()), usuario.getTelegramLinkTokenHash());
        assertEquals(resposta.expiraEm(), usuario.getTelegramLinkExpiresAt());
        verify(userRepository).save(usuario);
    }

    @Test
    void removeArrobaDoNomeDoBotNoLink() {
        TelegramLinkResponseDTO resposta = service("@cdg_bot").gerarCodigo();

        assertEquals("https://t.me/cdg_bot?start=" + resposta.codigo(), resposta.link());
    }

    @Test
    void ignoraEspacosAntesDaArrobaNoNomeDoBot() {
        TelegramLinkResponseDTO resposta = service(" @cdg_bot ").gerarCodigo();

        assertEquals("https://t.me/cdg_bot?start=" + resposta.codigo(), resposta.link());
    }

    @Test
    void semNomeDoBotNaoGeraLink() {
        TelegramLinkResponseDTO resposta = service("").gerarCodigo();

        assertNull(resposta.link());
        assertFalse(resposta.codigo().isBlank());
    }

    @Test
    void geraCodigosDiferentesACadaChamada() {
        TelegramLinkService service = service("cdg_bot");

        assertNotEquals(service.gerarCodigo().codigo(), service.gerarCodigo().codigo());
    }

    @Test
    void recusaGerarCodigoParaContaJaVinculada() {
        usuario.setTelegramUserId(555L);

        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").gerarCodigo());
        verify(userRepository, never()).save(any());
    }

    @Test
    void informaSeContaEstaVinculada() {
        TelegramLinkService service = service("cdg_bot");
        assertFalse(service.estaVinculado());

        usuario.setTelegramUserId(555L);
        assertTrue(service.estaVinculado());
    }

    @Test
    void vinculaComCodigoValidoELimpaToken() {
        TelegramLinkService service = service("cdg_bot");
        String codigo = service.gerarCodigo().codigo();
        when(userRepository.findByTelegramLinkTokenHash(sha256(codigo))).thenReturn(Optional.of(usuario));
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.empty());

        service.vincular(codigo, 555L);

        assertEquals(555L, usuario.getTelegramUserId());
        assertNull(usuario.getTelegramLinkTokenHash());
        assertNull(usuario.getTelegramLinkExpiresAt());
    }

    @Test
    void recusaCodigoDesconhecido() {
        when(userRepository.findByTelegramLinkTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").vincular("codigo-falso", 555L));
        assertNull(usuario.getTelegramUserId());
    }

    @Test
    void recusaCodigoExpirado() {
        usuario.setTelegramLinkTokenHash(sha256("abc"));
        usuario.setTelegramLinkExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findByTelegramLinkTokenHash(sha256("abc"))).thenReturn(Optional.of(usuario));

        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").vincular("abc", 555L));
        assertNull(usuario.getTelegramUserId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void recusaCodigoSemExpiracao() {
        usuario.setTelegramLinkTokenHash(sha256("abc"));
        when(userRepository.findByTelegramLinkTokenHash(sha256("abc"))).thenReturn(Optional.of(usuario));

        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").vincular("abc", 555L));
    }

    @Test
    void vincularMigraDadosDaContaSoDoTelegramEAApaga() {
        prepararCodigoValido("abc");
        User contaTelegram = usuario(20L, null);
        contaTelegram.setTelegramUserId(555L);
        Transacao transacao = new Transacao();
        transacao.setUser(contaTelegram);
        Categoria categoria = new Categoria();
        categoria.setUser(contaTelegram);
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.of(contaTelegram));
        when(transacaoRepository.findByUserId(20L)).thenReturn(List.of(transacao));
        when(categoriaRepository.findByUserId(20L)).thenReturn(List.of(categoria));

        service("cdg_bot").vincular("abc", 555L);

        assertSame(usuario, transacao.getUser());
        assertSame(usuario, categoria.getUser());
        verify(userRepository).delete(contaTelegram);
        assertEquals(555L, usuario.getTelegramUserId());
    }

    @Test
    void vincularDesvinculaOutraContaComEmailSemApagala() {
        prepararCodigoValido("abc");
        User outraConta = usuario(30L, "bia@cdg.com");
        outraConta.setTelegramUserId(555L);
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.of(outraConta));

        service("cdg_bot").vincular("abc", 555L);

        assertNull(outraConta.getTelegramUserId());
        verify(userRepository).saveAndFlush(outraConta);
        verify(userRepository, never()).delete(any());
        assertEquals(555L, usuario.getTelegramUserId());
    }

    @Test
    void criaContaTelegramQuandoNaoExiste() {
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.empty());

        User criada = service("cdg_bot").obterOuCriarContaTelegram(555L, "  Ana  ");
        User semNome = service("cdg_bot").obterOuCriarContaTelegram(555L, " ");

        assertEquals(555L, criada.getTelegramUserId());
        assertEquals("Ana", criada.getNome());
        assertNull(criada.getEmail());
        assertEquals("Usuário do Telegram", semNome.getNome());
    }

    @Test
    void reutilizaContaTelegramExistente() {
        User existente = usuario(20L, null);
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.of(existente));

        assertSame(existente, service("cdg_bot").obterOuCriarContaTelegram(555L, "Ana"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void autenticaMiniAppComAssinaturaValida() throws Exception {
        when(userRepository.findByTelegramUserId(555L)).thenReturn(Optional.of(usuario));
        String initData = initData(Instant.now().getEpochSecond(), BOT_TOKEN);

        assertSame(usuario, service("cdg_bot").autenticarMiniApp(initData));
    }

    @Test
    void recusaMiniAppComAssinaturaDeOutroBot() throws Exception {
        String initData = initData(Instant.now().getEpochSecond(), "999:outro-bot");

        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").autenticarMiniApp(initData));
    }

    @Test
    void recusaMiniAppComSessaoAntiga() throws Exception {
        String initData = initData(Instant.now().getEpochSecond() - 301, BOT_TOKEN);

        BusinessRuleException erro = assertThrows(BusinessRuleException.class,
                () -> service("cdg_bot").autenticarMiniApp(initData));
        assertTrue(erro.getMessage().contains("expirou"));
    }

    @Test
    void recusaMiniAppSemTokenDoBotOuSemDados() {
        TelegramLinkService semToken = new TelegramLinkService(userRepository, categoriaRepository, transacaoRepository,
                new ObjectMapper(), "", "cdg_bot");

        assertThrows(BusinessRuleException.class, () -> semToken.autenticarMiniApp("auth_date=1&hash=00"));
        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").autenticarMiniApp(""));
        assertThrows(BusinessRuleException.class, () -> service("cdg_bot").autenticarMiniApp("lixo"));
    }

    private void prepararCodigoValido(String codigo) {
        usuario.setTelegramLinkTokenHash(sha256(codigo));
        usuario.setTelegramLinkExpiresAt(Instant.now().plusSeconds(300));
        when(userRepository.findByTelegramLinkTokenHash(sha256(codigo))).thenReturn(Optional.of(usuario));
    }

    private TelegramLinkService service(String botUsername) {
        return new TelegramLinkService(userRepository, categoriaRepository, transacaoRepository,
                new ObjectMapper(), BOT_TOKEN, botUsername);
    }

    private static String initData(long authDate, String botToken) throws Exception {
        Map<String, String> campos = new TreeMap<>();
        campos.put("auth_date", String.valueOf(authDate));
        campos.put("query_id", "AAH");
        campos.put("user", "{\"id\":555,\"first_name\":\"Ana\",\"last_name\":\"Silva\"}");
        String dataCheck = campos.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] chave = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(dataCheck.getBytes(StandardCharsets.UTF_8)));
        campos.put("hash", hash);
        return campos.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static User usuario(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }
}
