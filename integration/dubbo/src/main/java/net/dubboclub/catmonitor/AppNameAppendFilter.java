package net.dubboclub.catmonitor;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.*;

/**
 * AppName. 过滤器实现
 *
 * @author bieber
 * @date 2015/11/12
 */
@Activate(group = {Constants.CONSUMER})
public class AppNameAppendFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext rpcContext = RpcContext.getContext();
        URL url = invoker.getUrl();
        rpcContext.setAttachment(Constants.APPLICATION_KEY, url.getParameter(Constants.APPLICATION_KEY));
        return invoker.invoke(invocation);
    }

}
