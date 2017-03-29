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
package org.apache.ibatis.session;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * Builds {@link SqlSession} instances.
 * 工厂模式 : 构建 SqlSessionFactory 的工厂
 *
 * @update Cui Jing
 * @date 2017/3/27 20:21:20
 * @author Clinton Begin
 */
public class SqlSessionFactoryBuilder {

    /*****************************************************
     ****** 方法返回一个 SqlSessionFactory *****************
     *****************************************************/
    // 下面的9个方法重载
    // 下面三个 包含 Reader 字符流的 build 方法 调用的是第4个 build 方法
    public SqlSessionFactory build(Reader reader) {
        return build(reader, null, null);
    }

    public SqlSessionFactory build(Reader reader, String environment) {
        return build(reader, environment, null);
    }

    public SqlSessionFactory build(Reader reader, Properties properties) {
        return build(reader, null, properties);
    }

    /**
     * 第4种方法是最常用的，它使用了一个参照了XML文档或更特定的SqlMapConfig.xml文件的Reader实例。可选的参数是environment
     * 和properties。Environment决定加载哪种环境(开发环境/生产环境)，包括数据源和事务管理器。如果使用properties，那么就会
     * 加载那些properties（属性配置文件），那些属性可以用${propName}语法形式多次用在配置文件中。和Spring很像，一个思想？
     *
     * @param reader
     * @param environment
     * @param properties
     * @return
     */
    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                reader.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    // 下面三个 包含 InputStream 字节流的 build 方法 调用的是第 8 build 方法
    public SqlSessionFactory build(InputStream inputStream) {
        return build(inputStream, null, null);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment) {
        return build(inputStream, environment, null);
    }

    public SqlSessionFactory build(InputStream inputStream, Properties properties) {
        return build(inputStream, null, properties);
    }

    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                inputStream.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    /**
     * 根据传入的 Configuration 返回 相应配置的 SqlSessionFactory
     * 第 9 个方法有意思了 , 每个方法最后都会调用到它
     *
     */
    public SqlSessionFactory build(Configuration config) {
        return new DefaultSqlSessionFactory(config);
    }

}
