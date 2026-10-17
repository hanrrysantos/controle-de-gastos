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
import java.time.format.DateTimeFormatter;
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
        if (user == null) return new Resposta("👋 Para começar, envie /start e eu preparo sua conta.", null);

        List<Categoria> categorias = categoriaRepository.findByUserId(user.getId());

        TransacaoInterpretadaDTO interpretada = geminiService.interpretar(texto, categorias);
        if (interpretada.valor() == null || interpretada.valor().signum() <= 0 ||
                interpretada.valor().stripTrailingZeros().scale() > 2 ||
                interpretada.valor().precision() - interpretada.valor().scale() > 17 ||
                interpretada.descricao() == null || interpretada.descricao().isBlank() || interpretada.descricao().length() > 255) {
            return new Resposta("🤔 Não encontrei um valor válido. Tente assim: “Gastei R$ 32,50 no almoço”.", null);
        }

        TipoTransacao tipo;
        LocalDate data;
        try {
            tipo = interpretada.tipo() == null || interpretada.tipo().isBlank()
                    ? TipoTransacao.DESPESA : TipoTransacao.valueOf(interpretada.tipo());
            data = interpretada.data() == null || interpretada.data().isBlank()
                    ? LocalDate.now(ZoneId.of("America/Sao_Paulo")) : LocalDate.parse(interpretada.data());
        } catch (RuntimeException e) {
            return new Resposta("🤔 Não consegui entender os detalhes. Pode escrever de outro jeito?", null);
        }

        String categoriaNome = interpretada.categoria() == null ? "" : interpretada.categoria().trim();
        if (categoriaNome.length() > 255) {
            return new Resposta("🏷️ O nome da categoria ficou longo. Tente usar um nome mais curto.", null);
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

        String resumo = "🧾 Confere esse lançamento?\n\n" +
                "💰 Valor: " + String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", pendente.getValor()) +
                "\n📝 Descrição: " + pendente.getDescricao() +
                "\n🏷️ Categoria: " + categoriaNome + (criarCategoria ? " ✨ (nova)" : "") +
                "\n" + (tipo == TipoTransacao.RECEITA ? "📈 Tipo: Receita" : "📉 Tipo: Despesa") +
                "\n📅 Data: " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                "\n\nConfirma para salvar ou cancela para descartar 👇";
        return new Resposta(resumo, "confirmar:" + pendente.getId());
    }

    @Transactional
    public String confirmar(String id, Long telegramUserId) {
        TransacaoPendenteTelegram pendente = pendingRepository.findById(id).orElse(null);
        if (pendente == null || !pendente.getTelegramUserId().equals(telegramUserId)) {
            return "⏳ Esta confirmação expirou ou já foi usada. Envie a transação novamente.";
        }
        if (!pendente.getExpiraEm().isAfter(Instant.now())) {
            pendingRepository.delete(pendente);
            return "⏳ Esta confirmação expirou. Envie a transação novamente.";
        }

        User user = userRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (user == null) {
            pendingRepository.delete(pendente);
            return "⚠️ Não consegui validar sua conta. Envie a transação novamente.";
        }

        Categoria categoria;
        if (pendente.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(pendente.getCategoriaId()).orElse(null);
            if (categoria == null || categoria.getUser() == null || !categoria.getUser().getId().equals(user.getId())) {
                pendingRepository.delete(pendente);
                return "⚠️ Não consegui validar a categoria. Envie a transação novamente.";
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
        return "✅ Lançamento salvo no CDG!\nEle já está no seu dashboard 📊";
    }

    @Transactional
    public String cancelar(String id, Long telegramUserId) {
        TransacaoPendenteTelegram pendente = pendingRepository.findById(id).orElse(null);
        if (pendente == null || !pendente.getTelegramUserId().equals(telegramUserId)) {
            return "⏳ Esta confirmação expirou ou já foi usada.";
        }
        pendingRepository.delete(pendente);
        return "🗑️ Lançamento cancelado. Nada foi salvo.";
    }

    public record Resposta(String texto, String callbackConfirmacao) { }
}
