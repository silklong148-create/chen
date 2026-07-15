package com.mysqladmin.service;

import com.mysqladmin.api.ApiException;
import com.mysqladmin.dto.ConnectionRequest;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConnectionService {
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

    public Map<String, Object> connect(String sessionId, ConnectionRequest request) {
        DatabaseType type = DatabaseType.from(request.databaseType());
        String host = request.host().trim();
        String url = switch (type) {
            case SQLSERVER -> "jdbc:sqlserver://" + host + ":" + request.port() + ";databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=5";
            case DAMENG -> "jdbc:dm://" + host + ":" + request.port();
            case MYSQL -> "jdbc:mysql://" + host + ":" + request.port() + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        };
        Profile candidate = new Profile(url, host, request.port(), request.username().trim(), request.password() == null ? "" : request.password(), type);
        try (Connection connection = create(candidate)) {
            var metadata = connection.getMetaData();
            profiles.put(sessionId, candidate);
            return Map.of("host", candidate.host(), "port", candidate.port(), "username", candidate.username(), "type", candidate.type().name(), "version", metadata.getDatabaseProductVersion());
        } catch (SQLException exception) {
            throw new ApiException("MySQL 连接失败：" + exception.getMessage());
        }
    }

    public Connection open(String sessionId) {
        Profile current = profiles.get(sessionId);
        if (current == null) throw new ApiException("请先建立 MySQL 连接");
        try { return create(current); } catch (SQLException exception) { throw new ApiException("MySQL 会话已失效，请重新连接：" + exception.getMessage()); }
    }

    public Connection openDatabase(String sessionId, String database) {
        Connection connection = open(sessionId);
        try { connection.setCatalog(database); return connection; } catch (SQLException exception) { try { connection.close(); } catch (SQLException ignored) { } throw new ApiException("切换数据库失败：" + exception.getMessage()); }
    }

    public Connection openSchema(String sessionId, String schema) {
        Connection connection = open(sessionId);
        try { connection.setSchema(schema); return connection; }
        catch (SQLException exception) { try { connection.close(); } catch (SQLException ignored) { } throw new ApiException("Unable to switch schema: " + exception.getMessage()); }
    }

    public DatabaseType type(String sessionId) {
        Profile current = profiles.get(sessionId); if (current == null) throw new ApiException("请先建立数据库连接"); return current.type();
    }

    public Map<String, Object> status(String sessionId) {
        Profile current = profiles.get(sessionId);
        return current == null ? Map.of("connected", false) : Map.of("connected", true, "host", current.host(), "port", current.port(), "username", current.username(), "type", current.type().name());
    }

    public void disconnect(String sessionId) { profiles.remove(sessionId); }

    private Connection create(Profile profile) throws SQLException {
        Properties properties = new Properties();
        properties.put("user", profile.username());
        properties.put("password", profile.password());
        properties.put("connectTimeout", "5000");
        properties.put("socketTimeout", "15000");
        return DriverManager.getConnection(profile.url(), properties);
    }

    private record Profile(String url, String host, int port, String username, String password, DatabaseType type) { }

    public enum DatabaseType { MYSQL, SQLSERVER, DAMENG;
        public static DatabaseType from(String value) { try { return value == null || value.isBlank() ? MYSQL : DatabaseType.valueOf(value.trim().toUpperCase()); } catch (IllegalArgumentException exception) { throw new ApiException("不支持的数据库类型：" + value); } }
    }
}
