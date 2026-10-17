package hanrry.cdg.services;

import hanrry.cdg.dtos.TransacaoInterpretadaDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.TransacaoPendenteTelegram;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TransacaoPendenteTelegramRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TelegramTransactionService {
    private final UserRepository userRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoPendenteTelegramRepository pendingRepository;
    private final GeminiService geminiService;

    public TelegramTransactionService(UserRepository userRepository, CategoriaRepository categoriaRepository,
                                     TransacaoRepository transacaoRepository,
                                     TransacaoPendenteTelegramRepository pendingRepository,
                                     GeminiService geminiService) {
        this.userRepository = userRepository;
        this.categoriaRepository = categoriaRepository;
        this.transacaoRepository = transacaoRepository;
        this.pendingRepository = pendingRepository;
        this.geminiService = geminiService;
    }

    @Transactional
    public Resposta preparar(Long telegramUserId, String texto) {
        pendingRepository.deleteAllByExpiraEmBefore(Instant.now());
        User user = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (user == null) return new Resposta("Envie /start para criar sua conta e começar.", null);

        List<Categoria> categorias = categoriaRepository.findByUserId(user.getId());

        TransacaoInterpretadaDTO interpretada = geminiService.interpretar(texto, categorias);
        if (interpretada.valor() == null || interpretada.valor().signum() <= 0 ||
                interpretada.valor().stripTrailingZeros().scale() > 2 ||
                interpretada.valor().precision() - interpretada.valor().scale() > 17 ||
                interpretada.descricao() == null || interpretada.descricao().isBlank() || interpretada.descricao().length() > 255) {
            return new Resposta("Não identifiquei um valor válido. Envie algo como: gastei 32,50 reais no almoço.", null);
        }

        TipoTransacao tipo;
        LocalDate data;
        try {
            tipo = interpretada.tipo() == null || interpretada.tipo().isBlank()
                    ? TipoTransacao.DESPESA : TipoTransacao.valueOf(interpretada.tipo());
            data = interpretada.data() == null || interpretada.data().isBlank()
                    ? LocalDate.now(ZoneId.of("America/Sao_Paulo")) : LocalDate.parse(interpretada.data());
        } catch (RuntimeException e) {
            return new Resposta("Não consegui identificar os detalhes da transação. Tente reformular a mensagem.", null);
        }

        String categoriaNome = interpretada.categoria() == null ? "" : interpretada.categoria().trim();
        if (categoriaNome.length() > 255) {
            return new Resposta("O nome da categoria ficou muito longo. Tente uma categoria mais curta.", null);
        }
        String categoriaInformada = categoriaNome;
        Categoria categoria = categorias.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(categoriaInformada))
                .filter(c -> c.getTipo() == tipo)
                .findFirst().orElse(null);
        boolean criarCategoria = false;
        if (categoria == null && !categoriaNome.isBlank() && categoriaNome.length() <= 255) {
            criarCategoria = true;
        } else if (categoria == null && categoriaNome.isBlank()) {
            categoria = categorias.stream()
                    .filter(c -> c.getNome().equalsIgnoreCase("Outros"))
                    .filter(c -> c.getTipo() == tipo)
                    .findFirst().orElse(null);
            if (categoria == null) {
                categoriaNome = "Outros";
                criarCategoria = true;
            } else {
                categoriaNome = categoria.getNome();
            }
        }

        TransacaoPendenteTelegram pendente = new TransacaoPendenteTelegram();
        pendente.setId(UUID.randomUUID().toString());
        pendente.setTelegramUserId(telegramUserId);
        pendente.setValor(interpretada.valor().setScale(2));
        pendente.setDescricao(interpretada.descricao().trim());
        pendente.setData(data);
        pendente.setCategoriaId(categoria == null ? null : categoria.getId());
        pendente.setCategoriaNome(categoriaNome);
        pendente.setTipo(tipo);
        pendente.setExpiraEm(Instant.now().plusSeconds(600));
        pendingRepository.save(pendente);

        String resumo = "Confirma esta transação?\n" + String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", pendente.getValor()) +
                " — " + pendente.getDescricao() + "\nCategoria: " + categoriaNome +
                (criarCategoria ? " (será criada)" : "") + "\nTipo: " + tipo + "\nData: " + data;
        return new Resposta(resumo, "confirmar:" + pendente.getId());
    }

    @Transactional
    public String confirmar(String id, Long telegramUserId) {
        TransacaoPendenteTelegram pendente = pendingRepository.findById(id).orElse(null);
        if (pendente == null || !pendente.getTelegramUserId().equals(telegramUserId)) {
            return "Esta confirmação expirou ou já foi processada. Envie a transação novamente se necessário.";
        }
        if (!pendente.getExpiraEm().isAfter(Instant.now())) {
            pendingRepository.delete(pendente);
            return "Esta confirmação expirou. Envie a transação novamente.";
        }

        User user = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (user == null) {
            pendingRepository.delete(pendente);
            return "Não foi possível validar sua conta. Tente enviar a transação novamente.";
        }

        Categoria categoria;
        if (pendente.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(pendente.getCategoriaId()).orElse(null);
            if (categoria == null || categoria.getUser() == null || !categoria.getUser().getId().equals(user.getId())) {
                pendingRepository.delete(pendente);
                return "Não foi possível validar a categoria. Tente enviar a transação novamente.";
            }
        } else {
            categoria = categoriaRepository.findByUserId(user.getId()).stream()
                    .filter(c -> c.getTipo() == pendente.getTipo())
                    .filter(c -> c.getNome().equalsIgnoreCase(pendente.getCategoriaNome()))
                    .findFirst().orElse(null);
            if (categoria == null) {
                categoria = new Categoria();
                categoria.setNome(pendente.getCategoriaNome());
                categoria.setTipo(pendente.getTipo());
                categoria.setUser(user);
                categoria = categoriaRepository.save(categoria);
            }
        }

        Transacao transacao = new Transacao();
        transacao.setValor(pendente.getValor());
        transacao.setDescricao(pendente.getDescricao());
        transacao.setData(pendente.getData());
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());
        transacao.setUser(user);
        transacaoRepository.save(transacao);
        pendingRepository.delete(pendente);
        return "Transação registrada no CDG!";
    }

    @Transactional
    public String cancelar(String id, Long telegramUserId) {
        TransacaoPendenteTelegram pendente = pendingRepository.findById(id).orElse(null);
        if (pendente == null || !pendente.getTelegramUserId().equals(telegramUserId)) {
            return "Esta confirmação expirou ou já foi processada.";
        }
        pendingRepository.delete(pendente);
        return "Tudo bem, não registrei a transação.";
    }

    public record Resposta(String texto, String callbackConfirmacao) { }
}
