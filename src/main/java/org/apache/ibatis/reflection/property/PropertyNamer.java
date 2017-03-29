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
package org.apache.ibatis.reflection.property;

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 *
 * 反射类 的 属性名
 * 属性命名器
 * @author Clinton Begin
 */
public final class PropertyNamer {

    private PropertyNamer() {
        // Prevent Instantiation of Static Class
        // 防止静态类的实例化 ???
    }

    /**
     * 根据方法名 提取 属性名
     * @param name
     * @return
     */
    public static String methodToProperty(String name) {
        if (name.startsWith("is")) {
            name = name.substring(2); //"unhappy".substring(2) = "happy";
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
        }

        // 如果只有1个字母-->转为小写
        // 如果大于1个字母，第二个字母非大写-->转为小写
        // String url >> String getUrl() >>  url
        // String URL >> String getURL() >>  URL
        if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

    //判断是否是属性(以 get\set\is 开头)
    public static boolean isProperty(String name) {
        return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
    }

    //判断是否 get\is 属性
    public static boolean isGetter(String name) {
        return name.startsWith("get") || name.startsWith("is");
    }

    //判断是否 set 属性
    public static boolean isSetter(String name) {
        return name.startsWith("set");
    }

}
