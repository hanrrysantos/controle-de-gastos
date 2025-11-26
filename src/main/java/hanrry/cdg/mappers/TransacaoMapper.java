package hanrry.cdg.mappers;

import hanrry.cdg.dtos.TransacaoRequestDTO;
import hanrry.cdg.dtos.TransacaoResponseDTO;
import hanrry.cdg.models.Transacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransacaoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "tipo", ignore = true)
    Transacao toEntity(TransacaoRequestDTO dto);

    @Mapping(target = "nomeCategoria", source = "categoria.nome")
    @Mapping(target = "tipo", source = "categoria.tipo")
    TransacaoResponseDTO toDto(Transacao entity);
}