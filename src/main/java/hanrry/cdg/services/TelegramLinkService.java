package hanrry.cdg.services;

import hanrry.cdg.dtos.TelegramLinkResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.HexFormat;

@Service
public class TelegramLinkService {

    private final UserRepository userRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ObjectMapper objectMapper;
    private final String botToken;
    private final String botUsername;
    private final SecureRandom random = new SecureRandom();

    public TelegramLinkService(UserRepository userRepository, CategoriaRepository categoriaRepository,
                               TransacaoRepository transacaoRepository, ObjectMapper objectMapper,
                               @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
                               @Value("${TELEGRAM_BOT_USERNAME:}") String botUsername) {
        this.userRepository = userRepository;
        this.categoriaRepository = categoriaRepository;
        this.transacaoRepository = transacaoRepository;
        this.objectMapper = objectMapper;
        this.botToken = botToken;
        this.botUsername = botUsername.trim().replaceFirst("^@", "");
    }

    @Transactional
    public User obterOuCriarContaTelegram(Long telegramUserId, String nome) {
        return userRepository.findByTelegramUserId(telegramUserId).orElseGet(() -> {
            User user = new User();
            user.setTelegramUserId(telegramUserId);
            user.setNome(nome == null || nome.isBlank() ? "Usuário do Telegram" : nome.trim());
            return userRepository.save(user);
        });
    }

    @Transactional
    public User autenticarMiniApp(String initData) {
        if (botToken.isBlank() || initData == null || initData.isBlank() || initData.length() > 4096) {
            throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
        }
        try {
            Map<String, String> fields = new TreeMap<>();
            for (String field : initData.split("&")) {
                String[] pair = field.split("=", 2);
                if (pair.length != 2 || fields.putIfAbsent(
                        URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null) {
                    throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
                }
            }
            String receivedHash = fields.remove("hash");
            String authDateValue = fields.get("auth_date");
            String userJson = fields.get("user");
            if (receivedHash == null || authDateValue == null || userJson == null) {
                throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
            }

            String dataCheckString = fields.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = mac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] calculatedHash = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(calculatedHash, HexFormat.of().parseHex(receivedHash))) {
                throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
            }

            long authDate = Long.parseLong(authDateValue);
            long now = Instant.now().getEpochSecond();
            if (authDate > now + 60 || now - authDate > 300) {
                throw new BusinessRuleException("Sua sessão do Telegram expirou. Abra o dashboard novamente pelo bot.");
            }
            var telegramUser = objectMapper.readTree(userJson);
            long telegramUserId = telegramUser.path("id").asLong(0);
            if (telegramUserId <= 0) {
                throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
            }
            String nome = (telegramUser.path("first_name").asText("") + " " +
                    telegramUser.path("last_name").asText("")).trim();
            return obterOuCriarContaTelegram(telegramUserId, nome);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível validar sua sessão do Telegram.");
        }
    }

    @Transactional
    public boolean estaVinculado() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return userRepository.findById(userId).orElseThrow().getTelegramUserId() != null;
    }

    @Transactional
    public TelegramLinkResponseDTO gerarCodigo() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getTelegramUserId() != null) {
            throw new BusinessRuleException("Esta conta já está vinculada ao Telegram.");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiraEm = Instant.now().plusSeconds(600);

        user.setTelegramLinkTokenHash(hash(codigo));
        user.setTelegramLinkExpiresAt(expiraEm);
        userRepository.save(user);
        String link = botUsername.isEmpty() ? null : "https://t.me/" + botUsername + "?start=" + codigo;
        return new TelegramLinkResponseDTO(codigo, expiraEm, link);
    }

    @Transactional
    public void vincular(String codigo, Long telegramUserId) {
        User user = userRepository.findByTelegramLinkTokenHash(hash(codigo))
                .orElseThrow(() -> new BusinessRuleException("Código inválido ou expirado. Gere outro código no CDG."));
        if (user.getTelegramLinkExpiresAt() == null || !user.getTelegramLinkExpiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Código inválido ou expirado. Gere outro código no CDG.");
        }
        User anterior = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (anterior != null) {
            if (anterior.getEmail() == null) {
                transacaoRepository.findByUserId(anterior.getId()).forEach(transacao -> transacao.setUser(user));
                categoriaRepository.findByUserId(anterior.getId()).forEach(categoria -> categoria.setUser(user));
                transacaoRepository.flush();
                categoriaRepository.flush();
                userRepository.delete(anterior);
                userRepository.flush();
            } else {
                anterior.setTelegramUserId(null);
                userRepository.saveAndFlush(anterior);
            }
        }

        user.setTelegramUserId(telegramUserId);
        user.setTelegramLinkTokenHash(null);
        user.setTelegramLinkExpiresAt(null);
        userRepository.save(user);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
