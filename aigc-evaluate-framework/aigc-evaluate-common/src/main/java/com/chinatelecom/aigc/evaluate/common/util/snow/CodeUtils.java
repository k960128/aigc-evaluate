package com.chinatelecom.aigc.evaluate.common.util.snow;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * The type Snowflake id utils.
 *
 */
@Component
public class CodeUtils {

    /**
     * 机器标识位数
     */
    private final static long WORKER_ID_BITS = 5L;
    /**
     * 数据中心标识位数
     */
    private final static long DATACENTER_ID_BITS = 5L;
    /**
     * 机器ID最大值
     */
    private final static long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    /**
     * 数据中心ID最大值
     */
    private final static long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    @Getter
    private static String localIpAddress;
    private static long WORKER_ID;
    /**
     * 数据标识id部分
     */
    private static long DATACENTER_ID;

    /**
     * Gets snow flake id.
     *
     * @return the snow flake id
     */
    public static long getSnowFlakeId() {
        Snowflake snowflake = IdUtil.getSnowflake(WORKER_ID, DATACENTER_ID);
        return snowflake.nextId();
    }

    /**
     * <p>
     * 获取 maxWorkerId
     * </p>
     */
    private static long generateMaxWorkerId() {
        StringBuilder mpid = new StringBuilder();
        mpid.append(DATACENTER_ID);
        String name = ManagementFactory.getRuntimeMXBean().getName();
        if (!name.isEmpty()) {
            /*
             * GET jvmPid
             */
            mpid.append(name.split("@")[0]);
        }
        /*
         * MAC + PID 的 hashcode 获取16个低位
         */
        return (mpid.toString().hashCode() & 0xffff) % (MAX_WORKER_ID + 1);
    }

    /**
     * <p>
     * 数据标识id部分
     * </p>
     */
    private static long generateDatacenterId() {
        long id;
        try {

            InetAddress ip = InetAddress.getByName(localIpAddress);
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network == null) {
                id = 1L;
            } else {
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 1])
                        | (0x0000FF00 & (((long) mac[mac.length - 2]) << 8))) >> 6;
                id = id % (MAX_DATACENTER_ID + 1);
            }
        } catch (Exception e) {
            return 1;
        }
        return id;
    }

    @Value("${localIpAddress:127.0.0.1}")
    public void setDatabase(String value) {
        localIpAddress = value;
        DATACENTER_ID = generateDatacenterId();
        WORKER_ID = generateMaxWorkerId();
    }

}
