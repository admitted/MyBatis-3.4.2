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
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.*;
import java.util.*;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 *  反射器, 属性  >> getter/setter的映射器，且加了缓存
 *
 *  可参考 ReflectorTest
 * @author Clinton Begin
 */
public class Reflector {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private Class<?> type;
    private String[] readablePropertyNames  = EMPTY_STRING_ARRAY;                 // getter 属性名列表
    private String[] writeablePropertyNames = EMPTY_STRING_ARRAY;                 // setter 属性名列表
    private Map<String, Invoker> setMethods = new HashMap<String, Invoker>();                   // set (属性名 , invoker)
    private Map<String, Invoker> getMethods = new HashMap<String, Invoker>();     // get (属性名 , invoker)
    private Map<String, Class<?>> setTypes  = new HashMap<String, Class<?>>();                   // set 形参参数类型
    private Map<String, Class<?>> getTypes  = new HashMap<String, Class<?>>();    // get 返回参数类型
    private Constructor<?> defaultConstructor;                                    // 默认空参构造器

    private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();    // 传入的 Class 最终确定的属性 map集合

    /**
     * 构造器 : 根据传进来的 clazz , 构造出这类的一个实例, 并注入
     * @param clazz
     */
    public Reflector(Class<?> clazz) {
        type = clazz;
        //添加 默认构造器
        addDefaultConstructor(clazz);
        //添加 get 方法
        addGetMethods(clazz);
        //添加 set 方法
        addSetMethods(clazz);
        //添加 属性
        addFields(clazz);
        readablePropertyNames  = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
        writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
        // 下面的两次 for 循环 为了 确保没有遗漏的 属性 名
        // 比如某些属性 只有 get 方法没有 set
        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writeablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    /**
     * clazz 构造器注入 默认构造器 即空参的构造器
     * @param clazz
     */
    private void addDefaultConstructor(Class<?> clazz) {
        //获取 clazz 类的全部构造器
        Constructor<?>[] consts = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : consts) {
            // getParameterTypes() 方法 按照声明顺序返回一组 Class 对象，这些对象表示此 Constructor
            // 对象所表示构造方法的形参类型。
            if (constructor.getParameterTypes().length == 0) {
                if (canAccessPrivateMethods()) {
                    try {
                        constructor.setAccessible(true);
                    } catch (Exception e) {
                        // Ignored. This is only a final precaution, nothing we can do.
                        // 忽略报错: 仅是一个的预防, 防止程序中断
                    }
                }
                // 若 报错 则默认构造器 会为 null
                if (constructor.isAccessible()) {
                    this.defaultConstructor = constructor;
                }
            }
        }
    }

    /**
     * 添加 get 方法
     * @param cls
     */
    private void addGetMethods(Class<?> cls) {
        Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
        Method[] methods = getClassMethods(cls);
        for (Method method : methods) {
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                if (method.getParameterTypes().length == 0) {
                    name = PropertyNamer.methodToProperty(name);         //提取属性名
                    addMethodConflict(conflictingGetters, name, method); //去除(重载的方法) 保持只有一个 name
                }
            } else if (name.startsWith("is") && name.length() > 2) {
                if (method.getParameterTypes().length == 0) {
                    name = PropertyNamer.methodToProperty(name);
                    addMethodConflict(conflictingGetters, name, method);
                }
            }
        }
        resolveGetterConflicts(conflictingGetters);
    }

    /**
     * 处理冲突的 get 方法
     * @param conflictingGetters
     */
    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (String propName : conflictingGetters.keySet()) {
            List<Method> getters = conflictingGetters.get(propName);
            Iterator<Method> iterator = getters.iterator();
            Method firstMethod = iterator.next();
            //正好只有一个 get 方法
            if (getters.size() == 1) {
                addGetMethod(propName, firstMethod);
            } else { // 若有多个 get 方法
                Method getter = firstMethod;
                Class<?> getterType = firstMethod.getReturnType(); //firstMethod 的返回类型
                while (iterator.hasNext()) {
                    Method method = iterator.next();
                    Class<?> methodType = method.getReturnType();  //下一个函数的 返回类型
                    if (methodType.equals(getterType)) {
                        throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property "
                                + propName + " in class " + firstMethod.getDeclaringClass()
                                + ".  This breaks the JavaBeans " + "specification and can cause unpredictable results.");
                    } else if (methodType.isAssignableFrom(getterType)) {
                        // 判定此 methodType 对象所表示的类或接口与指定的 getterType 参数所表示的类或接口是否相同，或是否是其超类或超接口。
                        // OK getter type is descendant
                        // getterType 是子类
                    } else if (getterType.isAssignableFrom(methodType)) {
                        // 若 methodType 是 getterType 的子类
                        getter = method;
                        getterType = methodType;
                    } else {
                        throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property "
                                + propName + " in class " + firstMethod.getDeclaringClass()
                                + ".  This breaks the JavaBeans " + "specification and can cause unpredictable results.");
                    }
                }
                addGetMethod(propName, getter);
            }
        }
    }

    /**
     * 添加 get 等
     * addGetMethod 方法注意和 addGetMethods 的区别
     * @param name
     * @param method
     */
    private void addGetMethod(String name, Method method) {
        // 校验属性名是否符合规范
        if (isValidPropertyName(name)) {
            // (方法名 , MethodInvoker)
            getMethods.put(name, new MethodInvoker(method));
            Type returnType = TypeParameterResolver.resolveReturnType(method, type);
            // (方法名 , 返回类型)
            getTypes.put(name, typeToClass(returnType));
        }
    }

    /**
     * 添加 set 方法
     * @param cls
     */
    private void addSetMethods(Class<?> cls) {
        Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
        Method[] methods = getClassMethods(cls);
        for (Method method : methods) {
            String name = method.getName();
            if (name.startsWith("set") && name.length() > 3) {
                if (method.getParameterTypes().length == 1) {
                    // 方法名 转 属性名
                    name = PropertyNamer.methodToProperty(name);
                    // 保证只有一个属性名 并向 conflictingSetters 这个 map 对象添加 键值对(name , List<Method>)
                    addMethodConflict(conflictingSetters, name, method);
                }
            }
        }
        // 处理冲突的 set 方法
        resolveSetterConflicts(conflictingSetters);
    }

    /**
     * 保证只有一个属性名 但由于可能会有重载的方法, 但方法不能丢
     * 去除相冲突的方法 (比如重载之类的)
     * 向 conflictingMethods 放入 ( 方法名 , List<Method> 方法 list)
     * @param conflictingMethods
     * @param name
     * @param method
     */
    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        //获取  get() 方法
        List<Method> list = conflictingMethods.get(name);
        if (list == null) {
            list = new ArrayList<Method>();
            conflictingMethods.put(name, list);
        }
        list.add(method); //注意此步不能省略 ,
    }

    /**
     * 处理冲突的 set 方法
     * @param conflictingSetters
     */
    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (String propName : conflictingSetters.keySet()) {
            // setters list 是一个 有序的 collection（也称为序列）
            List<Method> setters = conflictingSetters.get(propName);
            Class<?> getterType = getTypes.get(propName);
            Method match = null;
            ReflectionException exception = null;
            for (Method setter : setters) {
                Class<?> paramType = setter.getParameterTypes()[0];
                if (paramType.equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (exception == null) {
                    try {
                        match = pickBetterSetter(match, setter, propName);
                    } catch (ReflectionException e) {
                        // there could still be the 'best match'
                        match = null;
                        exception = e;
                    }
                }
            }
            if (match == null) {
                throw exception;
            } else {
                addSetMethod(propName, match);
            }
        }
    }

    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        } else if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
                + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
                + paramType2.getName() + "'.");
    }

    /**
     * 添加 set 等
     * setMethods setTypes 添加键值对
     * @param name
     * @param method
     */
    private void addSetMethod(String name, Method method) {
        if (isValidPropertyName(name)) {
            setMethods.put(name, new MethodInvoker(method));
            Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
            setTypes.put(name, typeToClass(paramTypes[0]));
        }
    }

    /**
     * 类型 type 强转 Class ???
     * @param src
     * @return
     */
    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) { // 参数化类型
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {  // 泛型数组类型
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {      // 类或接口类型
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance((Class<?>) componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    /**
     *
     * @param clazz
     */
    private void addFields(Class<?> clazz) {
        // 获取 声明的 field 字段
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (canAccessPrivateMethods()) {
                try {
                    field.setAccessible(true);// Accessible 设置为 TRUE 可以获取到私有属性
                } catch (Exception e) {
                    // Ignored. This is only a final precaution, nothing we can do.
                }
            }
            if (field.isAccessible()) {
                if (!setMethods.containsKey(field.getName())) {
                    // issue #379 - removed the check for final because JDK 1.5 allows
                    // modification of final fields through reflection (JSR-133). (JGB)
                    // pr #16 - final static can only be set by the classloader
                    // 用 int 值 代表 Java 语言修饰符  例如 (public = 1, private = 2 ....
                    int modifiers = field.getModifiers();
                    // 属性不是 final 和 static
                    if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                        //
                        addSetField(field);
                    }
                }
                if (!getMethods.containsKey(field.getName())) {
                    addGetField(field);
                }
            }
        }
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    /**
     * 添加 属性 等
     * @param field
     */
    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    /**
     * 校验属性名是否符合规范
     * 若属性名以 $ 开头 或 为serialVersionUID 或 为 class 则返回 FALSE
     * @param name
     * @return
     */
    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    /*
     * This method returns an array containing all methods
     * declared in this class and any superclass.
     * We use this method, instead of the simpler Class.getMethods(),
     * because we want to look for private methods as well.
     *
     * @param cls The class
     * @return An array containing all methods in this class
     */
    private Method[] getClassMethods(Class<?> cls) {
        Map<String, Method> uniqueMethods = new HashMap<String, Method>();
        Class<?> currentClass = cls;
        while (currentClass != null) {
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // we also need to look for interface methods -
            // because the class may be abstract
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            currentClass = currentClass.getSuperclass();
        }

        Collection<Method> methods = uniqueMethods.values();

        return methods.toArray(new Method[methods.size()]);
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            if (!currentMethod.isBridge()) {
                String signature = getSignature(currentMethod);
                // check to see if the method is already known
                // if it is known, then an extended class must have
                // overridden a method
                if (!uniqueMethods.containsKey(signature)) {
                    if (canAccessPrivateMethods()) {
                        try {
                            currentMethod.setAccessible(true);
                        } catch (Exception e) {
                            // Ignored. This is only a final precaution, nothing we can do.
                        }
                    }

                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }

    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append('#');
        }
        sb.append(method.getName());
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i == 0) {
                sb.append(':');
            } else {
                sb.append(',');
            }
            sb.append(parameters[i].getName());
        }
        return sb.toString();
    }

    /**
     * 检测有无 访问方法的权限
     * @return boolean
     */
    private static boolean canAccessPrivateMethods() {
        try {
            //安全管理器通过抛出异常来提供阻止操作完成的机会。如果允许执行该操作，则安全管理器例程只是简单地返回，
            //但如果不允许执行该操作，则抛出一个 SecurityException。
            //该约定的唯一例外是 checkTopLevelWindow，它返回 boolean 值。
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                // suppressAccessChecks 权限 能够访问类中的字段和调用方法。注意，这不仅包括
                // public、而且还包括 protected 和 private 字段和方法。
                // 若有权限则 执行完毕, 返回 TRUE 若无则报错 返回 FALSE
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /*
     * Gets the name of the class the instance provides information for
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /*
     * Gets the type for a property setter
     *
     * @param propertyName - the name of the property
     * @return The Class of the propery setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /*
     * Gets the type for a property getter
     *
     * @param propertyName - the name of the property
     * @return The Class of the propery getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /*
     * Gets an array of the readable properties for an object
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /*
     * Gets an array of the writeable properties for an object
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writeablePropertyNames;
    }

    /*
     * Check to see if a class has a writeable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writeable property by the name
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.keySet().contains(propertyName);
    }

    /*
     * Check to see if a class has a readable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.keySet().contains(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
}
