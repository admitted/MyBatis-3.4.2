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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 方法 调用者
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

    private Class<?> type;
    private Method method;

    public MethodInvoker(Method method) {
        this.method = method;

        // 方法的形参类型 正好只有一个形参 type 等于此形参
        // 否则 type 为返回参数类型
        // 为什么会这样设计呢? 有意思哈! 注意 getXxx()方法有返回值,但无形参; 而 setXxx(A a) 有形参,但无返回值, 懂了吧
        if (method.getParameterTypes().length == 1) {
            type = method.getParameterTypes()[0];
        } else {
            type = method.getReturnType();
        }
    }

    /**
     * Method 调用 invoke 即可执行本身的方法 并返回相应结果
     * @param target 是拥有此 Method 方法的目标类
     * @param args 此方法的形参
     * @return 返回值
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, args);
    }

    /**
     * 若是 getter 方法 则 type 是返回类型
     * 若是 setter 方法 则 type 是形参类型
     * @return
     */
    @Override
    public Class<?> getType() {
        return type;
    }
}
