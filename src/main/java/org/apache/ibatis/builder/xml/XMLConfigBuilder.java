/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   *
   * @return
   *
   * <?xml version="1.0" encoding="UTF-8" ?>
   * <!DOCTYPE configuration
   *   PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
   *   "http://mybatis.org/dtd/mybatis-3-config.dtd">
   * <configuration>
   *   <environments default="development">
   *     <environment id="development">
   *       <transactionManager type="JDBC"/>
   *       <dataSource type="POOLED">
   *         <property name="driver" value="${driver}"/>
   *         <property name="url" value="${url}"/>
   *         <property name="username" value="${username}"/>
   *         <property name="password" value="${password}"/>
   *       </dataSource>
   *     </environment>
   *   </environments>
   *   <mappers>
   *     <mapper resource="org/mybatis/example/BlogMapper.xml"/>
   *   </mappers>
   * </configuration>
   */
  /**
   *
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    /**
     * 一个典型的mybatis-config.xml文件如上所示。那么第一步先解析到最外层的 configuration节点
     *
     * parser.evalNode("/configuration") 是解析某一个节点的逻辑，具体可以调用相应的api做到，里面逻辑很复杂，暂且略过
     */
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      /**
       * 以上4行代码涉及到mybatis缓存相关逻辑，后续补上
       */

      /**
       * 解析typeAliases节点，
       * <typeAliases >
       *   <typeAlias type="user" alias="com.smart.User"></typeAlias>
       * </typeAliases>
       * 如上面配置之后，在xml文件中就可以使用user代替com.smart.User了，很简便。而在使用过程中，有时候在使用parameterType时，值设置为map或者string就可以，
       * 是因为别名的原因，且别名不区分大小写，所以写java.lang.String和写string或者String是一样的效果，就是因为mybtis默认有一些基本的别名存在。
       */

      typeAliasesElement(root.evalNode("typeAliases"));

      /**
       * plugins节点后续补上
       *
       */
      pluginElement(root.evalNode("plugins"));

      /**
       * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
       *   <property name="someProperty" value="100"/>
       * </objectFactory>
       *
       * 其配置如上所示，简单点说，在我们查询时，如果查询返回了一个对象，那么mybatis会使用默认的DefaultObjectFactory(实现自ObjectFactory)类来帮我们创建对象
       * 如果我们需要自定义创建对象的规则，那么可以配一个objectFactory。
       * 1、需要自定义一个类，实现DefaultObjectFactory，复写其create方法，加入自己的逻辑。
       *
       *
       * 例如：
       * public class MyObjectFactory extends DefaultObjectFactory {
       *     private static final long serialVersionUID = -4293520460481008255L;
       *     Logger log = Logger.getLogger(MyObjectFactory.class);
       *     private Object temp = null;
       *
       *    //这个方法会在查询语句塞值时调用
       *     @Override
       *     public void setProperties(Properties properties) {
       *         super.setProperties(properties);
       *         log.info("初始化参数：【" + properties.toString() + "】");
       *     }
       *
       *     // 方法2
       *     @Override
       *     public <T> T create(Class<T> type) {
       *         T result = super.create(type);
       *         log.info("创建对象：" + result.toString());
       *         log.info("是否和上次创建的是同一个对象：【" + (temp == result) + "】");
       *         return result;
       *     }
       *
       *     // 方法1
       *     @Override
       *     public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes,
       *             List<Object> constructorArgs) {
       *         T result = super.create(type, constructorArgTypes, constructorArgs);
       *         log.info("创建对象：" + result.toString());
       *         temp = result;
       *         return result;
       *     }
       *
       *     @Override
       *     public <T> boolean isCollection(Class<T> type) {
       *         return super.isCollection(type);
       *     }
       * }
       *
       *mybatis-config.xml中配置为：
       *
       * <objectFactory type="com.mybatis.test.MyObjectFactory">
       *     <property name="prop1" value="value1" />
       * </objectFactory>
       *
       *
       *
       *测试代码为：
       *          InputStream config = Resources.getResourceAsStream("mybatis-config.xml");
       *         SqlSessionFactory ssf = new SqlSessionFactoryBuilder().build(config);
       *         SqlSession ss = ssf.openSession();
       *         UserMapper userMapper = ss.getMapper(UserMapper.class);
       *         User user = userMapper.getUser(1L);
       *         System.out.println(user.getUserName());
       *
       *
       *那么打印结果为：
       *  先打印初始化参数：
       *  （方法2调用）创建对象：
       *  （方法1调用）创建对象：
       *
       */
      objectFactoryElement(root.evalNode("objectFactory"));

      /**
       * 反射的时候会用到，后续再学习 MateObject
       */
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);


      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));//jdbc相关内容解析
      /**
       * databaseIdProvider可以做到在程序中，根据数据库的不同，而切换执行语句。也就是说支持多种厂商的数据库，例如mysql、oracle等等。
       *
       * mapper中使用为：
       * <mapper namespace="com.hengzhe.hz.manager.data.biz.dao.TestDBMapper">
       *    <select id="selectTime"  resultType="String" databaseId="mysql">
       *         select now() from dual
       *     </select>
       *
       *     <select id="selectTime"  resultType="String" databaseId="oracle">
       *         select sysdate from dual
       *     </select>
       *
       *
       *     <select id="selectTime"  resultType="String">
       *         select
       *         <if test="_databaseId == 'mysql'">
       *           now()
       *         </if>
       *         <if test="_databaseId == 'oracle'">
       *           sysdate
       *         </if>
       *         from dual
       *     </select>
       * </mapper>
       *
       *
       *
       *
       *配置：
       * <databaseIdProvider type="DB_VENDOR">
       *   <property name="SQL Server" value="sqlserver"/>
       *   <property name="DB2" value="db2"/>
       *   <property name="Oracle" value="oracle" />
       * </databaseIdProvider>
       *
       *
       * 后续有需要可以查询相关资料深入学习
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      /**
       * typeHandlers可以把数据库的类型映射到java类型上去
       */
      typeHandlerElement(root.evalNode("typeHandlers"));

      /**
       * 解析mappers节点，非常重要
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 此处的参数就是已经把typeAliases节点解析完成，可以当作一棵树结构
   *
   *
   * <typeAliases >
   *   <typeAlias type="" alias=""></typeAlias>
   *   <package name="com.smart"/>    这个节点的意思是，name所配置的值（一个包名），会在这个包下寻找别名，若当前class已经使用注解
   *
   *   @Alias("author")
   * public class Author {
   *     ...
   * }
   *  如这样的形式，那么这个 Author类的别名是author。若没有此注解，那么会用默认别名注册到别名管理map中去，
   *  默认的别名机制是：和spring bean默认一样，取类的首字母为小写作为别名
   *
   * </typeAliases>
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /**
         * package总的来说就是，找到name所指示的值，这个值是一个包名，利用VFS找到这个包名下所有符合条件的class，加载到内存中，
         * 然后找是否有@alias("别名")，如果有把这个值注册到一个别名map中去，否则，默认的别名注册到别名map中去。
         *
         */
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          /**
           * <typeAlias type="" alias=""></typeAlias>
           * <typeAliases >标签下的子标签，配置的是typeAlias这个
           */
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }


  /**
   * 在<mappers></mappers>标签中，可以有4种方式，分别是：
   *
   *  <mappers>
   *   <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
   *   <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
   *   <mapper resource="org/mybatis/builder/PostMapper.xml"/>
   * </mappers>
   *
   *  <mappers>
   *   <mapper url="file:///var/mappers/AuthorMapper.xml"/>
   *   <mapper url="file:///var/mappers/BlogMapper.xml"/>
   *   <mapper url="file:///var/mappers/PostMapper.xml"/>
   * </mappers>
   *
   *
   *  <mappers>
   *   <mapper class="org.mybatis.builder.AuthorMapper"/>
   *   <mapper class="org.mybatis.builder.BlogMapper"/>
   *   <mapper class="org.mybatis.builder.PostMapper"/>
   * </mappers>
   *
   *  <mappers>
   *   <package name="org.mybatis.builder"/>
   * </mappers>
   *
   * 在这4种配置当中，显然<package/>是最大的，所以先解析这个
   *
   *
   *
   *
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        /**
         * 同样的，利用VFS文件系统，找到包下面的class文件
         */
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
