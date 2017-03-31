/**
 * Copyright 2009-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

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
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * XML 配置构建器
 * Builder 模式
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    private boolean parsed;       // 是否被解析过
    private XPathParser parser;   // XPathParser 解析器
    private String environment;   // 数据库环境
    private ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    /*****************************************************
     ********************** 构造器 ***********************
     *****************************************************/
    //以下三个 参数中 用字符流
    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    /*************注意看构造器参数*************/

    //以下三个 参数中 用字节流
    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    /**
     * 最后都调用这个构造器
     * @param parser
     * @param environment
     * @param props
     */
    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        // 调用父类的配置初始化操作
        super(new Configuration());
        // 错误上下文 SQL Mapper Configuration(XML文件配置),
        ErrorContext.instance().resource("SQL Mapper Configuration");
        // 将Properties 设置到父类的 Configuration 去
        this.configuration.setVariables(props);

        this.parsed = false;  // 默认没被解析
        this.environment = environment; // 注入 环境
        this.parser = parser; // 注入解析器
    }

    /**
     *  解析配置文件
     * <?xml version="1.0" encoding="UTF-8" ?>
     *   <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
     *   "http://mybatis.org/dtd/mybatis-3-config.dtd">
     *   <configuration>
     *   	<environments default="development">
     *   		<environment id="development">
     *   			<transactionManager type="JDBC"/>
     *   			<dataSource type="POOLED">
     *   				<property name="driver" value="${driver}"/>
     *   				<property name="url" value="${url}"/>
     *   				<property name="username" value="${username}"/>
     *   				<property name="password" value="${password}"/>
     *   			</dataSource>
     *   		</environment>
     *   	</environments>
     *   	<mappers>
     *   		<mapper resource="org/mybatis/example/BlogMapper.xml"/>
     *   	</mappers>
     *   </configuration>
     *
     * @return
     */
    public Configuration parse() {
        // 如果解析过了 报错
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        // 根节点是configuration
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 解析 好多的配置 信息
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            //issue #117 read properties first
            // 1 解析 properties 配置信息
            propertiesElement(root.evalNode("properties"));
            // 2 解析 settings
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            loadCustomVfs(settings);  // 加载自定义的 VFS
            // 3 解析 别名
            typeAliasesElement(root.evalNode("typeAliases"));
            // 4 解析 插件 plugins
            pluginElement(root.evalNode("plugins"));
            // 5 解析 对象工厂
            objectFactoryElement(root.evalNode("objectFactory"));
            // 6 解析 对象包装工厂
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            // 7 解析 反射工厂
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            // 8 解析 环境
            environmentsElement(root.evalNode("environments"));
            // 9 解析 databaseIdProvider
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // 10 解析 typeHandlers 类型处理器
            typeHandlerElement(root.evalNode("typeHandlers"));
            // 11 解析 映射器 mappers
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 将 XNode 节点解析为 Properties 键值对 (HashTable)
     * @param context
     * @return
     */
    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        // Properties 是一个 HashTable
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        // 检测所有的配置是否被 configuration 类已知
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    /**
     * 加载自定义 vfsImpl 配置
     * @param props
     * @throws ClassNotFoundException
     */
    private void loadCustomVfs(Properties props) throws ClassNotFoundException {
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            String[] clazzes = value.split(",");
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    // VFS 的实现类或 VFS 类
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    /**
     * 类型别名解析
     * 1. 类别名
     * <typeAliases>
     *   <typeAlias alias="Author" type="domain.blog.Author"/>
     *   <typeAlias alias="Blog" type="domain.blog.Blog"/>
     *   <typeAlias alias="Comment" type="domain.blog.Comment"/>
     *   <typeAlias alias="Post" type="domain.blog.Post"/>
     *   <typeAlias alias="Section" type="domain.blog.Section"/>
     *   <typeAlias alias="Tag" type="domain.blog.Tag"/>
     * </typeAliases>
     * or
     * 2. 包下的 @Alias 注解
     * <typeAliases>
     *   <package name="domain.blog"/>
     * </typeAliases>
     *
     * @param parent
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 若是 package
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    // （一）调用TypeAliasRegistry.registerAliases，去包下找所有类,然后注册别名
                    // (有@Alias注解则用，没有则取类的simpleName)
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    // // 如果是typeAlias
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        // 根据Class名字来注册类型别名
                        //（二）调用TypeAliasRegistry.registerAlias
                        if (alias == null) {
                            // 别名为空 则省略
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

    /**
     * 插件解析
     *  MyBatis 允许你在某一点拦截已映射语句执行的调用。默认情况下,MyBatis 允许使用插件来拦截方法调用
     *    <plugins>
     *      <plugin interceptor="org.mybatis.example.ExamplePlugin">
     *        <property name="someProperty" value="100"/>
     *      </plugin>
     *    </plugins>
     *
     * @param parent
     * @throws Exception
     */
    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            // 可能会有多个插件
            for (XNode child : parent.getChildren()) {
                // interceptor 拦截器
                String interceptor = child.getStringAttribute("interceptor");
                Properties properties = child.getChildrenAsProperties();
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
                interceptorInstance.setProperties(properties);
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    /**
     * 对象工厂解析
     *  <objectFactory type="org.mybatis.example.ExampleObjectFactory">
     *    <property name="someProperty" value="100"/>
     *  </objectFactory>
     * @param context
     * @throws Exception
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    /**
     * 包装类解析
     * @param context
     * @throws Exception
     */
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    /**
     * 反射器解析
     * @param context
     * @throws Exception
     */
    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    /**
     * properties 解析
     * 多个属性的话 , 加载方式如下 :
     *  1.在 properties 元素体内指定的属性首先被读取。
     *  2.从类路径下资源或 properties 元素的 url 属性中加载的属性第二被读取,它会覆盖已经存在的完全一样的属性。
     *  3.作为方法参数传递的属性最后被读取, 它也会覆盖任一已经存在的完全一样的属性,这些属性可能是从 properties 元
     *    素体内和 资源/url 属性中加载的。传入方式是调用构造函数时传入:
     *    public XMLConfigBuilder(Reader reader, String environment, Properties props)
     *
     *    <properties resource="org/mybatis/example/config.properties">
     *        <property name="username" value="dev_user"/>
     *        <property name="password" value="F2Fa3!33TYyg"/>
     *    </properties>
     *
     * @param context
     * @throws Exception
     */
    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 得到 Properties 的所有子节点
            Properties defaults = context.getChildrenAsProperties();
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // 若 resource 和 URL 只能取其一  , 都有的话: 报错
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                // 若 resource 存在
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                // 若 URL 存在
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            // 当前已存在的 Variables
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    /**
     * 设置 : 重要调整, 指示着 MyBatis 在运行时的行为方式
     * <settings>
     *   <setting name="cacheEnabled"               value="true"/>
     *   <setting name="lazyLoadingEnabled"         value="true"/>
     *   <setting name="multipleResultSetsEnabled"  value="true"/>
     *   <setting name="useColumnLabel"             value="true"/>
     *   <setting name="useGeneratedKeys"           value="false"/>
     *   <setting name="enhancementEnabled"         value="false"/>
     *   <setting name="defaultExecutorType"        value="SIMPLE"/>
     *   <setting name="defaultStatementTimeout"    value="25000"/>
     *   <setting name="safeRowBoundsEnabled"       value="false"/>
     *   <setting name="mapUnderscoreToCamelCase"   value="false"/>
     *   <setting name="localCacheScope"            value="SESSION"/>
     *   <setting name="jdbcTypeForNull"            value="OTHER"/>
     *   <setting name="lazyLoadTriggerMethods"     value="equals,clone,hashCode,toString"/>
     * </settings>
     *
     * 根据配置文件 设置相关配置
     * @param props
     * @throws Exception
     */
    private void settingsElement(Properties props) throws Exception {
        // 如何自动映射列到 字段 / 属性  : PARTIAL
        configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        // 自动映射不知名字段 : none
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        // 设置缓存 (一级缓存) : TRUE
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        // 设置代理工厂(从配置中获取) proxyFactory (CGLIB | JAVASSIST)
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        // 设置延迟加载 : FALSE (延迟加载的核心技术就是用代理模式，CGLIB/JAVASSIST两者选一)
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        // 设置延迟加载时, 每种属性是否还要延迟加载 : FALSE
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        // 设置是否 多种结果集从一个单独 的语句中返回 : TRUE
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        // 设置使用列标签代替列名: TRUE
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        // 设置是否使用 数据库 生成主键
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        // 设置默认执行器 : Simple
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        // 设置超时时间: null
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        // 意思是当调用rs.next时，ResultSet会一次性从服务器上取得多少行数据回来，这样在下次rs.next时，可以从缓存中取出来
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        // 设置是否将 数据库字段 自动映射到 驼峰式Java属性（CPM_NAME-->cpmName） : FALSE
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        // 设置是否在嵌套语句上使用RowBounds
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        // 设置本地缓存范围 : 默认用session级别的缓存
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        // 设置为 NUll 值设置 JdbcType : other
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        // 设置Object对象的哪些方法触发延迟加载函数: equal,clone,hashCode,toString
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        // 设置使用安全的ResultHandler : TRUE
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        // 设置动态SQL生成语言所使用的脚本语言
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        // 设置当结果集中含有Null值时是否执行映射对象的setter或者Map对象的put方法。此设置对于原始类型如int,boolean等无效。???
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        // 设置使用实际属性名称: TRUE
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        // 设置是否为查询到的空行返回实例
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        // logger名字的前缀
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        @SuppressWarnings("unchecked")
        Class<? extends Log> logImpl = (Class<? extends Log>) resolveClass(props.getProperty("logImpl"));
        // 设置 log 实现类
        configuration.setLogImpl(logImpl);
        // 设置配置工厂
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }

    /**
     	<environments default="development">
     	  <environment id="development">
     	    <transactionManager type="JDBC">
                <property name="..." value="..."/>
     	    </transactionManager>
            <dataSource type="POOLED">
                <property name="driver" value="${driver}"/>
                <property name="url" value="${url}"/>
                <property name="username" value="${username}"/>
                <property name="password" value="${password}"/>
     	    </dataSource>
     	  </environment>
     	</environments>
     * 数据库环境配置
     *   事务 \ 数据库连接池
     * @param context xml 文档
     * @throws Exception
     */
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
                    // 建造器 注入 环境 id , 事务txFactory , 连接池 dataSource
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    // 将环境配置加入到 总配置文件中去
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    /**
     * 根据不同数据库执行不同的SQL，sql要加databaseId属性
       参考org.apache.ibatis.submitted.multidb包里的测试用例
     	<databaseIdProvider type="VENDOR">
     	  <property name="SQL Server"   value="sqlserver"   />
     	  <property name="DB2"          value="db2"         />
     	  <property name="Oracle" v     alue="oracle"       />
     	</databaseIdProvider>
     *
     * @param context
     * @throws Exception
     */
    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            // 保持向后兼容性
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    /**
     * 事务管理器
     *           <transactionManager type="JDBC">
     *              <property name="..." value="..."/>
     *           </transactionManager>
     * @param context
     * @return
     * @throws Exception
     */
    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 根据 type="JDBC" 解析返回适当的 TransactionFactory (JdbcTransactionFactory)
            TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    /**
     * 数据源
     * <dataSource type="POOLED">
     *   <property name="driver" value="${driver}"/>
     *   <property name="url" value="${url}"/>
     *   <property name="username" value="${username}"/>
     *   <property name="password" value="${password}"/>
     * </dataSource>
     *
     * @param context
     * @return
     * @throws Exception
     */
    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 根据 type="POOLED" 返回适当的 DataSourceFactory (PooledDataSourceFactory)
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     *  类型处理器
     *	<typeHandlers>
     *	  <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
     *	</typeHandlers>
     * or
     * 	<typeHandlers>
     *	  <package name="org.mybatis.example"/>
     *	</typeHandlers>
     *
     * @param parent
     * @throws Exception
     */
    private void typeHandlerElement(XNode parent) throws Exception {
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
     *  映射器  四种只能同时使用一种
     * 	1. 使用类路径
     * 	<mappers>
     * 	  <mapper resource="org/mybatis/builder/AuthorMapper.xml"/>
     * 	  <mapper resource="org/mybatis/builder/BlogMapper.xml"/>
     * 	  <mapper resource="org/mybatis/builder/PostMapper.xml"/>
     * 	</mappers>
     *
     * 	2. 使用绝对url路径
     * 	<mappers>
     * 	  <mapper url="file:///var/mappers/AuthorMapper.xml"/>
     * 	  <mapper url="file:///var/mappers/BlogMapper.xml"/>
     * 	  <mapper url="file:///var/mappers/PostMapper.xml"/>
     * 	</mappers>
     *
     * 	3. 使用java类名
     * 	<mappers>
     * 	  <mapper class="org.mybatis.builder.AuthorMapper"/>
     * 	  <mapper class="org.mybatis.builder.BlogMapper"/>
     * 	  <mapper class="org.mybatis.builder.PostMapper"/>
     * 	</mappers>
     *
     * 	4. 自动扫描包下所有映射器
     * 	<mappers>
     * 	  <package name="org.mybatis.builder"/>
     * 	</mappers>
     *
     * @param parent
     * @throws Exception
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 首先看是不是自动扫描
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {
                    // 若不是自动扫描
                    String resource     = child.getStringAttribute("resource"); // 类路径
                    String url          = child.getStringAttribute("url");      // 绝对路径
                    String mapperClass  = child.getStringAttribute("class");    // Java 全称限定名
                    // 若类路径 resource 存在 , 且其他不存在
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();

                    // 若绝对路径 url 存在 , 且其他不存在
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();

                    // 若Java 类名 mapperClass 存在 , 且其他不存在
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

    /**
     * 确认是否是当前 environment 环境下的 配置文件
     * @param id
     * @return
     */
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
