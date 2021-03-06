package com.game;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import com.game.util.TimeUtil;
import com.server.util.Profile;
import com.server.util.ServerLogger;
import com.server.validate.AntiCheatService;
import com.server.validate.MixSupportor;

public class SysConfig {

    public static int port;// 端口号
    public static int serverId;// 服务端id
    public static int gmPort;// 后台服务端口
    public static int subLineCap;// 每个分线多少人
    public static int subLineCount;// 每个场景的分线人数
    public static String startUpDate;// 服务器开服时间 yyyy-MM-dd HH:mm:ss

    public static Date openDate;// 开服时间Date
    private static int openDays;// 开服时长

    public volatile static boolean gm;// 是否启用gm

    public static int serverThread;// ThreadService的线程数量
    public static int timerThread; // 系统定时器线程
    public static int loggerThread;// 日志记录线程
    public static String logpath;// 日志根目录
    public static int loggerBatchCount;// 批量插入日志量
    public static int disposeThread;// 清除缓存线程
    public static int delayDispose;// 下线多少秒清除缓存
    public static int scheduledThread;// 定时器线程
    public static boolean mangerService;// 后台管理service
    public static int maxCon;// 最大连接数
    public static String platform;// 平台名称
    public static String statlogpath;// 统计日志目录
    public static boolean debug;// 是否debug版本
    public static String dataPath;// data的目录

    public static boolean reg;// 开放注册
    public static boolean checkVersion;// 检查版本

    public static String mixIp;// 跨服IP
    public static int mixPort;// 跨服端口
    public static int cacheCount;//缓存的数量

    public static int gameId;
    public static int gameIdIOS;
    public static String eratingHost;
    public static int gatewayId;

    public static int sdkPort;
    public static String gatewayCode;
    public static String gatewayPwd;
    public static String currency;//币种
    public static boolean report;//开启上报

    public static String oauthkey;
    public static String host;
    public static String registerurl;
    public static String registerkey;
    public static String oauthsecret;
    public static String registersecret;
    public static String checktokenurl;
    public static String checktokenurlIos;

    public static String ingcleLogin;
    public static String channel;
    public static String gameKey;
    public static String gameKeyIOS;

    public static String gmServerUrl;

    public static int securePort;
    public static int sdkServerPort;

    public static void init() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(new File("config/sys.properties"))) {
            properties.load(fis);
            if (System.getProperty("port") != null) {
                port = Integer.parseInt(System.getProperty("port"));
            } else {
                port = Integer.parseInt(properties.getProperty("port"));
            }
            if (System.getProperty("serverId") != null) {
                serverId = Integer.parseInt(System.getProperty("serverId"));
            } else {
                serverId = Integer.parseInt(properties.getProperty("serverId"));
            }
            subLineCap = Integer.parseInt(properties.getProperty("subLineCap"));
            subLineCount = Integer.parseInt(properties.getProperty("subLineCount"));
            gm = Boolean.valueOf(properties.getProperty("gm"));

            serverThread = Integer.parseInt(properties.getProperty("serverThread"));
            timerThread = Integer.parseInt(properties.getProperty("timerThread"));
            disposeThread = Integer.parseInt(properties.getProperty("disposeThread"));
            delayDispose = Integer.parseInt(properties.getProperty("delayDispose"));
            loggerThread = Integer.parseInt(properties.getProperty("loggerThread"));
            loggerBatchCount = Integer.parseInt(properties.getProperty("loggerBatchCount"));
            scheduledThread = Integer.parseInt(properties.getProperty("scheduledThread"));

            logpath = properties.getProperty("logpath");
            System.setProperty("logpath", logpath);
            dataPath = properties.getProperty("dataPath");
            System.setProperty("dataPath", dataPath);
            startUpDate = properties.getProperty("startUpDate");

            mangerService = Boolean.parseBoolean(properties.getProperty("mangerService"));
            maxCon = Integer.parseInt(properties.getProperty("maxCon"));

            gmPort = Integer.parseInt(properties.getProperty("gmport"));
            platform = properties.getProperty("platform");
            statlogpath = properties.getProperty("statlogpath");

            debug = Boolean.parseBoolean(properties.getProperty("debug"));

            reg = Boolean.parseBoolean(properties.getProperty("reg"));
            checkVersion = Boolean.parseBoolean(properties.getProperty("checkVersion"));

            cacheCount = Integer.parseInt(properties.getProperty("cacheCount"));

            gameId = Integer.parseInt(properties.getProperty("game_id"));
            gameIdIOS = Integer.parseInt(properties.getProperty("game_id_ios"));
            gatewayId = Integer.parseInt(properties.getProperty("game_way_id"));
            sdkPort = Integer.parseInt(properties.getProperty("sdk_port"));
            eratingHost = properties.getProperty("sdk_host");
            gatewayCode = properties.getProperty("gateway_code");
            gatewayPwd = properties.getProperty("gateway_pwd");

            currency = properties.getProperty("currency");
            report = Boolean.parseBoolean(properties.getProperty("report"));

            oauthkey = properties.getProperty("oauthkey");
            host = properties.getProperty("host");
            registerurl = properties.getProperty("registerurl");
            registerkey = properties.getProperty("registerkey");
            registersecret = properties.getProperty("registersecret");
            oauthsecret = properties.getProperty("oauthsecret");
            checktokenurl = properties.getProperty("checktokenurl");
            checktokenurlIos = properties.getProperty("checktokenurl_ios");

            ingcleLogin = properties.getProperty("checktokenurl");
            channel = properties.getProperty("channel");
            gameKey = properties.getProperty("game_key");
            gameKeyIOS = properties.getProperty("game_key_ios");

            gmServerUrl = properties.getProperty("gm_server_url");

            sdkServerPort = Integer.parseInt(properties.getProperty("sdkServerPort"));


            Profile.setOpen(Boolean.parseBoolean(properties.getProperty("profile")));

            AntiCheatService.getInstance().setSafeCmd(properties.getProperty("safeMod"));
            AntiCheatService.getInstance().setNoSerialNumCmd(properties.getProperty("noSerialNum"));
            AntiCheatService.getInstance().setValidate(Boolean.valueOf(properties.getProperty("validate")));


            SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            openDate = dataFormat.parse(startUpDate);

            updateOpenDays();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (FileInputStream fis = new FileInputStream(new File("config/mix.properties"))) {
            // 加载跨服设置
            properties = new Properties();
            properties.load(fis);

            mixIp = properties.getProperty("mixIp");
            mixPort = Integer.parseInt(properties.getProperty("mixPort"));

            MixSupportor.setIncludeSection(properties.getProperty("includeSection"));
            MixSupportor.setExcludeCmd(properties.getProperty("excludeCmd"));
            MixSupportor.setShiftSwitch(Boolean.parseBoolean(properties.getProperty("open")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 开服多少天
    public static int getOpenDays() {
        return openDays;
    }

    public static void updateOpenDays() {
        Calendar open = Calendar.getInstance();
        ServerLogger.warn("========" + openDate);
        open.setTime(openDate);
        open.set(Calendar.HOUR_OF_DAY, 0);
        open.set(Calendar.MINUTE, 0);
        open.set(Calendar.SECOND, 0);

        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);

        long diff = (now.getTimeInMillis() - open.getTimeInMillis()) / 1000;
        openDays = (int) (diff / (TimeUtil.ONE_HOUR * 24 / 1000)) + 1;

    }

    public static boolean isJapan() {
        return platform.contains("japan");
    }

}
