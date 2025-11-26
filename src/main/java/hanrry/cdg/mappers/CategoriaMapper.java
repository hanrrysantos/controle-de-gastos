package hanrry.cdg.mappers;

import hanrry.cdg.dtos.CategoriaCreateDTO;
import hanrry.cdg.dtos.CategoriaResponseDTO;
import hanrry.cdg.models.Categoria;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoriaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "transacoes", ignore = true)
    Categoria toEntity(CategoriaCreateDTO dto);

    CategoriaResponseDTO toDto(Categoria entity);
}