package de.melinadanhier.projectflow.user.mapper;

import de.melinadanhier.projectflow.user.dto.UserDto;
import de.melinadanhier.projectflow.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
