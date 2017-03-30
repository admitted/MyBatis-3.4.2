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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * A class to wrap access to multiple class loaders making them work as one
 * 多种类加载器的包装类 , (使用此包装类就可使用多种类加载器 , 方便!!!)
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

    ClassLoader defaultClassLoader;   // 默认类加载器: null ???
    ClassLoader systemClassLoader;    // 我们自己写的类一般都是由systemClassloader加载的

    /**
     *  构造器
     */
    ClassLoaderWrapper() {
        try {
            //返回委托的系统类加载器
            systemClassLoader = ClassLoader.getSystemClassLoader();
        } catch (SecurityException ignored) {
            // AccessControlException on Google App Engine
            // 忽略错误 :
        }
    }

    /**
     * Get a resource as a URL using the current class path
     * 在当前类路径下查找资源
     * @param resource - the resource to locate
     * @return the resource or null
     */
    public URL getResourceAsURL(String resource) {
        return getResourceAsURL(resource, getClassLoaders(null));
    }

    /**
     * Get a resource from the classpath, starting with a specific class loader
     * 通过指定类加载器 在当前类路径下查找资源
     * @param resource    - the resource to find
     * @param classLoader - the first classloader to try
     * @return the stream or null
     */
    public URL getResourceAsURL(String resource, ClassLoader classLoader) {
        return getResourceAsURL(resource, getClassLoaders(classLoader));
    }

    /**
     * Get a resource from the classpath
     * 在当前类路径下查找资源并返回资源的 字节流
     * @param resource - the resource to find
     * @return the stream or null
     */
    public InputStream getResourceAsStream(String resource) {
        return getResourceAsStream(resource, getClassLoaders(null));
    }

    /**
     * Get a resource from the classpath, starting with a specific class loader
     * 通过指定类加载器 在当前类路径下查找资源,并返回字节流
     * @param resource    - the resource to find
     * @param classLoader - the first class loader to try
     * @return the stream or null
     */
    public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
        return getResourceAsStream(resource, getClassLoaders(classLoader));
    }

    /**
     * Find a class on the classpath (or die trying)
     * 在当前项目的classpath的相对路径来查找资源。
     * @param name - the class to look for
     * @return - the class
     * @throws ClassNotFoundException Duh.
     */
    public Class<?> classForName(String name) throws ClassNotFoundException {
        return classForName(name, getClassLoaders(null));
    }

    /**
     * Find a class on the classpath, starting with a specific classloader (or die trying)
     * 在指定类路径和指定类加载器下查找 Class
     * @param name        - the class to look for
     * @param classLoader - the first classloader to try
     * @return - the class
     * @throws ClassNotFoundException Duh.
     */
    public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
        return classForName(name, getClassLoaders(classLoader));
    }

    /**
     * Try to get a resource from a group of classloaders
     * 尝试得到给定 resource 的 字节码流
     * @param resource    - the resource to get
     * @param classLoader - the classloaders to examine
     * @return the resource or null
     */
    InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
        for (ClassLoader cl : classLoader) {
            if (null != cl) {

                // try to find the resource as passed
                // 尝试先在相对路径下加载此资源的 字节码流
                InputStream returnValue = cl.getResourceAsStream(resource);

                // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
                // 若返回 null 则在根路径下查找
                if (null == returnValue) {
                    returnValue = cl.getResourceAsStream("/" + resource);
                }

                if (null != returnValue) {
                    return returnValue;
                }
            }
        }
        return null;
    }

    /**
     * Get a resource as a URL using the current class path
     * 查找所有给定名称的资源 URL
     * (尝试 从一组类加载器中 取出一个符合要求的类加载器  来得到resource
     * 若一个都没有获得 则返回 null)
     * @param resource    - the resource to locate 资源的定位路径
     * @param classLoader - the class loaders to examine
     * @return the resource or null
     */
    URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

        URL url;

        for (ClassLoader cl : classLoader) {

            if (null != cl) {

                // look for the resource as passed in...
                url = cl.getResource(resource);

                // ...but some class loaders want this leading "/", so we'll add it
                // and try again if we didn't find the resource
                // 如果没有发现 则从根目录下查找
                if (null == url) {
                    url = cl.getResource("/" + resource);
                }

                // "It's always in the last place I look for it!"
                // ... because only an idiot would keep looking for it after finding it, so stop looking already.
                if (null != url) {
                    return url;
                }

            }

        }
        // didn't find it anywhere.
        // 哪也找不到,返回 null
        return null;

    }

    /**
     * Attempt to load a class from a group of classloaders
     *
     * 尝试 从一组类加载器中 取出一个符合要求的加载器 去加载一个 class
     * 若没有一个加载器加载成功 则抛出 ClassNotFoundException 异常
     * @param name        - the class to load
     * @param classLoader - the group of classloaders to examine
     * @return the class
     * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
     */
    Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {

        for (ClassLoader cl : classLoader) {

            if (null != cl) {

                try {

                    // 大名鼎鼎的Class.forName 内部 调用此方法 Class.forName(name, true, cl)
                    Class<?> c = Class.forName(name, true, cl);

                    if (null != c) {
                        return c;
                    }

                } catch (ClassNotFoundException e) {
                    // we'll ignore this until all classloaders fail to locate the class
                    // 暂时忽略异常,直到所有的类加载器都没有找到
                }

            }

        }

        throw new ClassNotFoundException("Cannot find class: " + name);

    }

    /**
     * 获得一组 类加载器
     * 搞清楚这一组类加载器都是什么东西
     * @param classLoader
     * @return
     */
    ClassLoader[] getClassLoaders(ClassLoader classLoader) {
        return new ClassLoader[]{
                // 传入的类加载器
                classLoader,
                // 为null : 默认类加载器 Bootstrap ClassLoad ;
                defaultClassLoader,
                // 当前线程上下文下类加载器 若创建线程时未设置 ,则从父线程中继承一个 , 若在应用程序范围内都没有设置
                // 那这个类加载器就是 应用程序类加载器  sun.misc.Launcher$AppClassLoader
                Thread.currentThread().getContextClassLoader(),
                // 返回 加载 当前对象类的 类加载器
                getClass().getClassLoader(),
                // sun.misc.Launcher$AppClassLoader 应用程序类加载器 负责加载用户类路径下的 类库
                systemClassLoader};
    }

}
