package hanrry.cdg.controllers;

import hanrry.cdg.dtos.LoginResponseDTO;
import hanrry.cdg.dtos.TelegramWebAppLoginDTO;
import hanrry.cdg.dtos.UserLoginDTO;
import hanrry.cdg.dtos.UserRegisterDTO;
import hanrry.cdg.dtos.UserResponseDTO;
import hanrry.cdg.models.User;
import hanrry.cdg.repositories.UserRepository;
import hanrry.cdg.services.TelegramLinkService;
import hanrry.cdg.services.TokenService;
import hanrry.cdg.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager; // <--- Injetamos o gerente
    private final TelegramLinkService telegramLinkService;
    private final UserRepository userRepository;

    public AuthController(UserService userService, TokenService tokenService, AuthenticationManager authenticationManager,
                          TelegramLinkService telegramLinkService, UserRepository userRepository) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
        this.telegramLinkService = telegramLinkService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> cadastrar(@RequestBody UserRegisterDTO dto, UriComponentsBuilder uriBuilder) {
        UserResponseDTO resposta = userService.cadastrar(dto);
        URI uri = uriBuilder.path("/users/{id}").buildAndExpand(resposta.getId()).toUri();
        return ResponseEntity.created(uri).body(resposta);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody UserLoginDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getSenha());
        Authentication auth = authenticationManager.authenticate(usernamePassword);

        var user = (org.springframework.security.core.userdetails.User) auth.getPrincipal();

        User usuario = userRepository.findByEmail(user.getUsername()).orElseThrow();
        var token = tokenService.generateToken(usuario);

        return ResponseEntity.ok(new LoginResponseDTO(token, usuario.getNome()));
    }

    @PostMapping("/telegram/webapp")
    public ResponseEntity<LoginResponseDTO> autenticarTelegramWebApp(@RequestBody TelegramWebAppLoginDTO dto) {
        User user = telegramLinkService.autenticarMiniApp(dto.initData());
        return ResponseEntity.ok(new LoginResponseDTO(tokenService.generateToken(user), user.getNome()));
    }
}
