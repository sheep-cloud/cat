package net.dubboclub.catmonitor;

import com.dianping.cat.Cat;
import com.dianping.cat.message.spi.MessageManager;

/**
 * DubboCat
 *
 * @author bieber
 * @date 2015/11/16
 */
public class DubboCat {

    private static boolean isEnable = true;

    /**
     * 禁用dubbo cat
     */
    public static void disable() {
        isEnable = false;
    }

    /**
     * 启用dubbo cat
     */
    public static void enable() {
        isEnable = true;
    }

    /**
     * 是否有效
     *
     * @return
     */
    public static boolean isEnable() {
        boolean isCatEnabled = false;
        try {
            MessageManager manager = Cat.getManager();
            // 检查 CAT 日志记录是启用还是禁用。
            isCatEnabled = manager.isCatEnabled();
        } catch (Throwable e) {
            Cat.logError("[DUBBO] Cat init error.", e);
        }

        return isCatEnabled && isEnable;
    }

}
