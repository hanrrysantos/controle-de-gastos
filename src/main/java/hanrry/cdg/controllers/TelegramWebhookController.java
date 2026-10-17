package hanrry.cdg.controllers;

import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.services.TelegramLinkService;
import hanrry.cdg.services.TelegramTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/integracoes/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final TelegramLinkService linkService;
    private final TelegramTransactionService transactionService;
    private final String botToken;
    private final String webhookSecret;
    private final String webBaseUrl;
    private final RestClient telegram = RestClient.create();

    public TelegramWebhookController(
            TelegramLinkService linkService,
            TelegramTransactionService transactionService,
            @Value("${TELEGRAM_BOT_TOKEN:}") String botToken,
            @Value("${TELEGRAM_WEBHOOK_SECRET:}") String webhookSecret,
            @Value("${api.web.base-url:http://localhost:5173}") String webBaseUrl) {
        this.linkService = linkService;
        this.transactionService = transactionService;
        this.botToken = botToken;
        this.webhookSecret = webhookSecret;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {
        if (webhookSecret.isBlank() || secret == null || !MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        JsonNode callback = update.path("callback_query");
        if (!callback.isMissingNode()) {
            tratarCallback(callback);
            return ResponseEntity.ok().build();
        }

        JsonNode message = update.path("message");
        long chatId = message.path("chat").path("id").asLong(0);
        long telegramUserId = message.path("from").path("id").asLong(0);
        String text = message.path("text").asText("").trim();
        if (chatId == 0 || telegramUserId == 0 || !"private".equals(message.path("chat").path("type").asText())) {
            return ResponseEntity.ok().build();
        }
        if (text.isBlank()) {
            responder(chatId, "Por enquanto, envie a transação por mensagem de texto.", null);
            return ResponseEntity.ok().build();
        }
        if (text.length() > 1000) {
            responder(chatId, "A mensagem ficou muito longa. Envie uma descrição mais curta da transação.", null);
            return ResponseEntity.ok().build();
        }

        String[] parts = text.split("\\s+", 3);
        String command = parts[0].split("@", 2)[0];
        if (command.equals("/start")) {
            if (parts.length == 1) {
                String nome = (message.path("from").path("first_name").asText("") + " " +
                        message.path("from").path("last_name").asText("")).trim();
                linkService.obterOuCriarContaTelegram(telegramUserId, nome);
                enviarAcessoDashboard(chatId,
                        "Conta pronta! Envie suas transações por aqui. Categorias novas são criadas após a confirmação. Toque em Abrir dashboard e fixe esta mensagem para voltar ao painel.");
            } else if (parts.length == 2) {
                try {
                    linkService.vincular(parts[1], telegramUserId);
                    enviarAcessoDashboard(chatId, "Telegram vinculado à sua conta CDG!");
                } catch (BusinessRuleException e) {
                    responder(chatId, e.getMessage(), null);
                }
            } else {
                responder(chatId, "Para começar, envie /start. Para usar um código de vínculo antigo, envie /start <código>.", null);
            }
        } else if (command.equals("/dashboard") || command.equals("/painel")) {
            String nome = (message.path("from").path("first_name").asText("") + " " +
                    message.path("from").path("last_name").asText("")).trim();
            linkService.obterOuCriarContaTelegram(telegramUserId, nome);
            enviarAcessoDashboard(chatId, "Abra seu dashboard pelo botão abaixo.");
        } else {
            try {
                TelegramTransactionService.Resposta resposta = transactionService.preparar(telegramUserId, text);
                responder(chatId, resposta.texto(), resposta.callbackConfirmacao());
            } catch (BusinessRuleException e) {
                log.warn("Não foi possível preparar a transação pelo Telegram: {}", e.getMessage());
                responder(chatId, e.getMessage(), null);
            } catch (RuntimeException e) {
                if (e instanceof RestClientResponseException responseException) {
                    log.warn("Falha na chamada ao Gemini: HTTP {}.", responseException.getStatusCode().value());
                } else {
                    log.warn("Falha ao interpretar mensagem do Telegram: {}.", e.getClass().getSimpleName());
                }
                responder(chatId, "Não consegui processar agora. Tente novamente em instantes.", null);
            }
        }
        return ResponseEntity.ok().build();
    }

    private void enviarAcessoDashboard(long chatId, String mensagem) {
        responder(chatId, mensagem, null, webBaseUrl + "/dashboard");
    }

    private void tratarCallback(JsonNode callback) {
        long chatId = callback.path("message").path("chat").path("id").asLong(0);
        long telegramUserId = callback.path("from").path("id").asLong(0);
        String callbackId = callback.path("id").asText("");
        String[] data = callback.path("data").asText("").split(":", 2);
        if (chatId == 0 || telegramUserId == 0 || data.length != 2) return;

        String reply;
        if (data[0].equals("confirmar")) {
            reply = transactionService.confirmar(data[1], telegramUserId);
        } else if (data[0].equals("cancelar")) {
            reply = transactionService.cancelar(data[1], telegramUserId);
        } else {
            return;
        }
        responderCallback(callbackId);
        responder(chatId, reply, null);
    }

    private void responder(long chatId, String text, String callbackData) {
        responder(chatId, text, callbackData, null);
    }

    private void responder(long chatId, String text, String callbackData, String webAppUrl) {
        if (botToken.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN não configurado; resposta do bot ignorada.");
            return;
        }
        try {
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("chat_id", chatId);
            message.put("text", text);
            java.util.List<java.util.List<Map<String, Object>>> rows = new java.util.ArrayList<>();
            if (callbackData != null) {
                String id = callbackData.substring(callbackData.indexOf(':') + 1);
                rows.add(java.util.List.of(
                        Map.of("text", "Confirmar", "callback_data", callbackData),
                        Map.of("text", "Cancelar", "callback_data", "cancelar:" + id)));
            }
            if (webAppUrl != null) {
                rows.add(java.util.List.of(Map.of("text", "Abrir dashboard", "web_app", Map.of("url", webAppUrl))));
            }
            if (!rows.isEmpty()) message.put("reply_markup", Map.of("inline_keyboard", rows));
            telegram.post()
                    .uri("https://api.telegram.org/bot" + botToken + "/sendMessage")
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("Não foi possível enviar a resposta ao Telegram.");
        }
    }

    private void responderCallback(String callbackId) {
        if (botToken.isBlank() || callbackId.isBlank()) return;
        try {
            telegram.post()
                    .uri("https://api.telegram.org/bot" + botToken + "/answerCallbackQuery")
                    .body(Map.of("callback_query_id", callbackId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.debug("Não foi possível confirmar o callback do Telegram.");
        }
    }
}
