package hanrry.cdg.services;

import hanrry.cdg.dtos.DashboardResponseDTO;
import hanrry.cdg.dtos.ResumoMensalResponseDTO;
import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.exceptions.AccessDeniedException;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.TransacaoMapper;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.TipoTransacao;
import hanrry.cdg.models.Transacao;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.TotalPorCategoria;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
@Service
public class TransacaoService {

    private final TransacaoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final UserRepository userRepository;
    private final TransacaoMapper mapper;

    public TransacaoService(TransacaoRepository repository, CategoriaRepository categoriaRepository, UserRepository userRepository, TransacaoMapper mapper) {
        this.repository = repository;
        this.categoriaRepository = categoriaRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public TransacaoResponseDTO criar(TransacaoRequestDTO dto) {
        validar(dto);
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        Categoria categoria = buscarCategoria(dto.getCategoriaId(), user);

        Transacao transacao = mapper.toEntity(dto);
        transacao.setUser(user);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());

        Transacao salva = repository.save(transacao);
        return mapper.toDto(salva);
    }

    public TransacaoResponseDTO atualizar(Long id, TransacaoRequestDTO dto) {
        validar(dto);
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        Transacao transacao = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada."));

        if (!transacao.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para editar este lançamento.");
        }

        Categoria categoria = buscarCategoria(dto.getCategoriaId(), user);
        mapper.atualizar(dto, transacao);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());

        Transacao salva = repository.save(transacao);
        return mapper.toDto(salva);
    }

    private static void validar(TransacaoRequestDTO dto) {
        if (dto.getValor() == null || dto.getValor().signum() <= 0 || dto.getValor().stripTrailingZeros().scale() > 2
                || dto.getValor().precision() - dto.getValor().scale() > 17) {
            throw new BusinessRuleException("O valor deve ser positivo, ter no máximo duas casas decimais e caber no limite monetário.");
        }
        if (dto.getCategoriaId() == null || dto.getData() == null || dto.getDescricao() == null || dto.getDescricao().isBlank()) {
            throw new BusinessRuleException("Preencha descrição, data e categoria.");
        }
    }

    private Categoria buscarCategoria(Long categoriaId, User user) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com ID: " + categoriaId));

        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para utilizar ou visualizar este recurso.");
        }
        return categoria;
    }

    public List<TransacaoResponseDTO> listar() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        return user.getTransacoes().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public DashboardResponseDTO getDashboard() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        List<Transacao> transacoes = user.getTransacoes();

        BigDecimal totalReceitas = transacoes.stream()
                .filter(t -> t.getTipo() == TipoTransacao.RECEITA)
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDespesas = transacoes.stream()
                .filter(t -> t.getTipo() == TipoTransacao.DESPESA)
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldo = totalReceitas.subtract(totalDespesas);

        return new DashboardResponseDTO(totalReceitas, totalDespesas, saldo);
    }

    public ResumoMensalResponseDTO getResumoMensal(String mes) {
        YearMonth periodo;
        try {
            periodo = mes == null || mes.isBlank() ? YearMonth.now() : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Informe o mês no formato AAAA-MM.");
        }
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        LocalDate inicio = periodo.atDay(1);
        LocalDate fim = periodo.atEndOfMonth();
        YearMonth anterior = periodo.minusMonths(1);

        BigDecimal receitas = centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA, inicio, fim));
        BigDecimal despesas = centavos(repository.totalPorTipo(userId, TipoTransacao.DESPESA, inicio, fim));
        BigDecimal saldoAcumulado = centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA)
                .subtract(repository.totalPorTipo(userId, TipoTransacao.DESPESA)));

        Map<Long, TotalPorCategoria> totais = repository.totalPorCategoria(userId, inicio, fim).stream()
                .collect(Collectors.toMap(TotalPorCategoria::categoriaId, Function.identity()));
        List<ResumoMensalResponseDTO.Categoria> categorias = categoriaRepository.findByUserId(userId).stream()
                .map(categoria -> {
                    TotalPorCategoria total = totais.get(categoria.getId());
                    return new ResumoMensalResponseDTO.Categoria(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                            centavos(total == null ? BigDecimal.ZERO : total.total()), total == null ? 0 : total.quantidade(),
                            categoria.getMetaMensal());
                })
                .sorted(Comparator.comparing(ResumoMensalResponseDTO.Categoria::total).reversed()
                        .thenComparing(ResumoMensalResponseDTO.Categoria::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ResumoMensalResponseDTO(
                periodo.toString(),
                receitas,
                despesas,
                receitas.subtract(despesas),
                centavos(repository.totalPorTipo(userId, TipoTransacao.RECEITA, anterior.atDay(1), anterior.atEndOfMonth())),
                centavos(repository.totalPorTipo(userId, TipoTransacao.DESPESA, anterior.atDay(1), anterior.atEndOfMonth())),
                saldoAcumulado,
                totais.values().stream().mapToLong(TotalPorCategoria::quantidade).sum(),
                categorias);
    }

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    public void deletar(Long id) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        Transacao transacao = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada."));

        if (!transacao.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para excluir este lançamento.");
        }

        repository.delete(transacao);
    }
}
