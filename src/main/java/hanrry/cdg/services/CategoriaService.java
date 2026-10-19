package hanrry.cdg.services;

import hanrry.cdg.dtos.CategoriaCreateDTO;
import hanrry.cdg.dtos.CategoriaResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.CategoriaMapper;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.QuantidadePorCategoria;
import hanrry.cdg.repositories.TransacaoPendenteTelegramRepository;
import hanrry.cdg.repositories.TransacaoRepository;
import hanrry.cdg.repositories.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final UserRepository userRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoPendenteTelegramRepository pendenteRepository;
    private final CategoriaMapper mapper;

    public CategoriaService(CategoriaRepository repository, UserRepository userRepository, TransacaoRepository transacaoRepository,
                            TransacaoPendenteTelegramRepository pendenteRepository, CategoriaMapper mapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.transacaoRepository = transacaoRepository;
        this.pendenteRepository = pendenteRepository;
        this.mapper = mapper;
    }

    public CategoriaResponseDTO criar(CategoriaCreateDTO dto) {
        validar(dto);
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado no sistema."));

        Categoria categoria = mapper.toEntity(dto);
        categoria.setUser(user);

        Categoria salva = repository.save(categoria);
        return mapper.toDto(salva, 0);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar() {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        User user = userRepository.findById(userId).orElseThrow();

        Map<Long, Long> quantidades = transacaoRepository.quantidadePorCategoria(userId).stream()
                .collect(Collectors.toMap(QuantidadePorCategoria::categoriaId, QuantidadePorCategoria::quantidade));
        return user.getCategorias().stream()
                .map(categoria -> mapper.toDto(categoria, quantidades.getOrDefault(categoria.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoriaResponseDTO atualizar(Long id, CategoriaCreateDTO dto) {
        validar(dto);
        Categoria categoria = buscarDoUsuario(id);
        long quantidade = transacaoRepository.countByCategoriaId(id);

        if (dto.getTipo() != categoria.getTipo() && quantidade > 0) {
            throw new BusinessRuleException("Não é possível mudar o tipo de uma categoria que já tem lançamentos.");
        }

        categoria.setNome(dto.getNome());
        categoria.setTipo(dto.getTipo());
        Categoria salva = repository.save(categoria);
        return mapper.toDto(salva, quantidade);
    }

    @Transactional
    public void deletar(Long id, Long moverPara) {
        Categoria categoria = buscarDoUsuario(id);
        long quantidade = transacaoRepository.countByCategoriaId(id);

        if (quantidade == 0) {
            pendenteRepository.deletarPorCategoria(id);
            repository.delete(categoria);
            return;
        }
        if (moverPara == null) {
            throw new BusinessRuleException("Esta categoria tem " + quantidade + (quantidade == 1 ? " lançamento" : " lançamentos")
                    + ". Escolha para qual categoria movê-los.");
        }
        if (moverPara.equals(id)) {
            throw new BusinessRuleException("Escolha uma categoria diferente da que será excluída.");
        }
        Categoria destino = repository.findByIdAndUserId(moverPara, categoria.getUser().getId())
                .orElseThrow(() -> new BusinessRuleException("A categoria de destino não foi encontrada."));
        if (destino.getTipo() != categoria.getTipo()) {
            throw new BusinessRuleException("A categoria de destino deve ser do mesmo tipo (receita ou despesa).");
        }

        pendenteRepository.moverCategoria(id, destino.getId(), destino.getNome());
        transacaoRepository.moverCategoria(id, destino);
        repository.delete(categoria);
    }

    private static void validar(CategoriaCreateDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank() || dto.getTipo() == null) {
            throw new BusinessRuleException("Preencha nome e tipo da categoria.");
        }
    }

    private Categoria buscarDoUsuario(Long id) {
        Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada com ID: " + id));
    }
}
