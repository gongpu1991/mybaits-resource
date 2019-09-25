package example;


import example.Mapper.UserMapper;
import example.dao.User;
import example.vo.Page;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

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
    Page page = new Page();
    page.setPageIndex(2);
    page.setPageSize(3);
    HashMap<String, Object> map = new HashMap<>();
    map.put("page",page);
    map.put("name","gp");
    List<User> users = userMapper.selectByNameByPage(map);
    System.out.println(users);
    /**
     * 在二级缓存中，缓存的保存是基于事务的，如果事务成功才会提交缓存。
     * 如果不调用sqlSession.commit();或者sqlSession.close();事务不会提交，那么二级缓存不会使用。
     * 当查询到记录时，先把查询的数据放到一个待提交的map里，mybatis里这个map是：entriesToAddOnCommit。
     *
     * 如果调用了commit()或者close()方法，那么最终会调用到
     *     @Override
    public void commit(boolean force) {
    try {
    executor.commit(isCommitOrRollbackRequired(force));
    dirty = false;
    } catch (Exception e) {
    throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
    } finally {
    ErrorContext.instance().reset();
    }
    }




    public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
    txCache.commit();
    }
    }


    private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
    delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
    if (!entriesToAddOnCommit.containsKey(entry)) {
    delegate.putObject(entry, null);
    }
    }
    }
     *
     */
//    sqlSession.commit();
//    SqlSession sqlSession1 = sqlSessionFactory.openSession();
//    /**
//     * 不同的sqlSession的查询
//     */
//    UserMapper userMapper1 = sqlSession1.getMapper(UserMapper.class);
//    User user1 = userMapper1.selectById("gp",2L);
//    System.out.println(user1);
  }
}
