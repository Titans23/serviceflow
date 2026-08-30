package com.serviceflow.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserRow findByUsername(@Param("username") String username);

    record UserRow(long id, String username, String passwordHash, String role, Long customerId, boolean enabled) {}
}
