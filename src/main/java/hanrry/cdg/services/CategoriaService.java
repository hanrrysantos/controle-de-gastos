package hanrry.cdg.services;

import hanrry.cdg.dtos.CategoriaCreateDTO;
import hanrry.cdg.dtos.CategoriaResponseDTO;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.CategoriaMapper;
import hanrry.cdg.models.Categoria;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.CategoriaRepository;
import hanrry.cdg.repositories.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final UserRepository userRepository;
    private final CategoriaMapper mapper;

    public CategoriaService(CategoriaRepository repository, UserRepository userRepository, CategoriaMapper mapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public CategoriaResponseDTO criar(CategoriaCreateDTO dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado no sistema."));

        Categoria categoria = mapper.toEntity(dto);
        categoria.setUser(user);

        Categoria salva = repository.save(categoria);
        return mapper.toDto(salva);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        return user.getCategorias().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
}