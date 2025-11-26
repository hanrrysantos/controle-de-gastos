package hanrry.cdg.repositories;

import java.util.Optional;
import hanrry.cdg.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
