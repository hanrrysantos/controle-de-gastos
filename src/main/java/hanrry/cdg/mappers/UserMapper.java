package hanrry.cdg.mappers;

import hanrry.cdg.dtos.UserRegisterDTO;
import hanrry.cdg.dtos.UserResponseDTO;
import hanrry.cdg.models.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(UserRegisterDTO dto);

    UserResponseDTO toDto(User user);
}
