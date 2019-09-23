package gongpu;

import gongpu.Mapper.UserMapper;
import gongpu.dao.User;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class MybatisTest {
  public static void main(String[] args) throws IOException {
    String xmlSource = "resources/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(xmlSource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    /**
     * 解析xml时，如果是${},则没有做转换，如果是#{}，则替换为？,但是如果一条语句中两者都有，也会在运行时去替换。
     */
    SqlSession sqlSession = sqlSessionFactory.openSession();
    UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
    User user = userMapper.selectById("gongpu",4L);
    System.out.println(user);
   // sqlSession.close();
   // SqlSession sqlSession = sqlSessionFactory.openSession();
    UserMapper userMapper1 = sqlSession.getMapper(UserMapper.class);
    User user1 = userMapper1.selectById("gongpu",4L);
    System.out.println(user1);
  }
}
