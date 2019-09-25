package example.plugins;

import example.vo.Page;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import static org.apache.ibatis.reflection.SystemMetaObject.DEFAULT_OBJECT_FACTORY;
import static org.apache.ibatis.reflection.SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY;

/**
 * Created by gongpu on 2019/9/25 9:58
 */
@Intercepts(@Signature(type = StatementHandler.class,method = "prepare",args = {Connection.class, Integer.class}))
public class MyPagePlugin  implements Interceptor{


    String databaseType=null;
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = MetaObject.forObject(statementHandler,DEFAULT_OBJECT_FACTORY,
                DEFAULT_OBJECT_WRAPPER_FACTORY,new DefaultReflectorFactory());

        String sqlId = (String) metaObject.getValue("delegate.mappedStatement.id");
        if (isPageSql(sqlId)){
            String sql = statementHandler.getBoundSql().getSql();
            String countSql="select count(0) as countD from ("+sql+") AS a";
            Connection connection = (Connection) invocation.getArgs()[0];
            PreparedStatement preparedStatement = connection.prepareStatement(countSql);
            ParameterHandler parameterHandler = statementHandler.getParameterHandler();
            /**
             * 替换实参
             */
            parameterHandler.setParameters(preparedStatement);
            ResultSet resultSet = preparedStatement.executeQuery();
            int count=0;
            if (resultSet.next()){
                count=resultSet.getInt(1);
            }
            resultSet.close();
            preparedStatement.close();

            Map<String,Object> parameterObject = (Map<String, Object>) parameterHandler.getParameterObject();
            Page  page = (Page) parameterObject.get("page");
            page.setCount(count);
            String pageSql = getPageSql(sql, page);
            metaObject.setValue("delegate.boundSql.sql",pageSql);
        }
        return invocation.proceed();
    }

    private boolean isPageSql(String sqlId) {
        String regex=".*ByPage$";
        boolean matches = Pattern.matches(regex, sqlId);
        return matches;
    }


    public String getPageSql(String sql,Page page){
        if(databaseType.equals("mysql")){
            return sql +" limit "+page.getPageIndex()+","+page.getPageIndex()*page.getPageSize();
        }else if(databaseType.equals("oracle")){
            //拼接oracle的分语句
        }

        return sql+" limit "+page.getPageIndex()+","+page.getPageIndex()*page.getPageSize();
    }


    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target,this);
    }

    @Override
    public void setProperties(Properties properties) {
        String sqlType = properties.getProperty("sqlType");
        this.databaseType=sqlType;
        System.out.println(sqlType);
    }
}
