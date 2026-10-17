package hanrry.cdg.config;

import hanrry.cdg.repositories.UserRepository;
import hanrry.cdg.services.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public SecurityFilter(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = recoverToken(request);

        if (token != null) {
            var login = tokenService.validateToken(token);

            if (login != null && !login.isEmpty()) {
                try {
                    var userEntity = login.startsWith("id:")
                            ? userRepository.findById(Long.parseLong(login.substring(3)))
                            : userRepository.findByEmail(login);
                    userEntity.ifPresent(entity -> {
                        UserDetails user = org.springframework.security.core.userdetails.User
                                .withUsername(entity.getId().toString())
                                .password(entity.getSenha() == null ? "" : entity.getSenha())
                                .authorities("USER")
                                .build();
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
                    });
                } catch (NumberFormatException ignored) {
                    // Um identificador inválido não autentica a requisição.
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        return authHeader.replace("Bearer ", "").trim();
    }
}
