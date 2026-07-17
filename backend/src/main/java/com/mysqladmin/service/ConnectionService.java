package com.mysqladmin.service;

import com.mysqladmin.api.ApiException;
import com.mysqladmin.dto.ConnectionRequest;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class ConnectionService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]+");
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

    public Map<String, Object> connect(String sessionId, ConnectionRequest request) {
        DatabaseType type = DatabaseType.from(request.databaseType());
        String host = request.host().trim();
        String databaseName = request.databaseName() == null || request.databaseName().isBlank()
                ? type.defaultDatabase() : request.databaseName().trim();
        String url = switch (type) {
            case SQLSERVER -> "jdbc:sqlserver://" + host + ":" + request.port() + ";databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=5";
            case DAMENG -> "jdbc:dm://" + host + ":" + request.port();
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + request.port() + "/" + databaseName + "?connectTimeout=5&socketTimeout=15";
            case ORACLE -> "jdbc:oracle:thin:@//" + host + ":" + request.port() + "/" + databaseName;
            case MYSQL -> "jdbc:mysql://" + host + ":" + request.port() + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        };
        Profile candidate = new Profile(url, host, request.port(), request.username().trim(),
                request.password() == null ? "" : request.password(), type, databaseName);
        try (Connection connection = create(candidate)) {
            var metadata = connection.getMetaData();
            profiles.put(sessionId, candidate);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("host", candidate.host());
            result.put("port", candidate.port());
            result.put("username", candidate.username());
            result.put("type", candidate.type().name());
            result.put("databaseName", candidate.databaseName());
            result.put("version", metadata.getDatabaseProductVersion());
            return result;
        } catch (SQLException exception) {
            throw new ApiException(type.displayName() + " 连接失败：" + exception.getMessage());
        }
    }

    public Connection open(String sessionId) {
        Profile current = profiles.get(sessionId);
        if (current == null) throw new ApiException("请先建立数据库连接");
        try { return create(current); }
        catch (SQLException exception) { throw new ApiException("数据库会话已失效，请重新连接：" + exception.getMessage()); }
    }

    public Connection openDatabase(String sessionId, String database) {
        Connection connection = open(sessionId);
        try { connection.setCatalog(database); return connection; }
        catch (SQLException exception) { close(connection); throw new ApiException("切换数据库失败：" + exception.getMessage()); }
    }

    public Connection openSchema(String sessionId, String schema) {
        Connection connection = open(sessionId);
        if (schema == null || !IDENTIFIER.matcher(schema).matches()) { close(connection); throw new ApiException("Schema 名称不合法：" + schema); }
        try {
            if (type(sessionId) == DatabaseType.ORACLE) {
                try (var statement = connection.createStatement()) { statement.execute("ALTER SESSION SET CURRENT_SCHEMA = \"" + schema + "\""); }
            } else connection.setSchema(schema);
            return connection;
        }
        catch (SQLException exception) { close(connection); throw new ApiException("切换 Schema 失败：" + exception.getMessage()); }
    }

    public DatabaseType type(String sessionId) {
        Profile current = profiles.get(sessionId);
        if (current == null) throw new ApiException("请先建立数据库连接");
        return current.type();
    }

    public Map<String, Object> status(String sessionId) {
        Profile current = profiles.get(sessionId);
        if (current == null) return Map.of("connected", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true); result.put("host", current.host()); result.put("port", current.port());
        result.put("username", current.username()); result.put("type", current.type().name());
        result.put("databaseName", current.databaseName());
        return result;
    }

    public void disconnect(String sessionId) { profiles.remove(sessionId); }

    private Connection create(Profile profile) throws SQLException {
        Properties properties = new Properties();
        properties.put("user", profile.username()); properties.put("password", profile.password());
        properties.put("connectTimeout", "5000"); properties.put("socketTimeout", "15000");
        return DriverManager.getConnection(profile.url(), properties);
    }

    private void close(Connection connection) { try { connection.close(); } catch (SQLException ignored) { } }

    private record Profile(String url, String host, int port, String username, String password,
                           DatabaseType type, String databaseName) { }

    public enum DatabaseType {
        MYSQL("MySQL", ""), SQLSERVER("SQL Server", "master"), DAMENG("达梦 DM8", ""),
        POSTGRESQL("PostgreSQL", "postgres"), ORACLE("Oracle", "FREEPDB1");
        private final String displayName;
        private final String defaultDatabase;
        DatabaseType(String displayName, String defaultDatabase) { this.displayName = displayName; this.defaultDatabase = defaultDatabase; }
        public String displayName() { return displayName; }
        public String defaultDatabase() { return defaultDatabase; }
        public static DatabaseType from(String value) {
            try { return value == null || value.isBlank() ? MYSQL : DatabaseType.valueOf(value.trim().toUpperCase()); }
            catch (IllegalArgumentException exception) { throw new ApiException("不支持的数据库类型：" + value); }
        }
    }
}
