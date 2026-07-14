package com.mysqladmin.controller;

import com.mysqladmin.api.ApiResponse;
import com.mysqladmin.dto.ColumnDefinition;
import com.mysqladmin.dto.ConnectionRequest;
import com.mysqladmin.dto.DatabaseRequest;
import com.mysqladmin.dto.TableRequest;
import com.mysqladmin.dto.TableUpdateRequest;
import com.mysqladmin.dto.ViewRequest;
import com.mysqladmin.dto.IndexRequest;
import com.mysqladmin.dto.RowCreateRequest;
import com.mysqladmin.dto.RowUpdateRequest;
import com.mysqladmin.service.ConnectionService;
import com.mysqladmin.service.MysqlAdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MysqlAdminController {
    private final ConnectionService connections; private final MysqlAdminService mysql;
    public MysqlAdminController(ConnectionService connections, MysqlAdminService mysql) { this.connections = connections; this.mysql = mysql; }

    @PostMapping("/connections/test") @Operation(summary = "测试并保存 MySQL 连接")
    public ApiResponse<Map<String, Object>> connect(@Valid @RequestBody ConnectionRequest request, HttpSession session) { return ApiResponse.ok("MySQL 连接成功", connections.connect(session.getId(), request)); }
    @GetMapping("/connections/status") public ApiResponse<Map<String, Object>> status(HttpSession session) { return ApiResponse.ok(connections.status(session.getId())); }
    @DeleteMapping("/connections") public ApiResponse<Void> disconnect(HttpSession session) { connections.disconnect(session.getId()); return ApiResponse.ok("连接已断开", null); }

    @GetMapping("/databases") public ApiResponse<?> databases(HttpSession session) { return ApiResponse.ok(mysql.databases(session.getId())); }
    @PostMapping("/databases") public ApiResponse<Void> createDatabase(@Valid @RequestBody DatabaseRequest request, HttpSession session) { mysql.createDatabase(session.getId(), request); return ApiResponse.ok("数据库已创建", null); }
    @PutMapping("/databases/{database}") public ApiResponse<Void> updateDatabase(@PathVariable String database, @Valid @RequestBody DatabaseRequest request, HttpSession session) { mysql.updateDatabase(session.getId(), database, request); return ApiResponse.ok("数据库配置已更新", null); }
    @DeleteMapping("/databases/{database}") public ApiResponse<Void> deleteDatabase(@PathVariable String database, HttpSession session) { mysql.deleteDatabase(session.getId(), database); return ApiResponse.ok("数据库已删除", null); }

    @GetMapping("/databases/{database}/tables") public ApiResponse<?> tables(@PathVariable String database, HttpSession session) { return ApiResponse.ok(mysql.tables(session.getId(), database)); }
    @PostMapping("/databases/{database}/tables") public ApiResponse<Void> createTable(@PathVariable String database, @Valid @RequestBody TableRequest request, HttpSession session) { mysql.createTable(session.getId(), database, request); return ApiResponse.ok("数据表已创建", null); }
    @PutMapping("/databases/{database}/tables/{table}") public ApiResponse<Void> updateTable(@PathVariable String database, @PathVariable String table, @RequestBody TableUpdateRequest request, HttpSession session) { mysql.updateTable(session.getId(), database, table, request); return ApiResponse.ok("数据表已更新", null); }
    @DeleteMapping("/databases/{database}/tables/{table}") public ApiResponse<Void> deleteTable(@PathVariable String database, @PathVariable String table, HttpSession session) { mysql.deleteTable(session.getId(), database, table); return ApiResponse.ok("数据表已删除", null); }
    @PostMapping("/databases/{database}/tables/{table}/columns") public ApiResponse<Void> addColumn(@PathVariable String database, @PathVariable String table, @Valid @RequestBody ColumnDefinition request, HttpSession session) { mysql.addColumn(session.getId(), database, table, request); return ApiResponse.ok("字段已新增", null); }
    @PutMapping("/databases/{database}/tables/{table}/columns/{column}") public ApiResponse<Void> updateColumn(@PathVariable String database, @PathVariable String table, @PathVariable String column, @Valid @RequestBody ColumnDefinition request, HttpSession session) { mysql.updateColumn(session.getId(), database, table, column, request); return ApiResponse.ok("字段已更新", null); }
    @DeleteMapping("/databases/{database}/tables/{table}/columns/{column}") public ApiResponse<Void> dropColumn(@PathVariable String database, @PathVariable String table, @PathVariable String column, HttpSession session) { mysql.dropColumn(session.getId(), database, table, column); return ApiResponse.ok("字段已删除", null); }
    @GetMapping("/databases/{database}/tables/{table}/structure") public ApiResponse<?> structure(@PathVariable String database, @PathVariable String table, HttpSession session) { return ApiResponse.ok(mysql.structure(session.getId(), database, table)); }
    @GetMapping("/databases/{database}/tables/{table}/rows") public ApiResponse<?> rows(@PathVariable String database, @PathVariable String table, @RequestParam(defaultValue = "100") int limit, @RequestParam(defaultValue = "0") int offset, @RequestParam(required = false) String keyword, @RequestParam(required = false) String filterColumn, @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "ASC") String sortDirection, HttpSession session) { return ApiResponse.ok(mysql.rows(session.getId(), database, table, limit, offset, keyword, filterColumn, sortBy, sortDirection)); }
    @PostMapping("/databases/{database}/tables/{table}/rows") public ApiResponse<Void> createRow(@PathVariable String database, @PathVariable String table, @Valid @RequestBody RowCreateRequest request, HttpSession session) { mysql.createRow(session.getId(), database, table, request.values()); return ApiResponse.ok("记录已新增", null); }
    @PutMapping("/databases/{database}/tables/{table}/rows") public ApiResponse<Void> updateRow(@PathVariable String database, @PathVariable String table, @Valid @RequestBody RowUpdateRequest request, HttpSession session) { mysql.updateRow(session.getId(), database, table, request.key(), request.values()); return ApiResponse.ok("记录已更新", null); }
    @DeleteMapping("/databases/{database}/tables/{table}/rows") public ApiResponse<Void> deleteRow(@PathVariable String database, @PathVariable String table, @Valid @RequestBody RowUpdateRequest request, HttpSession session) { mysql.deleteRow(session.getId(), database, table, request.key()); return ApiResponse.ok("记录已删除", null); }
    @GetMapping("/databases/{database}/views") public ApiResponse<?> views(@PathVariable String database, HttpSession session) { return ApiResponse.ok(mysql.views(session.getId(), database)); }
    @PostMapping("/databases/{database}/views") public ApiResponse<Void> createView(@PathVariable String database, @Valid @RequestBody ViewRequest request, HttpSession session) { mysql.createView(session.getId(), database, request); return ApiResponse.ok("视图已创建", null); }
    @GetMapping("/databases/{database}/views/{view}") public ApiResponse<?> view(@PathVariable String database, @PathVariable String view, HttpSession session) { return ApiResponse.ok(mysql.view(session.getId(), database, view)); }
    @PutMapping("/databases/{database}/views/{view}") public ApiResponse<Void> updateView(@PathVariable String database, @PathVariable String view, @Valid @RequestBody ViewRequest request, HttpSession session) { mysql.updateView(session.getId(), database, view, request); return ApiResponse.ok("视图已更新", null); }
    @DeleteMapping("/databases/{database}/views/{view}") public ApiResponse<Void> deleteView(@PathVariable String database, @PathVariable String view, HttpSession session) { mysql.deleteView(session.getId(), database, view); return ApiResponse.ok("视图已删除", null); }
    @GetMapping("/databases/{database}/views/{view}/rows") public ApiResponse<?> viewRows(@PathVariable String database, @PathVariable String view, @RequestParam(defaultValue = "100") int limit, HttpSession session) { return ApiResponse.ok(mysql.viewRows(session.getId(), database, view, limit)); }
    @GetMapping("/databases/{database}/tables/{table}/indexes") public ApiResponse<?> indexes(@PathVariable String database, @PathVariable String table, HttpSession session) { return ApiResponse.ok(mysql.indexes(session.getId(), database, table)); }
    @PostMapping("/databases/{database}/tables/{table}/indexes") public ApiResponse<Void> createIndex(@PathVariable String database, @PathVariable String table, @Valid @RequestBody IndexRequest request, HttpSession session) { mysql.createIndex(session.getId(), database, table, request); return ApiResponse.ok("索引已创建", null); }
    @PutMapping("/databases/{database}/tables/{table}/indexes/{index}") public ApiResponse<Void> updateIndex(@PathVariable String database, @PathVariable String table, @PathVariable String index, @Valid @RequestBody IndexRequest request, HttpSession session) { mysql.updateIndex(session.getId(), database, table, index, request); return ApiResponse.ok("索引已更新", null); }
    @DeleteMapping("/databases/{database}/tables/{table}/indexes/{index}") public ApiResponse<Void> deleteIndex(@PathVariable String database, @PathVariable String table, @PathVariable String index, HttpSession session) { mysql.deleteIndex(session.getId(), database, table, index); return ApiResponse.ok("索引已删除", null); }
}
