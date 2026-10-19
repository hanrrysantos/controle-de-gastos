package hanrry.cdg.repositories;

import hanrry.cdg.models.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByUserId(Long userId);
    Optional<Categoria> findByIdAndUserId(Long id, Long userId);
}
