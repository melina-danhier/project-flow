package com.melina.projectflow.user.mapper;

import com.melina.projectflow.user.dto.UserDto;
import com.melina.projectflow.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
