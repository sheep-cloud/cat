package net.dubboclub.catmonitor.constants;

/**
 * Cat常量类
 *
 * @author bieber
 * @date 2015/11/12
 */
public class CatConstants {

    public static final String CROSS_CONSUMER = "PigeonCall";
    public static final String CROSS_SERVER = "PigeonService";

    /// -------------------------------------------------  服务提供方  ---------------------------------------------------
    public static final String PROVIDER_APPLICATION_NAME = "serverApplicationName";
    public static final String PROVIDER_CALL_SERVER = "PigeonService.client";
    public static final String PROVIDER_CALL_APP = "PigeonService.app";

    /// -------------------------------------------------  服务消费方  ---------------------------------------------------
    public static final String CONSUMER_CALL_SERVER = "PigeonCall.server";
    public static final String CONSUMER_CALL_APP = "PigeonCall.app";
    public static final String CONSUMER_CALL_PORT = "PigeonCall.port";

}
