package qzui;

import com.google.common.base.Optional;
import org.flywaydb.core.Flyway;
import restx.server.JettyWebServer;
import restx.server.WebServer;

import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class can be used to run the app.
 * <p>
 * Alternatively, you can deploy the app as a war in a regular container like tomcat or jetty.
 * <p>
 * Reading the port from system env PORT makes it compatible with heroku.
 */
public class AppServer {
    public static final String WEB_INF_LOCATION = "/WEB-INF/web.xml";
    public static final String WEB_APP_LOCATION = ".";

    public static void main(String[] args) throws Exception {
        //java -cp WEB-INF/classes:"WEB-INF/lib/*":. qzui.AppServer
        String propertiesFile = getProperty("application.properties");
        if (null != propertiesFile) {
            System.getProperties().load(new FileInputStream(propertiesFile));
        }

        linkProperties();

        System.setProperty("restx.app.package", "qzui");
        /*
         * load mode from system property if defined, or default to dev
         * be careful with that setting, if you use this class to launch your server in production, make sure to launch
         * it with -Drestx.mode=prod or change the default here
         */
        System.setProperty("restx.mode", getProperty("restx.mode", "dev"));

        migrateDatabase();

        int port = Integer.valueOf(Optional.fromNullable(getProperty("restx.bind.port")).or("8080"));
        String webAppLocation = Optional.fromNullable(getProperty("restx.webapp")).or(WEB_APP_LOCATION);
        String webInfLocation = Optional.fromNullable(getProperty("restx.webapp.webinf")).or(webAppLocation + WEB_INF_LOCATION);
        String bindInterface = Optional.fromNullable(getProperty("restx.bind.ip")).or("0.0.0.0");
        WebServer server = new JettyWebServer(webInfLocation, webAppLocation, port, bindInterface);

        server.startAndAwait();
    }

    private static void migrateDatabase() {
        String url = Optional.fromNullable(getProperty("flyway.url")).or(getProperty("restx.datasource.url"));
        String user = Optional.fromNullable(getProperty("flyway.user")).or(getProperty("restx.datasource.username"));
        String password = Optional.fromNullable(getProperty("flyway.password")).or(getProperty("restx.datasource.password"));

        System.setProperty("flyway.url", url);
        System.setProperty("flyway.user", user);
        System.setProperty("flyway.password", password);

        Flyway flyway = new Flyway();
        flyway.configure(System.getProperties());
        flyway.getPlaceholders().put("tablePrefix", Optional.fromNullable(getProperty("org.quartz.jobStore.tablePrefix")).or("QRTZ_"));
        flyway.migrate();
    }

    private static String[] toArray(String s) {
        if (null == s) {
            return null;
        }
        return Arrays.asList(s.split("[,;]")).stream().map(s2 -> s2.trim()).toArray(String[]::new);
    }

    private static void linkProperties() {
        boolean anotherLoop = false;
        for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
            Object value = prop.getValue();
            if (null == value) {
                continue;
            }
            if (!value.toString().contains("${")) {
                continue;
            }
            Pattern pattern = Pattern.compile("\\$\\{(.+?)}");
            Matcher matcher = pattern.matcher(value.toString());
            List<String> keys = new LinkedList<>();
            while (matcher.find()) {
                String group = matcher.group(1);
                String v = getProperty(group);
                if (null != v && !pattern.matcher(v).find()) {
                    keys.add(group);
                } else if (null != v) {
                    anotherLoop = true;
                }
            }

            for (String key : keys) {
                String val = getProperty(key);
                System.setProperty(prop.getKey().toString(), value.toString().replace("${" + key + "}", val));
            }
        }
        if (anotherLoop) {
            linkProperties();
        }
    }

    public static String getProperty(String key, String def) {
        String v = getProperty(key);
        if (null != v) {
            return v;
        }

        return def;
    }

    private static String getProperty(String key) {
        String v = System.getProperty(key);
        if (null != v) {
            return v;
        }

        return System.getenv(key);
    }
}
