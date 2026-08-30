package com.serviceflow.mapper;

import com.serviceflow.model.AuthModels;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    AuthModels.User findByUsername(@Param("username") String username);
}
