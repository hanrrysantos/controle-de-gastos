package hanrry.cdg.services;

import hanrry.cdg.dtos.DashboardResponseDTO;
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
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;
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
        if (dto.getValor() == null || dto.getValor().signum() <= 0 || dto.getValor().stripTrailingZeros().scale() > 2
                || dto.getValor().precision() - dto.getValor().scale() > 17) {
            throw new BusinessRuleException("O valor deve ser positivo, ter no máximo duas casas decimais e caber no limite monetário.");
        }
        if (dto.getCategoriaId() == null || dto.getData() == null || dto.getDescricao() == null || dto.getDescricao().isBlank()) {
            throw new BusinessRuleException("Preencha descrição, data e categoria.");
        }
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com ID: " + dto.getCategoriaId()));

        if (!categoria.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Você não tem permissão para utilizar ou visualizar este recurso.");
        }

        Transacao transacao = mapper.toEntity(dto);
        transacao.setUser(user);
        transacao.setCategoria(categoria);
        transacao.setTipo(categoria.getTipo());

        Transacao salva = repository.save(transacao);
        return mapper.toDto(salva);
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
