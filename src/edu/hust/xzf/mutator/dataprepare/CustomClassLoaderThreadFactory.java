package edu.hust.xzf.mutator.dataprepare;

import java.util.concurrent.ThreadFactory;

public class CustomClassLoaderThreadFactory implements ThreadFactory {

    private ClassLoader customClassLoader;

    public CustomClassLoaderThreadFactory(ClassLoader customClassLoader) {
        this.customClassLoader = customClassLoader;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread newThread = new Thread(r);
        newThread.setDaemon(true);
        newThread.setContextClassLoader(customClassLoader());
        return newThread;
    }

    private ClassLoader customClassLoader() {
        return customClassLoader;
    }

}
