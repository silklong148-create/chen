package com.mysqladmin.service;

import com.mysqladmin.api.ApiException;
import com.mysqladmin.dto.ColumnDefinition;
import com.mysqladmin.dto.DatabaseRequest;
import com.mysqladmin.dto.TableRequest;
import com.mysqladmin.dto.TableUpdateRequest;
import com.mysqladmin.dto.ViewRequest;
import com.mysqladmin.dto.IndexRequest;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MysqlAdminService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]+");
    private static final Pattern LENGTH = Pattern.compile("\\d{1,5}(,\\d{1,5})?");
    private static final Pattern MYSQL_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern MYSQL57_SIMPLE_CTE = Pattern.compile("(?is)^WITH\\s+([A-Za-z][A-Za-z0-9_$]*)\\s+AS\\s*\\((.+)\\)\\s*(SELECT\\s+.+)$");
    private static final Set<String> TYPES = Set.of("tinyint", "smallint", "mediumint", "int", "integer", "bigint", "decimal", "numeric", "float", "double", "char", "varchar", "text", "tinytext", "mediumtext", "longtext", "date", "datetime", "timestamp", "time", "boolean", "json");
    private final ConnectionService connections;

    public MysqlAdminService(ConnectionService connections) { this.connections = connections; }

    public List<Map<String, Object>> databases(String sessionId) {
        if (isSqlServer(sessionId)) return sqlServerDatabases(sessionId);
        String sql = "SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME";
        try (Connection connection = connections.open(sessionId); PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString(1);
                result.add(Map.of("name", name, "charset", rs.getString(2), "collation", rs.getString(3), "system", isSystemSchema(name)));
            }
            return result;
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public void createDatabase(String sessionId, DatabaseRequest request) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, null, "CREATE DATABASE " + sqlId(request.name())); return; } execute(sessionId, "CREATE DATABASE " + id(request.name()) + charset(request)); }
    public void updateDatabase(String sessionId, String name, DatabaseRequest request) { if (isSqlServer(sessionId)) { sqlServerUpdateDatabase(sessionId, name, request); return; } execute(sessionId, "ALTER DATABASE " + id(name) + charset(request)); }
    public void deleteDatabase(String sessionId, String name) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, null, "ALTER DATABASE " + sqlId(name) + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE " + sqlId(name)); return; } execute(sessionId, "DROP DATABASE " + id(name)); }

    public List<Map<String, Object>> tables(String sessionId, String database) {
        if (isSqlServer(sessionId)) return sqlServerTables(sessionId, database);
        id(database);
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, ENGINE, TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        try (Connection connection = connections.open(sessionId); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", rs.getString("TABLE_NAME")); item.put("comment", rs.getString("TABLE_COMMENT"));
                    item.put("rows", rs.getLong("TABLE_ROWS")); item.put("engine", rs.getString("ENGINE")); item.put("type", rs.getString("TABLE_TYPE")); result.add(item);
                }
                return result;
            }
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public void createTable(String sessionId, String database, TableRequest request) {
        if (isSqlServer(sessionId)) { sqlServerCreateTable(sessionId, database, request); return; }
        String definitions = request.columns().stream().map(column -> columnSql(sessionId, column)).reduce((a, b) -> a + ", " + b).orElseThrow();
        execute(sessionId, "CREATE TABLE " + id(database) + "." + id(request.name()) + " (" + definitions + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=" + literal(request.comment()));
    }

    public void updateTable(String sessionId, String database, String table, TableUpdateRequest request) {
        if (isSqlServer(sessionId)) { sqlServerUpdateTable(sessionId, database, table, request); return; }
        String target = table;
        if (request.newName() != null && !request.newName().isBlank() && !request.newName().equals(table)) {
            execute(sessionId, "RENAME TABLE " + id(database) + "." + id(table) + " TO " + id(database) + "." + id(request.newName()));
            target = request.newName();
        }
        if (request.comment() != null) execute(sessionId, "ALTER TABLE " + id(database) + "." + id(target) + " COMMENT = " + literal(request.comment()));
    }

    public void deleteTable(String sessionId, String database, String table) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, database, "DROP TABLE " + sqlTable(table)); return; } execute(sessionId, "DROP TABLE " + id(database) + "." + id(table)); }
    public void addColumn(String sessionId, String database, String table, ColumnDefinition column) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, database, "ALTER TABLE " + sqlTable(table) + " ADD " + sqlServerColumnSql(column)); return; } execute(sessionId, "ALTER TABLE " + id(database) + "." + id(table) + " ADD COLUMN " + columnSql(sessionId, column)); }
    public void updateColumn(String sessionId, String database, String table, String column, ColumnDefinition definition) { if (isSqlServer(sessionId)) { sqlServerUpdateColumn(sessionId, database, table, column, definition); return; } execute(sessionId, "ALTER TABLE " + id(database) + "." + id(table) + " CHANGE COLUMN " + id(column) + " " + columnSql(sessionId, definition)); }
    public void dropColumn(String sessionId, String database, String table, String column) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, database, "ALTER TABLE " + sqlTable(table) + " DROP COLUMN " + sqlId(column)); return; } execute(sessionId, "ALTER TABLE " + id(database) + "." + id(table) + " DROP COLUMN " + id(column)); }

    public List<Map<String, Object>> structure(String sessionId, String database, String table) {
        if (isSqlServer(sessionId)) return sqlServerStructure(sessionId, database, table);
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (Connection connection = connections.open(sessionId); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, database); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", rs.getString(1)); item.put("type", rs.getString(2)); item.put("nullable", rs.getString(3)); item.put("key", rs.getString(4)); item.put("defaultValue", rs.getString(5)); item.put("extra", rs.getString(6)); item.put("comment", rs.getString(7)); result.add(item);
                }
                return result;
            }
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public List<Map<String, Object>> rows(String sessionId, String database, String table, int limit, int offset) { return rows(sessionId, database, table, limit, offset, null, null, null, "ASC"); }

    public List<Map<String, Object>> rows(String sessionId, String database, String table, int limit, int offset, String keyword, String filterColumn, String sortBy, String sortDirection) {
        if (isSqlServer(sessionId)) return sqlServerRows(sessionId, database, table, limit, offset, keyword, filterColumn, sortBy, sortDirection);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        List<String> columns = queryColumns(sessionId, database, table);
        String targetColumn = checkedQueryColumn(filterColumn, columns, false);
        String orderColumn = checkedQueryColumn(sortBy, columns, true);
        String direction = checkedDirection(sortDirection);
        String where = keyword == null || keyword.isBlank() ? "" : " WHERE " + searchClause(columns, targetColumn, false);
        String sql = "SELECT * FROM " + id(database) + "." + id(table) + where + " ORDER BY " + id(orderColumn) + " " + direction + " LIMIT " + safeOffset + ", " + safeLimit;
        try (Connection connection = connections.open(sessionId); PreparedStatement ps = connection.prepareStatement(sql)) {
            if (keyword != null && !keyword.isBlank()) bindSearch(ps, keyword, targetColumn == null ? columns.size() : 1);
            try (ResultSet rs = ps.executeQuery()) { return resultRows(rs); }
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public void createRow(String sessionId, String database, String table, Map<String, Object> values) {
        Map<String, Object> safeValues = safeValues(values); String columns = safeValues.keySet().stream().map(column -> columnId(sessionId, column)).reduce((a,b)->a+", "+b).orElseThrow(); String placeholders = safeValues.keySet().stream().map(key -> "?").reduce((a,b)->a+", "+b).orElseThrow();
        String sql = "INSERT INTO " + qualifiedTable(sessionId, database, table) + " (" + columns + ") VALUES (" + placeholders + ")";
        try (Connection connection = dataConnection(sessionId, database); PreparedStatement ps = connection.prepareStatement(sql)) { bind(ps, safeValues.values()); ps.executeUpdate(); } catch (SQLException exception) { throw dataError("新增记录失败", exception); }
    }

    public void updateRow(String sessionId, String database, String table, Map<String, Object> key, Map<String, Object> values) {
        Map<String, Object> safeKey = requirePrimaryKey(sessionId, database, table, key); Map<String, Object> safeValues = safeValues(values);
        for (String primary : safeKey.keySet()) if (safeValues.containsKey(primary)) throw new ApiException("为保证记录定位安全，不能直接修改主键字段：" + primary);
        String sets = safeValues.keySet().stream().map(column -> columnId(sessionId, column) + " = ?").reduce((a,b)->a+", "+b).orElseThrow();
        String where = whereClause(sessionId, safeKey.keySet()); String sql = "UPDATE " + qualifiedTable(sessionId, database, table) + " SET " + sets + " WHERE " + where;
        try (Connection connection = dataConnection(sessionId, database); PreparedStatement ps = connection.prepareStatement(sql)) { List<Object> params = new ArrayList<>(safeValues.values()); params.addAll(safeKey.values()); bind(ps, params); if (ps.executeUpdate() == 0) throw new ApiException("未找到要更新的记录，它可能已被其他操作删除"); } catch (SQLException exception) { throw dataError("更新记录失败", exception); }
    }

    public void deleteRow(String sessionId, String database, String table, Map<String, Object> key) {
        Map<String, Object> safeKey = requirePrimaryKey(sessionId, database, table, key); String sql = "DELETE FROM " + qualifiedTable(sessionId, database, table) + " WHERE " + whereClause(sessionId, safeKey.keySet());
        try (Connection connection = dataConnection(sessionId, database); PreparedStatement ps = connection.prepareStatement(sql)) { bind(ps, safeKey.values()); if (ps.executeUpdate() == 0) throw new ApiException("未找到要删除的记录，它可能已被其他操作删除"); } catch (SQLException exception) { throw dataError("无法删除该记录", exception); }
    }

    public List<Map<String, Object>> views(String sessionId, String database) {
        if (isSqlServer(sessionId)) return sqlServerViews(sessionId, database);
        String sql = "SELECT TABLE_NAME, CHECK_OPTION, IS_UPDATABLE, DEFINER FROM information_schema.VIEWS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
        try (Connection connection = connections.open(sessionId); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>(); item.put("name", rs.getString(1)); item.put("checkOption", rs.getString(2)); item.put("updatable", rs.getString(3)); item.put("definer", rs.getString(4)); result.add(item);
                }
                return result;
            }
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public Map<String, Object> view(String sessionId, String database, String view) {
        if (isSqlServer(sessionId)) return sqlServerView(sessionId, database, view);
        String sql = "SHOW CREATE VIEW " + id(database) + "." + id(view);
        try (Connection connection = connections.open(sessionId); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) throw new ApiException("视图不存在：" + view);
            return Map.of("name", view, "definition", rs.getString("Create View"));
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public List<Map<String, Object>> viewRows(String sessionId, String database, String view, int limit) { return rows(sessionId, database, view, limit, 0); }
    public void createView(String sessionId, String database, ViewRequest request) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, database, "CREATE VIEW " + sqlTable(request.name()) + " AS " + selectSql(sessionId, request.selectSql())); return; } executeInDatabase(sessionId, database, "CREATE VIEW " + id(database) + "." + id(request.name()) + " AS " + selectSql(sessionId, request.selectSql())); }
    public void updateView(String sessionId, String database, String view, ViewRequest request) {
        if (isSqlServer(sessionId)) { sqlServerUpdateView(sessionId, database, view, request); return; }
        String target = request.name().equals(view) ? view : request.name();
        executeInDatabase(sessionId, database, "CREATE OR REPLACE VIEW " + id(database) + "." + id(target) + " AS " + selectSql(sessionId, request.selectSql()));
        if (!target.equals(view)) execute(sessionId, "DROP VIEW " + id(database) + "." + id(view));
    }
    public void deleteView(String sessionId, String database, String view) { if (isSqlServer(sessionId)) { sqlExecute(sessionId, database, "DROP VIEW " + sqlTable(view)); return; } execute(sessionId, "DROP VIEW " + id(database) + "." + id(view)); }

    public List<Map<String, Object>> indexes(String sessionId, String database, String table) {
        if (isSqlServer(sessionId)) return sqlServerIndexes(sessionId, database, table);
        String sql = "SHOW INDEX FROM " + id(database) + "." + id(table);
        try (Connection connection = connections.open(sessionId); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
            while (rs.next()) {
                String name = rs.getString("Key_name");
                Map<String, Object> item = grouped.computeIfAbsent(name, ignored -> { Map<String, Object> index = new LinkedHashMap<>(); index.put("name", name); index.put("unique", rsBoolean(rs, "Non_unique") == false); index.put("type", rsString(rs, "Index_type")); index.put("columns", new ArrayList<String>()); return index; });
                @SuppressWarnings("unchecked") List<String> columns = (List<String>) item.get("columns"); columns.add(rs.getString("Column_name"));
            }
            return new ArrayList<>(grouped.values());
        } catch (SQLException exception) { throw sqlError(exception); }
    }

    public void createIndex(String sessionId, String database, String table, IndexRequest request) { if (isSqlServer(sessionId)) { sqlServerCreateIndex(sessionId, database, table, request); return; } createIndexSql(sessionId, database, table, request); }
    public void updateIndex(String sessionId, String database, String table, String index, IndexRequest request) {
        if (isSqlServer(sessionId)) { sqlServerUpdateIndex(sessionId, database, table, index, request); return; }
        if ("PRIMARY".equalsIgnoreCase(index)) throw new ApiException("主键索引不能在此修改，请通过字段结构调整");
        execute(sessionId, "DROP INDEX " + id(index) + " ON " + id(database) + "." + id(table)); createIndexSql(sessionId, database, table, request);
    }
    public void deleteIndex(String sessionId, String database, String table, String index) {
        if (isSqlServer(sessionId)) { sqlServerDeleteIndex(sessionId, database, table, index); return; }
        if ("PRIMARY".equalsIgnoreCase(index)) throw new ApiException("主键索引不能在此删除，请通过字段结构调整");
        execute(sessionId, "DROP INDEX " + id(index) + " ON " + id(database) + "." + id(table));
    }

    private boolean isSqlServer(String sessionId) { return connections.type(sessionId) == ConnectionService.DatabaseType.SQLSERVER; }
    private void sqlExecute(String sessionId, String database, String sql) { try (Connection connection = database == null ? connections.open(sessionId) : connections.openDatabase(sessionId, database); Statement statement = connection.createStatement()) { statement.execute(sql); } catch (SQLException exception) { throw sqlError(exception); } }
    private List<Map<String, Object>> sqlServerDatabases(String sessionId) {
        String sql = "SELECT name, collation_name, state_desc FROM sys.databases ORDER BY name";
        try (Connection connection = connections.open(sessionId); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            List<Map<String, Object>> result = new ArrayList<>(); while (rs.next()) { String name = rs.getString(1); Map<String, Object> item = new LinkedHashMap<>(); item.put("name", name); item.put("charset", "SQL Server"); item.put("collation", rs.getString(2)); item.put("state", rs.getString(3)); item.put("system", Set.of("master", "model", "msdb", "tempdb").contains(name)); result.add(item); } return result;
        } catch (SQLException exception) { throw sqlError(exception); }
    }
    private void sqlServerUpdateDatabase(String sessionId, String database, DatabaseRequest request) {
        if (request.collation() != null && !request.collation().isBlank()) sqlExecute(sessionId, null, "ALTER DATABASE " + sqlId(database) + " COLLATE " + sqlId(request.collation()));
    }
    private List<Map<String, Object>> sqlServerTables(String sessionId, String database) {
        String sql = "SELECT t.name, CAST(ep.value AS nvarchar(4000)) AS comment, ISNULL(SUM(ps.row_count),0) AS rows FROM sys.tables t LEFT JOIN sys.extended_properties ep ON ep.major_id=t.object_id AND ep.minor_id=0 AND ep.name='MS_Description' LEFT JOIN sys.dm_db_partition_stats ps ON ps.object_id=t.object_id AND ps.index_id IN (0,1) GROUP BY t.name, ep.value ORDER BY t.name";
        try (Connection connection = connections.openDatabase(sessionId, database); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            List<Map<String, Object>> result = new ArrayList<>(); while (rs.next()) { Map<String, Object> item = new LinkedHashMap<>(); item.put("name", rs.getString(1)); item.put("comment", rs.getString(2)); item.put("rows", rs.getLong(3)); item.put("engine", "SQL Server"); item.put("type", "BASE TABLE"); result.add(item); } return result;
        } catch (SQLException exception) { throw sqlError(exception); }
    }
    private void sqlServerCreateTable(String sessionId, String database, TableRequest request) {
        String definitions = request.columns().stream().map(this::sqlServerColumnSql).reduce((a,b) -> a + ", " + b).orElseThrow(); sqlExecute(sessionId, database, "CREATE TABLE " + sqlTable(request.name()) + " (" + definitions + ")");
    }
    private void sqlServerUpdateTable(String sessionId, String database, String table, TableUpdateRequest request) {
        if (request.newName() != null && !request.newName().isBlank() && !request.newName().equals(table)) sqlExecute(sessionId, database, "EXEC sp_rename " + literal("dbo." + table) + ", " + literal(request.newName()) + ", 'OBJECT'");
    }
    private void sqlServerUpdateColumn(String sessionId, String database, String table, String column, ColumnDefinition definition) {
        if (!definition.name().equals(column)) sqlExecute(sessionId, database, "EXEC sp_rename " + literal("dbo." + table + "." + column) + ", " + literal(definition.name()) + ", 'COLUMN'");
        sqlExecute(sessionId, database, "ALTER TABLE " + sqlTable(table) + " ALTER COLUMN " + sqlServerColumnSql(definition).replace(" PRIMARY KEY", "").replace(" IDENTITY(1,1)", ""));
    }
    private List<Map<String, Object>> sqlServerStructure(String sessionId, String database, String table) {
        String sql = "SELECT c.name, ty.name + CASE WHEN ty.name IN ('varchar','nvarchar','char','nchar','decimal','numeric') THEN '(' + CASE WHEN c.max_length=-1 THEN 'max' WHEN ty.name IN ('nvarchar','nchar') THEN CAST(c.max_length/2 AS varchar) ELSE CAST(c.max_length AS varchar) END + CASE WHEN c.scale>0 THEN ','+CAST(c.scale AS varchar) ELSE '' END + ')' ELSE '' END, CASE WHEN c.is_nullable=1 THEN 'YES' ELSE 'NO' END, CASE WHEN i.is_primary_key=1 THEN 'PRI' ELSE '' END, dc.definition, CASE WHEN c.is_identity=1 THEN 'IDENTITY' ELSE '' END, CAST(ep.value AS nvarchar(4000)) FROM sys.columns c JOIN sys.types ty ON c.user_type_id=ty.user_type_id LEFT JOIN sys.default_constraints dc ON c.default_object_id=dc.object_id LEFT JOIN sys.index_columns ic ON ic.object_id=c.object_id AND ic.column_id=c.column_id LEFT JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id AND i.is_primary_key=1 LEFT JOIN sys.extended_properties ep ON ep.major_id=c.object_id AND ep.minor_id=c.column_id AND ep.name='MS_Description' WHERE c.object_id=OBJECT_ID(?) ORDER BY c.column_id";
        try (Connection connection = connections.openDatabase(sessionId, database); PreparedStatement ps = connection.prepareStatement(sql)) { ps.setString(1, "dbo." + table); try (ResultSet rs = ps.executeQuery()) { List<Map<String,Object>> result=new ArrayList<>(); while(rs.next()){Map<String,Object> item=new LinkedHashMap<>(); item.put("name",rs.getString(1));item.put("type",rs.getString(2));item.put("nullable",rs.getString(3));item.put("key",rs.getString(4));item.put("defaultValue",rs.getString(5));item.put("extra",rs.getString(6));item.put("comment",rs.getString(7));result.add(item);}return result;} } catch(SQLException exception){throw sqlError(exception);}
    }
    private List<Map<String, Object>> sqlServerRows(String sessionId, String database, String table, int limit, int offset, String keyword, String filterColumn, String sortBy, String sortDirection) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        List<String> columns = queryColumns(sessionId, database, table);
        String targetColumn = checkedQueryColumn(filterColumn, columns, false);
        String orderColumn = checkedQueryColumn(sortBy, columns, true);
        String direction = checkedDirection(sortDirection);
        String where = keyword == null || keyword.isBlank() ? "" : " WHERE " + searchClause(columns, targetColumn, true);
        String sql = "SELECT * FROM " + sqlTable(table) + where + " ORDER BY " + sqlId(orderColumn) + " " + direction + " OFFSET " + safeOffset + " ROWS FETCH NEXT " + safeLimit + " ROWS ONLY";
        try (Connection connection = connections.openDatabase(sessionId, database); PreparedStatement ps = connection.prepareStatement(sql)) {
            if (keyword != null && !keyword.isBlank()) bindSearch(ps, keyword, targetColumn == null ? columns.size() : 1);
            try (ResultSet rs = ps.executeQuery()) { return resultRows(rs); }
        } catch (SQLException exception) { throw sqlError(exception); }
    }
    private List<Map<String, Object>> sqlServerViews(String sessionId, String database) {
        String sql="SELECT v.name, CASE WHEN OBJECTPROPERTY(v.object_id,'IsUpdatable')=1 THEN 'YES' ELSE 'NO' END, SCHEMA_NAME(v.schema_id) FROM sys.views v ORDER BY v.name";
        try(Connection connection=connections.openDatabase(sessionId,database);Statement statement=connection.createStatement();ResultSet rs=statement.executeQuery(sql)){List<Map<String,Object>> result=new ArrayList<>();while(rs.next()){Map<String,Object> item=new LinkedHashMap<>();item.put("name",rs.getString(1));item.put("checkOption","NONE");item.put("updatable",rs.getString(2));item.put("definer",rs.getString(3));result.add(item);}return result;}catch(SQLException exception){throw sqlError(exception);}
    }
    private Map<String,Object> sqlServerView(String sessionId,String database,String view){String sql="SELECT m.definition FROM sys.views v JOIN sys.sql_modules m ON m.object_id=v.object_id WHERE v.name=? AND SCHEMA_NAME(v.schema_id)='dbo'";try(Connection connection=connections.openDatabase(sessionId,database);PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,view);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new ApiException("视图不存在："+view);return Map.of("name",view,"definition",rs.getString(1));}}catch(SQLException exception){throw sqlError(exception);}}
    private void sqlServerUpdateView(String sessionId,String database,String view,ViewRequest request){String target=request.name().equals(view)?view:request.name();sqlExecute(sessionId,database,"CREATE OR ALTER VIEW "+sqlTable(target)+" AS "+selectSql(sessionId, request.selectSql()));if(!target.equals(view))sqlExecute(sessionId,database,"DROP VIEW "+sqlTable(view));}
    private List<Map<String,Object>> sqlServerIndexes(String sessionId,String database,String table){String sql="SELECT i.name,i.is_unique,i.type_desc,c.name FROM sys.indexes i JOIN sys.index_columns ic ON i.object_id=ic.object_id AND i.index_id=ic.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE i.object_id=OBJECT_ID(?) AND i.name IS NOT NULL ORDER BY i.name,ic.key_ordinal";try(Connection connection=connections.openDatabase(sessionId,database);PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,"dbo."+table);try(ResultSet rs=ps.executeQuery()){Map<String,Map<String,Object>> grouped=new LinkedHashMap<>();while(rs.next()){String name=rs.getString(1);Map<String,Object> item=grouped.computeIfAbsent(name,ignored->{Map<String,Object> v=new LinkedHashMap<>();v.put("name",name);v.put("unique",rsBool(rs,2));v.put("type",rsText(rs,3));v.put("columns",new ArrayList<String>());return v;});@SuppressWarnings("unchecked") List<String> columns=(List<String>)item.get("columns");columns.add(rs.getString(4));}return new ArrayList<>(grouped.values());}}catch(SQLException exception){throw sqlError(exception);}}
    private void sqlServerCreateIndex(String sessionId,String database,String table,IndexRequest request){if("PRIMARY".equalsIgnoreCase(request.name()))throw new ApiException("PRIMARY 是保留索引名");String columns=request.columns().stream().map(this::sqlId).reduce((a,b)->a+", "+b).orElseThrow();sqlExecute(sessionId,database,"CREATE "+(request.unique()?"UNIQUE ":"")+"INDEX "+sqlId(request.name())+" ON "+sqlTable(table)+" ("+columns+")");}
    private void sqlServerUpdateIndex(String sessionId,String database,String table,String index,IndexRequest request){if("PRIMARY".equalsIgnoreCase(index))throw new ApiException("主键索引不能在此修改");sqlServerDeleteIndex(sessionId,database,table,index);sqlServerCreateIndex(sessionId,database,table,request);}
    private void sqlServerDeleteIndex(String sessionId,String database,String table,String index){if("PRIMARY".equalsIgnoreCase(index))throw new ApiException("主键索引不能在此删除");sqlExecute(sessionId,database,"DROP INDEX "+sqlId(index)+" ON "+sqlTable(table));}
    private List<Map<String,Object>> sqlQueryRows(String sessionId,String database,String sql){try(Connection connection=connections.openDatabase(sessionId,database);Statement statement=connection.createStatement();ResultSet rs=statement.executeQuery(sql)){return resultRows(rs);}catch(SQLException exception){throw sqlError(exception);}}
    private String sqlServerColumnSql(ColumnDefinition column){String type=column.type().toLowerCase();String length=column.length()==null||column.length().isBlank()?"":checkedLength(column.length());String mapped=switch(type){case "boolean"->"BIT";case "json","text","tinytext","mediumtext","longtext"->"NVARCHAR(MAX)";case "datetime","timestamp"->"DATETIME2";case "varchar","char"->type.toUpperCase()+"("+(length.isBlank()?"255":length)+")";case "decimal","numeric"->type.toUpperCase()+"("+(length.isBlank()?"18,2":length)+")";case "tinyint","smallint","int","integer","bigint","float","double","date","time"->(type.equals("integer")?"INT":type.toUpperCase());default->throw new ApiException("SQL Server 不支持字段类型："+column.type());};return sqlId(column.name())+" "+mapped+(column.autoIncrement()?" IDENTITY(1,1)":"")+(column.nullable()?" NULL":" NOT NULL")+(column.defaultValue()==null||column.defaultValue().isBlank()?"":" DEFAULT "+sqlServerDefault(column.defaultValue()))+(column.primaryKey()?" PRIMARY KEY":"");}
    private String sqlServerDefault(String value){return value.equalsIgnoreCase("CURRENT_TIMESTAMP")||value.equalsIgnoreCase("GETDATE()")||value.equalsIgnoreCase("NULL")?value.toUpperCase():literal(value);}
    private String sqlId(String value){if(value==null||!IDENTIFIER.matcher(value).matches())throw new ApiException("标识符只能包含英文字母、数字、下划线或 $："+value);return "["+value+"]";}
    private String sqlTable(String table){return "[dbo]."+sqlId(table);}
    private boolean rsBool(ResultSet rs,int index){try{return rs.getBoolean(index);}catch(SQLException exception){throw new ApiException("读取索引信息失败",exception);}}
    private String rsText(ResultSet rs,int index){try{return rs.getString(index);}catch(SQLException exception){throw new ApiException("读取索引信息失败",exception);}}
    private Map<String, Object> safeValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) throw new ApiException("至少需要提供一个字段值");
        Map<String, Object> safe = new LinkedHashMap<>(); values.forEach((key, value) -> { id(key); safe.put(key, value); }); return safe;
    }
    private List<String> queryColumns(String sessionId, String database, String table) { List<String> columns = structure(sessionId, database, table).stream().map(column -> String.valueOf(column.get("name"))).toList(); if (columns.isEmpty()) throw new ApiException("数据表没有可查询的字段"); return columns; }
    private String checkedQueryColumn(String candidate, List<String> columns, boolean required) { if (candidate == null || candidate.isBlank()) return required ? columns.get(0) : null; if (!columns.contains(candidate)) throw new ApiException("筛选或排序字段不存在：" + candidate); return candidate; }
    private String checkedDirection(String direction) { return "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC"; }
    private String searchClause(List<String> columns, String targetColumn, boolean sqlServer) { List<String> targets = targetColumn == null ? columns : List.of(targetColumn); return targets.stream().map(column -> sqlServer ? "CAST(" + sqlId(column) + " AS NVARCHAR(MAX)) LIKE ?" : "CAST(" + id(column) + " AS CHAR) LIKE ?").reduce((left, right) -> left + " OR " + right).orElseThrow(); }
    private void bindSearch(PreparedStatement statement, String keyword, int count) throws SQLException { for (int index = 1; index <= count; index++) statement.setString(index, "%" + keyword.trim() + "%"); }
    private List<Map<String, Object>> resultRows(ResultSet rs) throws SQLException { List<Map<String, Object>> result = new ArrayList<>(); ResultSetMetaData meta = rs.getMetaData(); while (rs.next()) { Map<String, Object> row = new LinkedHashMap<>(); for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i)); result.add(row); } return result; }
    private Map<String, Object> requirePrimaryKey(String sessionId, String database, String table, Map<String, Object> key) {
        Map<String, Object> safe = safeValues(key); List<String> primaryKeys = primaryKeys(sessionId, database, table);
        if (primaryKeys.isEmpty()) throw new ApiException("该表没有主键，无法安全定位记录，因此不允许修改或删除数据。请先定义主键。");
        for (String primary : primaryKeys) if (!safe.containsKey(primary)) throw new ApiException("修改或删除记录必须提供主键字段：" + primary);
        return safe;
    }
    private List<String> primaryKeys(String sessionId, String database, String table) {
        try (Connection connection = dataConnection(sessionId, database); ResultSet rs = connection.getMetaData().getPrimaryKeys(database, null, table)) { List<String> keys = new ArrayList<>(); while (rs.next()) keys.add(rs.getString("COLUMN_NAME")); return keys; } catch (SQLException exception) { throw sqlError(exception); }
    }
    private Connection dataConnection(String sessionId, String database) { return isSqlServer(sessionId) ? connections.openDatabase(sessionId, database) : connections.open(sessionId); }
    private String qualifiedTable(String sessionId, String database, String table) { return isSqlServer(sessionId) ? sqlTable(table) : id(database) + "." + id(table); }
    private String columnId(String sessionId, String column) { return isSqlServer(sessionId) ? sqlId(column) : id(column); }
    private String whereClause(String sessionId, java.util.Collection<String> columns) {
        boolean sqlServer = isSqlServer(sessionId); return columns.stream().map(column -> sqlServer ? sqlId(column) + " = ?" : id(column) + " <=> ?").reduce((a,b)->a+" AND "+b).orElseThrow();
    }
    private void bind(PreparedStatement statement, java.util.Collection<Object> values) throws SQLException { int position = 1; for (Object value : values) statement.setObject(position++, value); }
    private ApiException dataError(String operation, SQLException exception) { String message = exception.getMessage(); String normalized = message == null ? "" : message.toLowerCase(); if (normalized.contains("foreign key") || normalized.contains("reference constraint") || message.contains("FK_")) return new ApiException(operation + "：该记录仍被其他数据通过外键关联。请先处理关联记录后再重试。"); if (normalized.contains("duplicate") || normalized.contains("unique constraint")) return new ApiException(operation + "：字段值与已有记录重复，未满足唯一约束。请修改后重试。"); if (normalized.contains("cannot be null") || normalized.contains("doesn't have a default value") || normalized.contains("cannot insert the value null")) return new ApiException(operation + "：存在必填字段未填写。请补全非空字段后重试。"); if (normalized.contains("data truncation") || normalized.contains("too long") || normalized.contains("conversion failed")) return new ApiException(operation + "：字段值的长度或类型不符合表定义。请检查后重试。"); return new ApiException(operation + "：" + message, exception); }
    private void execute(String sessionId, String sql) { try (Connection connection = connections.open(sessionId); Statement statement = connection.createStatement()) { statement.execute(sql); } catch (SQLException exception) { throw sqlError(exception); } }
    private void executeInDatabase(String sessionId, String database, String sql) { try (Connection connection = connections.openDatabase(sessionId, database); Statement statement = connection.createStatement()) { statement.execute(sql); } catch (SQLException exception) { throw sqlError(exception); } }
    private String charset(DatabaseRequest request) {
        String charset = request.charset() == null || request.charset().isBlank() ? "utf8mb4" : id(request.charset());
        String collation = request.collation() == null || request.collation().isBlank() ? "utf8mb4_unicode_ci" : id(request.collation());
        return " CHARACTER SET " + charset + " COLLATE " + collation;
    }
    private String columnSql(String sessionId, ColumnDefinition column) {
        String type = column.type().toLowerCase(); if (!TYPES.contains(type)) throw new ApiException("不支持的字段类型：" + column.type());
        if ("json".equals(type) && !mysqlAtLeast(sessionId, 5, 7, 8)) throw new ApiException("当前 MySQL 版本低于 5.7.8，不支持 JSON 字段。请改用 LONGTEXT，或升级至 MySQL 5.7.8+。");
        String length = column.length() == null || column.length().isBlank() ? "" : "(" + checkedLength(column.length()) + ")";
        String nullable = column.nullable() ? " NULL" : " NOT NULL";
        String defaultValue = column.defaultValue() == null || column.defaultValue().isBlank() ? "" : " DEFAULT " + defaultSql(column.defaultValue());
        String extra = column.autoIncrement() ? " AUTO_INCREMENT" : "";
        String comment = " COMMENT " + literal(column.comment());
        String primary = column.primaryKey() ? " PRIMARY KEY" : "";
        return id(column.name()) + " " + type.toUpperCase() + length + nullable + defaultValue + extra + comment + primary;
    }
    private String defaultSql(String value) { return value.equalsIgnoreCase("CURRENT_TIMESTAMP") || value.equalsIgnoreCase("NULL") ? value.toUpperCase() : literal(value); }
    private void createIndexSql(String sessionId, String database, String table, IndexRequest request) {
        if ("PRIMARY".equalsIgnoreCase(request.name())) throw new ApiException("PRIMARY 是保留索引名");
        String columns = request.columns().stream().map(this::id).reduce((a, b) -> a + ", " + b).orElseThrow();
        execute(sessionId, "CREATE " + (request.unique() ? "UNIQUE " : "") + "INDEX " + id(request.name()) + " ON " + id(database) + "." + id(table) + " (" + columns + ")");
    }
    private String selectSql(String sessionId, String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.contains(";") || !normalized.matches("(?is)^(select|with)\\s+.+")) throw new ApiException("视图 SQL 只能是一条以 SELECT 或 WITH 开头的查询，且不能包含分号");
        if (!isSqlServer(sessionId) && normalized.matches("(?is)^with\\s+.+") && !mysqlAtLeast(sessionId, 8, 0, 0)) return rewriteMysql57Cte(normalized);
        return normalized;
    }
    private String rewriteMysql57Cte(String sql) {
        Matcher cte = MYSQL57_SIMPLE_CTE.matcher(sql);
        if (!cte.matches()) throw new ApiException("当前 MySQL 5.7 只能自动兼容单个、非递归的 WITH 查询。多个 CTE、递归 CTE 或 CTE 列别名请改用子查询，或升级至 MySQL 8.0+。");
        String cteName = cte.group(1); String cteQuery = cte.group(2).trim(); String mainQuery = cte.group(3).trim();
        if (!cteQuery.matches("(?is)^select\\s+.+") || cteQuery.matches("(?is)^with\\s+.+")) throw new ApiException("当前 MySQL 5.7 不支持递归或嵌套 WITH 查询。请改用普通子查询，或升级至 MySQL 8.0+。");
        Pattern reference = Pattern.compile("(?is)(\\b(?:from|join)\\s+)(`?" + Pattern.quote(cteName) + "`?)(?:\\s+(?:as\\s+)?([A-Za-z][A-Za-z0-9_$]*))?(?=\\s+(?:where|join|left|right|inner|outer|cross|group|order|limit|having|on|union)\\b|\\s*$|\\s*,|\\s*\\))");
        Matcher referenceMatcher = reference.matcher(mainQuery); StringBuffer rewritten = new StringBuffer(); boolean replaced = false;
        while (referenceMatcher.find()) { String alias = referenceMatcher.group(3); referenceMatcher.appendReplacement(rewritten, "$1(" + Matcher.quoteReplacement(cteQuery) + ") AS " + (alias == null ? cteName : alias)); replaced = true; }
        referenceMatcher.appendTail(rewritten);
        if (!replaced) throw new ApiException("MySQL 5.7 未找到可自动改写的 CTE 引用。请使用 FROM " + cteName + " 或 JOIN " + cteName + "，或手动改为子查询。");
        return rewritten.toString();
    }
    private boolean mysqlAtLeast(String sessionId, int requiredMajor, int requiredMinor, int requiredPatch) {
        if (isSqlServer(sessionId)) return true;
        try (Connection connection = connections.open(sessionId)) {
            Matcher matcher = MYSQL_VERSION.matcher(connection.getMetaData().getDatabaseProductVersion());
            if (!matcher.find()) return false;
            int major = Integer.parseInt(matcher.group(1)); int minor = Integer.parseInt(matcher.group(2)); int patch = Integer.parseInt(matcher.group(3));
            if (major != requiredMajor) return major > requiredMajor;
            if (minor != requiredMinor) return minor > requiredMinor;
            return patch >= requiredPatch;
        } catch (SQLException exception) { throw sqlError(exception); }
    }
    private boolean rsBoolean(ResultSet resultSet, String column) { try { return resultSet.getBoolean(column); } catch (SQLException exception) { throw new ApiException("读取索引信息失败", exception); } }
    private String rsString(ResultSet resultSet, String column) { try { return resultSet.getString(column); } catch (SQLException exception) { throw new ApiException("读取索引信息失败", exception); } }
    private String id(String value) { if (value == null || !IDENTIFIER.matcher(value).matches()) throw new ApiException("标识符只能包含英文字母、数字、下划线或 $：" + value); return "`" + value + "`"; }
    private String checkedLength(String value) { if (!LENGTH.matcher(value).matches()) throw new ApiException("字段长度格式不合法"); return value; }
    private String literal(String value) { return "'" + (value == null ? "" : value.replace("'", "''")) + "'"; }
    private boolean isSystemSchema(String name) { return Set.of("mysql", "information_schema", "performance_schema", "sys").contains(name); }
    private ApiException sqlError(SQLException exception) { return new ApiException("SQL 执行失败：" + exception.getMessage()); }
}
