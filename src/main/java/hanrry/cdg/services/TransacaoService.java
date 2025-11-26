package hanrry.cdg.services;

import hanrry.cdg.dtos.DashboardResponseDTO;
import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.exceptions.AccessDeniedException;
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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        return user.getTransacoes().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public DashboardResponseDTO getDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        List<Transacao> transacoes = user.getTransacoes();

        Double totalReceitas = transacoes.stream()
                .filter(t -> t.getTipo() == TipoTransacao.RECEITA)
                .mapToDouble(Transacao::getValor)
                .sum();

        Double totalDespesas = transacoes.stream()
                .filter(t -> t.getTipo() == TipoTransacao.DESPESA)
                .mapToDouble(Transacao::getValor)
                .sum();

        Double saldo = totalReceitas - totalDespesas;

        return new DashboardResponseDTO(totalReceitas, totalDespesas, saldo);
    }

    public void deletar(Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        Transacao transacao = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        if (!transacao.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para excluir isso.");
        }

        repository.delete(transacao);
    }
}