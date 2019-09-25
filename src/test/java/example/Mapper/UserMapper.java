package example.Mapper;


import example.dao.User;
import org.apache.ibatis.annotations.Param;


import java.util.List;
import java.util.Map;

public interface UserMapper {
  User selectById(@Param("name") String name, @Param("id") Long id);

  List<User> selectAll();

  User selectByIdAndName(long id, String name);

  List<User> selectByNameByPage(Map<String,Object> param);
}
