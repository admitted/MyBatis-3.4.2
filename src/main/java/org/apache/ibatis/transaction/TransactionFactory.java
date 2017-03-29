/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;

/**
 * Creates {@link Transaction} instances.
 * 事务工厂 接口
 * @author Clinton Begin
 */
public interface TransactionFactory {

  /**
   * Sets transaction factory custom properties.
   * @param props
   */
  void setProperties(Properties props);

  /**
   * Creates a {@link Transaction} out of an existing connection.
   * 在已存的 connection 中创建一个 事务 transaction
   * @param conn Existing database connection
   * @return Transaction
   * @since 3.1.0
   */
  Transaction newTransaction(Connection conn);
  
  /**
   * Creates a {@link Transaction} out of a datasource.
   * 向一个DataSource获取数据库链接 创建一个新的事务 , 并配置此事务的隔离级别和是否自动 commit
   * @param dataSource DataSource to take the connection from   向此 DataSource 获取 connection
   * @param level Desired isolation level                       要求的  隔离级别
   * @param autoCommit Desired autocommit                       要求    是否自动提交
   * @return Transaction
   * @since 3.1.0
   */
  Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit);

}
