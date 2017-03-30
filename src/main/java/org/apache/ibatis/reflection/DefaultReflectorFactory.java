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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认 反射器工厂 类
 * mybatis 允许我们自己写自己的  ReflectorFactory 实现类
 * 这个是默认给我们提供的
 */
public class DefaultReflectorFactory implements ReflectorFactory {
    // 默认开启缓存 应该是一级缓存
    private boolean classCacheEnabled = true;
    // ConcurrentMap 线程安全 map
    private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<Class<?>, Reflector>();

    /**
     * 空参构造器
     */
    public DefaultReflectorFactory() {
    }

    @Override
    public boolean isClassCacheEnabled() {
        return classCacheEnabled;
    }

    @Override
    public void setClassCacheEnabled(boolean classCacheEnabled) {
        this.classCacheEnabled = classCacheEnabled;
    }

    /**
     *
     * @param type
     * @return
     */
    @Override
    public Reflector findForClass(Class<?> type) {
        // 如果开启了缓存
        if (classCacheEnabled) {
            // synchronized (type) removed see issue #461
            Reflector cached = reflectorMap.get(type);
            if (cached == null) {
                cached = new Reflector(type);
                reflectorMap.put(type, cached);
            }
            return cached;
        } else {
            return new Reflector(type);
        }
    }

}
