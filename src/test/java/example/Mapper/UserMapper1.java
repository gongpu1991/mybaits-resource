package example.Mapper;


import example.dao.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper1 {
  User selectById(@Param("name") String name, @Param("id") Long id);

  List<User> selectAll();

  User selectByIdAndName( String name);
}
