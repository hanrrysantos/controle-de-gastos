package hanrry.cdg.services;

import hanrry.cdg.dtos.UserRegisterDTO;
import hanrry.cdg.dtos.UserResponseDTO;
import hanrry.cdg.exceptions.BusinessRuleException;
import hanrry.cdg.mappers.UserMapper;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
        if (repository.findByEmail(dto.getEmail()).isPresent()) {
            throw new BusinessRuleException("Este e-mail já está em uso.");
        }

        User user = mapper.toEntity(dto);

        String senhaCriptografada = passwordEncoder.encode(dto.getSenha());
        user.setSenha(senhaCriptografada);

        User salvo = repository.save(user);

        return mapper.toDto(salvo);
    }
}