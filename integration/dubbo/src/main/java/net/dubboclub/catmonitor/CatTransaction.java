package net.dubboclub.catmonitor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.dubbo.rpc.*;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import com.dianping.cat.Cat;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Message;
import com.dianping.cat.message.Transaction;

import java.util.HashMap;
import java.util.Map;

import static net.dubboclub.catmonitor.constants.CatConstants.*;

/**
 * CatTransaction
 *
 * @author bieber
 * @date 2015/11/4
 */
@Activate(group = {Constants.PROVIDER, Constants.CONSUMER}, order = -9000)
public class CatTransaction implements Filter {

    /** Dubbo 业务异常 */
    private final static String DUBBO_BIZ_ERROR = "DUBBO_BIZ_ERROR";

    /** Dubbo 超时异常 */
    private final static String DUBBO_TIMEOUT_ERROR = "DUBBO_TIMEOUT_ERROR";

    /** Dubbo 远程处理异常 */
    private final static String DUBBO_REMOTING_ERROR = "DUBBO_REMOTING_ERROR";

    private static final ThreadLocal<Cat.Context> CAT_CONTEXT = new ThreadLocal<Cat.Context>();

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (!DubboCat.isEnable()) {
            return invoker.invoke(invocation);
        }

        URL url = invoker.getUrl();
        String sideKey = url.getParameter(Constants.SIDE_KEY);
        // 例: HelloService.hello
        String loggerName = invoker.getInterface()
                                   .getSimpleName() + "." + invocation.getMethodName();
        String type = CROSS_CONSUMER;
        if (Constants.PROVIDER_SIDE.equals(sideKey)) {
            type = CROSS_SERVER;
        }

        boolean init = false;
        Transaction transaction = null;

        try {
            transaction = Cat.newTransaction(type, loggerName);
            Cat.Context context = getContext();
            if (Constants.CONSUMER_SIDE.equals(sideKey)) {
                createConsumerCross(url, transaction);
                Cat.logRemoteCallClient(context);
            } else {
                createProviderCross(url, transaction);
                Cat.logRemoteCallServer(context);
            }
            setAttachment(context);
            init = true;
        } catch (Exception e) {
            Cat.logError("[DUBBO] DubboCat init error.", e);
        }

        Result result = null;
        try {
            result = invoker.invoke(invocation);
            if (!init) {
                return result;
            }

            boolean isAsync = RpcUtils.isAsync(invoker.getUrl(), invocation);
            // 异步的不能判断是否有异常,这样会阻塞住接口(<AsyncRpcResult>hasException->getRpcResult->resultFuture.get()
            if (isAsync) {
                transaction.setStatus(Message.SUCCESS);
                return result;
            }

            if (result.hasException()) {
                // 给调用接口出现异常进行打点
                Throwable throwable = result.getException();
                Event event;
                if (RpcException.class == throwable.getClass()) {
                    Throwable caseBy = throwable.getCause();
                    if (caseBy != null && caseBy.getClass() == TimeoutException.class) {
                        event = Cat.newEvent(DUBBO_TIMEOUT_ERROR, loggerName);
                    } else {
                        event = Cat.newEvent(DUBBO_REMOTING_ERROR, loggerName);
                    }
                } else if (RemotingException.class.isAssignableFrom(throwable.getClass())) {
                    event = Cat.newEvent(DUBBO_REMOTING_ERROR, loggerName);
                } else {
                    event = Cat.newEvent(DUBBO_BIZ_ERROR, loggerName);
                }

                event.setStatus(result.getException());
                event.complete();

                transaction.addChild(event);
                transaction.setStatus(result.getException()
                                            .getClass()
                                            .getSimpleName());
            } else {
                transaction.setStatus(Message.SUCCESS);
            }
            return result;
        } catch (RuntimeException e) {
            if (init) {
                Cat.logError(e);
                Event event;
                if (RpcException.class == e.getClass()) {
                    Throwable caseBy = e.getCause();
                    if (caseBy != null && caseBy.getClass() == TimeoutException.class) {
                        event = Cat.newEvent(DUBBO_TIMEOUT_ERROR, loggerName);
                    } else {
                        event = Cat.newEvent(DUBBO_REMOTING_ERROR, loggerName);
                    }
                } else {
                    event = Cat.newEvent(DUBBO_BIZ_ERROR, loggerName);
                }

                event.setStatus(e);
                event.complete();

                transaction.addChild(event);
                transaction.setStatus(e.getClass()
                                       .getSimpleName());
            }
            if (result == null) {
                throw e;
            } else {
                return result;
            }
        } finally {
            if (transaction != null) {
                transaction.complete();
            }
            CAT_CONTEXT.remove();
        }
    }

    static class DubboCatContext implements Cat.Context {

        private Map<String, String> properties = new HashMap<String, String>();

        @Override
        public void addProperty(String key, String value) {
            properties.put(key, value);
        }

        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }
    }

    private String getProviderAppName(URL url) {
        String appName = url.getParameter(PROVIDER_APPLICATION_NAME);
        if (StrUtil.isEmpty(appName)) {
            String interfaceName = url.getParameter(Constants.INTERFACE_KEY);
            appName = interfaceName.substring(0, interfaceName.lastIndexOf('.'));
        }

        return appName;
    }

    private void setAttachment(Cat.Context context) {
        RpcContext.getContext()
                  .setAttachment(Cat.Context.ROOT, context.getProperty(Cat.Context.ROOT))
                  .setAttachment(Cat.Context.CHILD, context.getProperty(Cat.Context.CHILD))
                  .setAttachment(Cat.Context.PARENT, context.getProperty(Cat.Context.PARENT));
    }

    private Cat.Context getContext() {
        Cat.Context context = CAT_CONTEXT.get();
        if (context == null) {
            context = initContext();
            CAT_CONTEXT.set(context);
        }
        return context;
    }

    private Cat.Context initContext() {
        Cat.Context context = new DubboCatContext();
        RpcContext rpcContext = RpcContext.getContext();
        Map<String, String> attachments = rpcContext.getAttachments();
        if (MapUtil.isNotEmpty(attachments)) {
            for (Map.Entry<String, String> entry : attachments.entrySet()) {
                String key = entry.getKey();
                if (StrUtil.equalsAny(key, Cat.Context.CHILD, Cat.Context.ROOT, Cat.Context.PARENT)) {
                    context.addProperty(key, entry.getValue());
                }
            }
        }

        return context;
    }

    private void createConsumerCross(URL url, Transaction transaction) {
        Event crossAppEvent = Cat.newEvent(CONSUMER_CALL_APP, getProviderAppName(url));
        Event crossServerEvent = Cat.newEvent(CONSUMER_CALL_SERVER, url.getHost());
        Event crossPortEvent = Cat.newEvent(CONSUMER_CALL_PORT, Convert.toStr(url.getPort()));

        crossAppEvent.setStatus(Event.SUCCESS);
        crossServerEvent.setStatus(Event.SUCCESS);
        crossPortEvent.setStatus(Event.SUCCESS);

        crossAppEvent.complete();
        crossPortEvent.complete();
        crossServerEvent.complete();

        transaction.addChild(crossAppEvent);
        transaction.addChild(crossPortEvent);
        transaction.addChild(crossServerEvent);
    }

    private void createProviderCross(URL url, Transaction transaction) {
        RpcContext rpcContext = RpcContext.getContext();
        String consumerAppName = StrUtil.emptyToDefault(rpcContext.getAttachment(Constants.APPLICATION_KEY), rpcContext.getRemoteHost() + ":" + rpcContext.getRemotePort());

        Event crossAppEvent = Cat.newEvent(PROVIDER_CALL_APP, consumerAppName);
        Event crossServerEvent = Cat.newEvent(PROVIDER_CALL_SERVER, rpcContext.getRemoteHost());

        crossAppEvent.setStatus(Event.SUCCESS);
        crossServerEvent.setStatus(Event.SUCCESS);

        crossAppEvent.complete();
        crossServerEvent.complete();

        transaction.addChild(crossAppEvent);
        transaction.addChild(crossServerEvent);
    }

}
