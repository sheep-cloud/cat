package net.dubboclub.catmonitor.registry;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.RegistryFactory;

import java.util.List;

import static net.dubboclub.catmonitor.constants.CatConstants.PROVIDER_APPLICATION_NAME;

/**
 * Cat 注册表工厂包装类
 *
 * @author bieber
 * @date 2015/11/12
 */
public class CatRegistryFactoryWrapper implements RegistryFactory {

    /**
     * 注册表工厂。 （SPI、单例、线程安全）
     */
    private RegistryFactory registryFactory;

    public CatRegistryFactoryWrapper(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    @Override
    public Registry getRegistry(URL url) {
        Registry registry = registryFactory.getRegistry(url);
        return new RegistryWrapper(registry);
    }

    /**
     * 注册表。 （SPI、原型、线程安全）
     */
    static class RegistryWrapper implements Registry {

        private Registry originRegistry;

        private URL appendProviderAppName(URL url) {
            String side = url.getParameter(Constants.SIDE_KEY);
            if (StrUtil.equals(Constants.PROVIDER_SIDE, side)) {
                url = url.addParameter(PROVIDER_APPLICATION_NAME, url.getParameter(Constants.APPLICATION_KEY));
            }

            return url;
        }

        public RegistryWrapper(Registry originRegistry) {
            this.originRegistry = originRegistry;
        }

        @Override
        public URL getUrl() {
            return originRegistry.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return originRegistry.isAvailable();
        }

        @Override
        public void destroy() {
            originRegistry.destroy();
        }

        @Override
        public void register(URL url) {
            originRegistry.register(appendProviderAppName(url));
        }

        @Override
        public void unregister(URL url) {
            originRegistry.unregister(appendProviderAppName(url));
        }

        @Override
        public void subscribe(URL url, NotifyListener listener) {
            originRegistry.subscribe(url, listener);
        }

        @Override
        public void unsubscribe(URL url, NotifyListener listener) {
            originRegistry.unsubscribe(url, listener);
        }

        @Override
        public List<URL> lookup(URL url) {
            return originRegistry.lookup(appendProviderAppName(url));
        }

    }

}
