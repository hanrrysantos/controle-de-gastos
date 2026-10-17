package hanrry.cdg.repositories;

import hanrry.cdg.models.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByUserId(Long userId);
}
