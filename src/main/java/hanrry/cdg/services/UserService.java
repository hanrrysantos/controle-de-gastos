package hanrry.cdg.services;

import hanrry.cdg.dtos.UserRegisterDTO;
import hanrry.cdg.dtos.UserResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.exceptions.ResourceNotFoundException;
import hanrry.cdg.mappers.UserMapper;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, UserMapper mapper, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDTO cadastrar(UserRegisterDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank() || dto.getEmail() == null || dto.getEmail().isBlank()
                || dto.getSenha() == null || dto.getSenha().isBlank()) {
            throw new BusinessRuleException("Preencha nome, e-mail e senha.");
        }
        dto.setNome(dto.getNome().trim());
        dto.setEmail(normalizarEmail(dto.getEmail()));
        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new BusinessRuleException("Este e-mail já está em uso.");
        }

        User user = mapper.toEntity(dto);

        String senhaCriptografada = passwordEncoder.encode(dto.getSenha());
        user.setSenha(senhaCriptografada);

        User salvo = repository.save(user);

        return mapper.toDto(salvo);
    }

    public UserResponseDTO buscarPorEmail(String email) {
        return repository.findByEmail(normalizarEmail(email))
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    // "Ana@CDG.com " e "ana@cdg.com" são o mesmo e-mail: cadastro e login sempre comparam a forma normalizada.
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
