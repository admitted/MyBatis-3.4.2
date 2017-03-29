/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.mapping;

import javax.sql.DataSource;

import org.apache.ibatis.transaction.TransactionFactory;

/**
 * 数据库运行环境
 * @author Clinton Begin
 */
public final class Environment {
    private final String id;
    private final TransactionFactory transactionFactory;
    private final DataSource dataSource;

    /**
     * Environment 构造器
     * @param id ID标记
     * @param transactionFactory
     * @param dataSource
     */
    public Environment(String id, TransactionFactory transactionFactory, DataSource dataSource) {
        if (id == null) {
            throw new IllegalArgumentException("Parameter 'id' must not be null");
        }
        if (transactionFactory == null) {
            throw new IllegalArgumentException("Parameter 'transactionFactory' must not be null");
        }
        this.id = id;
        if (dataSource == null) {
            throw new IllegalArgumentException("Parameter 'dataSource' must not be null");
        }
        this.transactionFactory = transactionFactory;
        this.dataSource = dataSource;
    }

    /**
     * 为什么造一个这个和外部类很像的静态内部类??? 为什么要这么做??? 好处是什么???
     *
     * 运用链表式 返回 this 对象
     * 静态内部类 : 相当于一个静态属性
     * 外部可调用此类的方法  例如: XMLConfigBuilder 的 environmentsElement 方法
     * Environment.Builder environmentBuilder = new Environment.Builder(id)
                        .transactionFactory(txFactory)
                        .dataSource(dataSource);
       Environment environment = environmentBuilder.build();
     */
    public static class Builder {
        private String id;
        private TransactionFactory transactionFactory;
        private DataSource dataSource;

        public Builder(String id) {
            this.id = id;
        }

        public Builder transactionFactory(TransactionFactory transactionFactory) {
            this.transactionFactory = transactionFactory;
            return this;
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public String id() {
            return this.id;
        }

        /**
         * 内部build 构造一个外部类 Environment 实例
         * 注意 this.id 代表 的是 build 对象哦
         * @return Environment
         */
        public Environment build() {
            return new Environment(this.id, this.transactionFactory, this.dataSource);
        }

    }

    public String getId() {
        return this.id;
    }

    public TransactionFactory getTransactionFactory() {
        return this.transactionFactory;
    }

    public DataSource getDataSource() {
        return this.dataSource;
    }

}
