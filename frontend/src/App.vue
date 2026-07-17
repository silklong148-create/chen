<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from './api'

const connected = ref(false)
const authenticated = ref(false)
const authBusy = ref(false)
const authMode = ref('login')
const currentUser = ref('')
const showUserMenu = ref(false)
const connecting = ref(false)
const busy = ref(false)
const databases = ref([])
const tables = ref([])
const views = ref([])
const indexes = ref([])
const activeDatabase = ref(null)
const selectedTable = ref(null)
const selectedView = ref(null)
const tableStructure = ref([])
const tableRows = ref([])
const selectedViewDefinition = ref('')
const selectedViewRows = ref([])
const activeTab = ref('structure')
const databasePanel = ref('tables')
const databaseListPage = ref(0)
const databaseListPageSize = 10
const rowPage = ref(0)
const rowPageSize = ref(100)
const rowKeyword = ref('')
const rowFilterColumn = ref('')
const rowSortBy = ref('')
const rowSortDirection = ref('ASC')
const visibleColumns = ref([])
const showColumnPicker = ref(false)
const dataViewport = ref(null)
const dataScrollTop = ref(0)
const showDatabaseDialog = ref(false)
const showDatabaseInfoDialog = ref(false)
const showTableDialog = ref(false)
const showTableInfoDialog = ref(false)
const showColumnDialog = ref(false)
const showServerDialog = ref(false)
const showViewDialog = ref(false)
const showIndexDialog = ref(false)
const showRowDialog = ref(false)
const editingColumn = ref(null)
const editingView = ref(null)
const editingIndex = ref(null)
const editingRow = ref(null)
const toast = ref(null)
const connection = reactive({ host: '127.0.0.1', port: 3306, username: 'root', password: '', databaseType: 'MYSQL', databaseName: '' })
const serverForm = reactive({ host: '127.0.0.1', port: 3306, username: 'root', password: '', databaseType: 'MYSQL', databaseName: '' })
const databaseTypeOptions = [
  { value: 'MYSQL', mark: 'MY', label: 'MySQL', meta: '3306' },
  { value: 'POSTGRESQL', mark: 'PG', label: 'PostgreSQL', meta: '5432' },
  { value: 'ORACLE', mark: 'OR', label: 'Oracle', meta: '1521' },
  { value: 'SQLSERVER', mark: 'MS', label: 'SQL Server', meta: '1433' },
  { value: 'DAMENG', mark: 'DM', label: '达梦 DM8', meta: '5236' }
]
const authForm = reactive({ username: '', password: '' })
const databaseForm = reactive({ name: '', charset: 'utf8mb4', collation: 'utf8mb4_unicode_ci' })
const databaseInfoForm = reactive({ name: '', charset: 'utf8mb4', collation: 'utf8mb4_unicode_ci' })
const tableForm = reactive({ name: '', comment: '', columns: [newColumn()] })
const tableInfoForm = reactive({ originalName: '', name: '', comment: '' })
const columnForm = reactive(newColumn())
const viewForm = reactive({ name: '', selectSql: '' })
const indexForm = reactive({ name: '', unique: false, columns: [] })
const rowValues = reactive({})

function newColumn() { return { name: 'id', type: 'bigint', length: '20', primaryKey: true, autoIncrement: true, nullable: false, defaultValue: '', comment: '' } }
const userDatabases = computed(() => databases.value.filter(item => !item.system))
const databaseListTotalPages = computed(() => Math.max(1, Math.ceil(userDatabases.value.length / databaseListPageSize)))
const pagedUserDatabases = computed(() => userDatabases.value.slice(databaseListPage.value * databaseListPageSize, (databaseListPage.value + 1) * databaseListPageSize))
const rowHeaders = computed(() => tableRows.value.length ? Object.keys(tableRows.value[0]) : tableStructure.value.map(column => column.name))
const visibleRowHeaders = computed(() => rowHeaders.value.filter(column => visibleColumns.value.includes(column)))
const primaryColumns = computed(() => tableStructure.value.filter(column => column.key === 'PRI'))
const canSafelyEditRows = computed(() => primaryColumns.value.length > 0)
const virtualStart = computed(() => Math.max(0, Math.floor(dataScrollTop.value / 38) - 8))
const virtualEnd = computed(() => Math.min(tableRows.value.length, virtualStart.value + 28))
const virtualRows = computed(() => tableRows.value.slice(virtualStart.value, virtualEnd.value))
const virtualTopHeight = computed(() => virtualStart.value * 38)
const virtualBottomHeight = computed(() => Math.max(0, (tableRows.value.length - virtualEnd.value) * 38))
const dataColumnSpan = computed(() => visibleRowHeaders.value.length + (canSafelyEditRows.value ? 1 : 0))
const isOracleConnection = computed(() => connection.databaseType === 'ORACLE')
const databaseEntityLabel = computed(() => isOracleConnection.value ? 'Schema 用户' : '数据库')
const databaseListLabel = computed(() => isOracleConnection.value ? 'SCHEMAS' : 'DATABASES')
const databaseCreateTitle = computed(() => isOracleConnection.value ? '创建 Schema 用户' : '创建数据库')
const databaseCreateAction = computed(() => isOracleConnection.value ? '创建 Schema 用户' : '创建数据库')
const databaseNameLabel = computed(() => isOracleConnection.value ? 'Schema 用户名' : '数据库名称')
const databaseNamePlaceholder = computed(() => isOracleConnection.value ? '例如 APP_BIZ' : '例如 app_dev')
const databaseSelectPrompt = computed(() => isOracleConnection.value ? '选择一个 Schema 用户开始' : '选择一个数据库开始')
const databaseEmptyMessage = computed(() => isOracleConnection.value ? '尚无可管理的业务 Schema' : '尚无可管理的业务数据库')
const databaseContextCopy = computed(() => isOracleConnection.value ? 'Oracle 中的 Schema 与用户一一对应。使用 root 账号可直接创建业务 Schema 用户，并在其中维护表、视图和数据。' : '左侧显示当前实例下的业务数据库。系统数据库默认隐藏，避免误操作。')
const databaseSummaryCopy = computed(() => isOracleConnection.value ? '当前 Schema 的表、视图与数据预览均在此维护。' : '当前数据库的表、视图与数据预览均在此维护。')
const databaseUnitLabel = computed(() => isOracleConnection.value ? '个 Schema' : '个数据库')
const serverDatabaseFieldVisible = computed(() => ['MYSQL', 'POSTGRESQL', 'ORACLE'].includes(serverForm.databaseType))
const serverDatabaseFieldRequired = computed(() => ['POSTGRESQL', 'ORACLE'].includes(serverForm.databaseType))
const serverDatabaseFieldLabel = computed(() => ({ MYSQL: '指定数据库（可选）', POSTGRESQL: '初始数据库', ORACLE: 'Service Name' }[serverForm.databaseType] || '数据库名称'))
const serverDatabaseFieldPlaceholder = computed(() => ({ MYSQL: '例如 app_dev，留空则读取可见数据库', POSTGRESQL: '例如 postgres', ORACLE: '例如 FREEPDB1' }[serverForm.databaseType] || ''))

function notify(message, tone = 'success') { toast.value = { message, tone }; window.setTimeout(() => { toast.value = null }, 3200) }
function fail(error) { notify(error.message || '操作失败，请稍后重试', 'error') }
function normalizeDatabaseListPage() { databaseListPage.value = Math.min(databaseListPage.value, databaseListTotalPages.value - 1) }
function nextDatabaseListPage() { databaseListPage.value = Math.min(databaseListPage.value + 1, databaseListTotalPages.value - 1) }
function previousDatabaseListPage() { databaseListPage.value = Math.max(databaseListPage.value - 1, 0) }

async function authenticate() {
  authBusy.value = true
  try {
    const user = authMode.value === 'login' ? await api.login(authForm) : await api.register(authForm)
    authenticated.value = true; currentUser.value = user.username; authForm.password = ''; notify(authMode.value === 'login' ? '欢迎回来' : '账号已创建')
  } catch (error) { fail(error) } finally { authBusy.value = false }
}
async function logout() {
  if (!confirm('确定退出 Mysqladmin 吗？当前数据库连接将一并断开。')) return
  try { await api.logout(); authenticated.value = false; connected.value = false; showUserMenu.value = false; currentUser.value = ''; databases.value = []; tables.value = []; activeDatabase.value = null; selectedTable.value = null; notify('已退出登录') } catch (error) { fail(error) }
}

async function connect() {
  connecting.value = true
  try {
    const profile = await api.connect(connection)
    connected.value = true
    const loaded = await loadDatabases({ disconnectOnError: false, quiet: true })
    notify(loaded ? `已连接 ${profile.host}:${profile.port}` : `已连接 ${profile.host}:${profile.port}，但暂时无法读取数据库列表`)
  } catch (error) { fail(error) } finally { connecting.value = false }
}
function openServerManager() {
  showUserMenu.value = false
  Object.assign(serverForm, connection, { password: '' })
  showServerDialog.value = true
}
function changeDatabaseType() {
  const defaults = { MYSQL: [3306, ''], SQLSERVER: [1433, 'master'], DAMENG: [5236, ''], POSTGRESQL: [5432, 'postgres'], ORACLE: [1521, 'FREEPDB1'] }
  const knownPorts = [3306, 1433, 5236, 5432, 1521]
  const [port, databaseName] = defaults[serverForm.databaseType]
  if (knownPorts.includes(serverForm.port)) serverForm.port = port
  serverForm.databaseName = databaseName
}

function databaseTypeLabel(type) {
  return { MYSQL: 'MySQL', SQLSERVER: 'SQL Server', DAMENG: '达梦 DM8', POSTGRESQL: 'PostgreSQL', ORACLE: 'Oracle' }[type] || type
}
async function switchServer() {
  connecting.value = true
  try {
    const profile = await api.connect(serverForm)
    Object.assign(connection, { host: profile.host, port: profile.port, username: profile.username, password: serverForm.password, databaseType: profile.type, databaseName: profile.databaseName || '' })
    connected.value = true; activeDatabase.value = null; selectedTable.value = null; tables.value = []; databaseListPage.value = 0; showServerDialog.value = false
    const loaded = await loadDatabases({ disconnectOnError: false, quiet: true })
    notify(loaded ? `已切换至 ${profile.host}:${profile.port}` : `已切换至 ${profile.host}:${profile.port}，但暂时无法读取数据库列表`)
  } catch (error) { fail(error) } finally { connecting.value = false }
}
async function disconnect() {
  if (!confirm('断开后将清除当前服务端会话。确定继续吗？')) return
  try { await api.disconnect(); connected.value = false; databases.value = []; tables.value = []; activeDatabase.value = null; selectedTable.value = null; notify('连接已断开') } catch (error) { fail(error) }
}
async function loadDatabases({ disconnectOnError = false, quiet = false } = {}) {
  try {
    databases.value = await api.databases()
    normalizeDatabaseListPage()
    return true
  } catch (error) {
    databases.value = []
    if (!quiet) fail(error)
    if (disconnectOnError) connected.value = false
    return false
  }
}
async function selectDatabase(database) {
  activeDatabase.value = database; selectedTable.value = null; selectedView.value = null; tableStructure.value = []; tableRows.value = []; selectedViewRows.value = []; databasePanel.value = 'tables'
  try { const [tableList, viewList] = await Promise.all([api.tables(database.name), api.views(database.name)]); tables.value = tableList; views.value = viewList } catch (error) { fail(error) }
}
async function openTable(table) {
  selectedView.value = null; selectedTable.value = table; activeTab.value = 'structure'; rowPage.value = 0; rowKeyword.value = ''; rowFilterColumn.value = ''; rowSortBy.value = ''; rowSortDirection.value = 'ASC'; visibleColumns.value = []; showColumnPicker.value = false; busy.value = true
  try { const [structure, indexList] = await Promise.all([api.structure(activeDatabase.value.name, table.name), api.indexes(activeDatabase.value.name, table.name)]); tableStructure.value = structure; indexes.value = indexList; syncVisibleColumns(); await loadRows(0) } catch (error) { fail(error) } finally { busy.value = false }
}
async function loadRows(page = rowPage.value) {
  if (!selectedTable.value) return
  busy.value = true
  try {
    tableRows.value = await api.rows(activeDatabase.value.name, selectedTable.value.name, { limit: rowPageSize.value, offset: page * rowPageSize.value, keyword: rowKeyword.value, filterColumn: rowFilterColumn.value, sortBy: rowSortBy.value, sortDirection: rowSortDirection.value })
    rowPage.value = page; syncVisibleColumns(); resetDataViewport()
  } catch (error) { fail(error) } finally { busy.value = false }
}
function syncVisibleColumns() { const headers = rowHeaders.value; const retained = visibleColumns.value.filter(column => headers.includes(column)); visibleColumns.value = retained.length ? retained : [...headers] }
function applyRowQuery() { loadRows(0) }
function changePageSize() { loadRows(0) }
function toggleSortDirection() { rowSortDirection.value = rowSortDirection.value === 'ASC' ? 'DESC' : 'ASC'; applyRowQuery() }
function toggleColumn(column) { if (visibleColumns.value.includes(column)) { if (visibleColumns.value.length === 1) return notify('至少保留一列可见', 'error'); visibleColumns.value = visibleColumns.value.filter(item => item !== column) } else visibleColumns.value = [...visibleColumns.value, column] }
function resetDataViewport() { dataScrollTop.value = 0; if (dataViewport.value) dataViewport.value.scrollTop = 0 }
function handleDataScroll(event) { dataScrollTop.value = event.target.scrollTop }
async function openView(view) {
  selectedTable.value = null; selectedView.value = view; busy.value = true
  try { const [detail, rows] = await Promise.all([api.view(activeDatabase.value.name, view.name), api.viewRows(activeDatabase.value.name, view.name)]); selectedViewDefinition.value = detail.definition; selectedViewRows.value = rows } catch (error) { fail(error) } finally { busy.value = false }
}
async function createDatabase() {
  busy.value = true
  try { await api.createDatabase(databaseForm); showDatabaseDialog.value = false; databaseForm.name = ''; await loadDatabases(); databaseListPage.value = databaseListTotalPages.value - 1; notify(`${databaseEntityLabel.value}已创建`) } catch (error) { fail(error) } finally { busy.value = false }
}
function openDatabaseInfoDialog(database) { Object.assign(databaseInfoForm, { name: database.name, charset: database.charset || 'utf8mb4', collation: database.collation || 'utf8mb4_unicode_ci' }); showDatabaseInfoDialog.value = true }
async function saveDatabaseInfo() { try { await api.updateDatabase(databaseInfoForm.name, databaseInfoForm); showDatabaseInfoDialog.value = false; await loadDatabases(); notify('数据库配置已更新') } catch (error) { fail(error) } }
async function deleteDatabase(database) {
  const message = isOracleConnection.value
    ? `危险操作：删除 Schema 用户 “${database.name}” 将执行 DROP USER CASCADE，并永久清空其中所有表、视图和对象。确定删除吗？`
    : `危险操作：删除数据库 “${database.name}” 将永久清空其中所有数据表。确定删除吗？`
  if (!confirm(message)) return
  try { await api.deleteDatabase(database.name); if (activeDatabase.value?.name === database.name) { activeDatabase.value = null; tables.value = []; selectedTable.value = null }; await loadDatabases(); normalizeDatabaseListPage(); notify(`${databaseEntityLabel.value}已删除`) } catch (error) { fail(error) }
}
async function createTable() {
  if (!activeDatabase.value) return
  busy.value = true
  try { await api.createTable(activeDatabase.value.name, tableForm); showTableDialog.value = false; Object.assign(tableForm, { name: '', comment: '', columns: [newColumn()] }); await selectDatabase(activeDatabase.value); notify('数据表已创建') } catch (error) { fail(error) } finally { busy.value = false }
}
function openTableInfoDialog(table) { Object.assign(tableInfoForm, { originalName: table.name, name: table.name, comment: table.comment || '' }); showTableInfoDialog.value = true }
async function saveTableInfo() { const wasSelected = selectedTable.value?.name === tableInfoForm.originalName; try { await api.updateTable(activeDatabase.value.name, tableInfoForm.originalName, { newName: tableInfoForm.name, comment: tableInfoForm.comment }); showTableInfoDialog.value = false; await selectDatabase(activeDatabase.value); const updated = tables.value.find(table => table.name === tableInfoForm.name); if (wasSelected && updated) await openTable(updated); notify('数据表信息已更新') } catch (error) { fail(error) } }
async function deleteTable(table) {
  if (!confirm(`危险操作：删除数据表 “${table.name}” 后不可恢复。确定删除吗？`)) return
  try { await api.deleteTable(activeDatabase.value.name, table.name); if (selectedTable.value?.name === table.name) selectedTable.value = null; await selectDatabase(activeDatabase.value); notify('数据表已删除') } catch (error) { fail(error) }
}
function openViewDialog(view = null) {
  editingView.value = view
  Object.assign(viewForm, view ? { name: view.name, selectSql: selectedViewDefinition.value.replace(/^CREATE.+?\sAS\s/i, '') } : { name: '', selectSql: '' })
  showViewDialog.value = true
}
async function saveView() {
  try { if (editingView.value) await api.updateView(activeDatabase.value.name, editingView.value.name, viewForm); else await api.createView(activeDatabase.value.name, viewForm); showViewDialog.value = false; await selectDatabase(activeDatabase.value); notify(editingView.value ? '视图已更新' : '视图已创建') } catch (error) { fail(error) }
}
async function deleteView(view) {
  if (!confirm(`危险操作：删除视图 “${view.name}” 后不可恢复。确定删除吗？`)) return
  try { await api.deleteView(activeDatabase.value.name, view.name); if (selectedView.value?.name === view.name) selectedView.value = null; await selectDatabase(activeDatabase.value); notify('视图已删除') } catch (error) { fail(error) }
}
function openIndexDialog(index = null) {
  editingIndex.value = index
  Object.assign(indexForm, index ? { name: index.name, unique: index.unique, columns: [...index.columns] } : { name: '', unique: false, columns: [] })
  showIndexDialog.value = true
}
async function saveIndex() {
  try { if (editingIndex.value) await api.updateIndex(activeDatabase.value.name, selectedTable.value.name, editingIndex.value.name, indexForm); else await api.createIndex(activeDatabase.value.name, selectedTable.value.name, indexForm); showIndexDialog.value = false; await openTable(selectedTable.value); notify(editingIndex.value ? '索引已更新' : '索引已创建') } catch (error) { fail(error) }
}
async function deleteIndex(index) {
  if (!confirm(`危险操作：删除索引 “${index.name}” 可能影响查询性能。确定删除吗？`)) return
  try { await api.deleteIndex(activeDatabase.value.name, selectedTable.value.name, index.name); await openTable(selectedTable.value); notify('索引已删除') } catch (error) { fail(error) }
}
function rowKey(row) {
  return Object.fromEntries(primaryColumns.value.map(column => [column.name, row[column.name]]))
}
function openRowDialog(row = null) {
  editingRow.value = row
  Object.keys(rowValues).forEach(key => delete rowValues[key])
  tableStructure.value.forEach(column => { rowValues[column.name] = row ? (row[column.name] ?? '') : (column.defaultValue ?? '') })
  showRowDialog.value = true
}
function editableRowValues() {
  const values = {}
  tableStructure.value.forEach(column => {
    const isPrimary = primaryColumns.value.some(primary => primary.name === column.name)
    if (editingRow.value && isPrimary) return
    if (!editingRow.value && column.extra?.toLowerCase().includes('auto_increment') && String(rowValues[column.name] ?? '') === '') return
    if (!editingRow.value && column.extra?.toLowerCase().includes('identity') && String(rowValues[column.name] ?? '') === '') return
    values[column.name] = rowValues[column.name] === '' && column.nullable === 'YES' ? null : rowValues[column.name]
  })
  return values
}
async function saveRow() {
  try {
    const values = editableRowValues()
    if (editingRow.value) await api.updateRow(activeDatabase.value.name, selectedTable.value.name, rowKey(editingRow.value), values)
    else await api.createRow(activeDatabase.value.name, selectedTable.value.name, values)
    showRowDialog.value = false; await loadRows(rowPage.value); notify(editingRow.value ? '记录已更新' : '记录已新增')
  } catch (error) { fail(error) }
}
async function deleteRow(row) {
  if (!canSafelyEditRows.value) return
  if (!confirm('危险操作：删除该记录后不可恢复。若记录被其他表引用，数据库会阻止删除并说明原因。确定继续吗？')) return
  try { await api.deleteRow(activeDatabase.value.name, selectedTable.value.name, rowKey(row)); await loadRows(rowPage.value); notify('记录已删除') } catch (error) { fail(error) }
}
function openColumnDialog(column = null) {
  editingColumn.value = column
  if (column) {
    const match = /^([a-z]+)(?:\(([^)]+)\))?/i.exec(column.type) || []
    Object.assign(columnForm, { name: column.name, type: (match[1] || 'varchar').toLowerCase(), length: match[2] || '', primaryKey: column.key === 'PRI', autoIncrement: (column.extra || '').includes('auto_increment'), nullable: column.nullable === 'YES', defaultValue: column.defaultValue ?? '', comment: column.comment || '' })
  } else Object.assign(columnForm, { name: '', type: 'varchar', length: '255', primaryKey: false, autoIncrement: false, nullable: true, defaultValue: '', comment: '' })
  showColumnDialog.value = true
}
async function saveColumn() {
  try {
    if (editingColumn.value) await api.updateColumn(activeDatabase.value.name, selectedTable.value.name, editingColumn.value.name, columnForm)
    else await api.addColumn(activeDatabase.value.name, selectedTable.value.name, columnForm)
    showColumnDialog.value = false; await openTable(selectedTable.value); notify(editingColumn.value ? '字段已更新' : '字段已新增')
  } catch (error) { fail(error) }
}
async function deleteColumn(column) {
  if (!confirm(`危险操作：删除字段 “${column.name}” 可能导致数据丢失。确定删除吗？`)) return
  try { await api.deleteColumn(activeDatabase.value.name, selectedTable.value.name, column.name); await openTable(selectedTable.value); notify('字段已删除') } catch (error) { fail(error) }
}
function addColumn() { tableForm.columns.push({ name: '', type: 'varchar', length: '255', primaryKey: false, autoIncrement: false, nullable: true, defaultValue: '', comment: '' }) }
function removeColumn(index) { if (tableForm.columns.length > 1) tableForm.columns.splice(index, 1) }
onMounted(async () => { try { const session = await api.session(); if (!session.authenticated) return; authenticated.value = true; currentUser.value = session.username; const status = await api.status(); if (status.connected) { Object.assign(connection, { host: status.host, port: status.port, username: status.username, password: '', databaseType: status.type || 'MYSQL', databaseName: status.databaseName || '' }); connected.value = true; await loadDatabases({ disconnectOnError: false, quiet: true }) } } catch { /* 后端未启动时留在登录页 */ } })
</script>

<template>
  <main v-if="!authenticated" class="connect-page auth-page">
    <section class="connect-intro"><div class="brand-mark">M</div><p class="section-label">MYSQLADMIN · DATABASE WORKSPACE</p><h1>数据库管理，保持清晰与可控。</h1><p>使用独立工作账号建立受控连接，在同一处完成数据库、表结构和数据记录的日常管理。</p><div class="intro-points"><span>独立会话</span><span>密码哈希</span><span>操作受控</span></div></section>
    <section class="connect-panel" aria-labelledby="auth-title"><p class="section-label">{{ authMode === 'login' ? 'WELCOME BACK' : 'CREATE ACCOUNT' }}</p><h2 id="auth-title">{{ authMode === 'login' ? '登录 Mysqladmin' : '创建你的工作账号' }}</h2><p class="hint">{{ authMode === 'login' ? '登录后再连接需要管理的数据库实例。' : '用户名使用 3-32 位字母、数字、下划线或短横线。' }}</p><form @submit.prevent="authenticate" class="connection-form"><label>用户名<input v-model.trim="authForm.username" required minlength="3" maxlength="32" autocomplete="username" placeholder="例如 admin"></label><label>密码<input v-model="authForm.password" required minlength="6" maxlength="72" type="password" autocomplete="current-password" placeholder="至少 6 位"></label><button class="primary-button" :disabled="authBusy">{{ authBusy ? '正在处理…' : authMode === 'login' ? '登录并继续' : '注册并继续' }} <span aria-hidden="true">→</span></button></form><p class="auth-switch">{{ authMode === 'login' ? '还没有账号？' : '已有账号？' }} <button @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '去注册' : '去登录' }}</button></p></section>
  </main>

  <main v-else class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand"><div class="brand-mark small">M</div><span>Mysqladmin</span><small>LOCAL</small></div>
      <div :class="['connection-state', { disconnected: !connected }]"><button class="connection-card" @click="openServerManager"><span :class="['status-dot', { offline: !connected }]"></span><span><strong>{{ connected ? '已连接 · ' + databaseTypeLabel(connection.databaseType) : '未连接服务端' }}</strong><small>{{ connected ? connection.host + ':' + connection.port : '请选择或切换数据库服务端' }}</small></span><span class="connection-edit">管理</span></button><button v-if="connected" class="icon-button" title="断开连接" @click="disconnect">⌁</button></div>
      <div class="sidebar-title"><span>{{ databaseListLabel }}</span><button class="icon-button" :title="databaseCreateTitle" @click="showDatabaseDialog = true">＋</button></div>
      <nav class="database-list" :aria-label="databaseEntityLabel + '列表'">
        <div v-for="database in pagedUserDatabases" :key="database.name" :class="['database-item', { active: activeDatabase?.name === database.name }]">
          <button @click="selectDatabase(database)"><span class="db-glyph">◈</span><span>{{ database.name }}</span></button>
          <div class="item-actions"><button v-if="!isOracleConnection" title="修改数据库配置" @click="openDatabaseInfoDialog(database)">···</button><button :title="'删除' + databaseEntityLabel" @click="deleteDatabase(database)">×</button></div>
        </div>
        <p v-if="!connected" class="empty-side">连接服务端后显示{{ databaseEntityLabel }}列表</p><p v-else-if="!userDatabases.length" class="empty-side">{{ databaseEmptyMessage }}</p>
        <div v-if="connected && userDatabases.length > databaseListPageSize" class="database-list-pager">
          <button :disabled="databaseListPage === 0" title="上一页" @click="previousDatabaseListPage">‹</button>
          <span>第 {{ databaseListPage + 1 }} / {{ databaseListTotalPages }} 页</span>
          <button :disabled="databaseListPage >= databaseListTotalPages - 1" title="下一页" @click="nextDatabaseListPage">›</button>
        </div>
      </nav>
      <div class="sidebar-footer">
        <div :class="['account-menu', { open: showUserMenu }]">
          <button class="account-trigger" type="button" aria-haspopup="menu" :aria-expanded="showUserMenu" @click="showUserMenu = !showUserMenu">
            <span class="user-avatar">{{ currentUser.slice(0, 1).toUpperCase() }}</span>
            <span class="account-copy">
              <strong>{{ currentUser }}</strong>
              <small>{{ connected ? databaseTypeLabel(connection.databaseType) + ' · ' + userDatabases.length + databaseUnitLabel : '未连接服务端' }}</small>
            </span>
            <span class="account-chevron" aria-hidden="true">⌃</span>
          </button>
          <div v-if="showUserMenu" class="account-popover" role="menu">
            <div class="account-popover-head">
              <span class="user-avatar">{{ currentUser.slice(0, 1).toUpperCase() }}</span>
              <span><strong>{{ currentUser }}</strong><small>本地工作账号</small></span>
            </div>
            <button class="account-popover-action" role="menuitem" type="button" @click="openServerManager">管理数据库服务端</button>
            <button class="account-popover-action danger-text" role="menuitem" type="button" @click="logout">退出登录</button>
          </div>
        </div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar"><div class="workspace-heading"><p class="section-label">{{ !connected ? 'DATABASE SERVER' : activeDatabase ? (isOracleConnection ? 'SCHEMA / ' : 'DATABASE / ') + activeDatabase.name : databaseTypeLabel(connection.databaseType).toUpperCase() + ' INSTANCE' }}</p><div class="heading-line"><h1>{{ !connected ? '尚未连接数据库服务端' : activeDatabase?.name || databaseSelectPrompt }}</h1><span v-if="connected" class="server-pill"><i></i>{{ databaseTypeLabel(connection.databaseType) }} · {{ connection.host }}:{{ connection.port }}</span></div></div><div class="top-actions"><button class="secondary-button" :disabled="!connected" @click="loadDatabases">↻ 刷新</button><button class="primary-button" :disabled="!activeDatabase" @click="showTableDialog = true">＋ 新建数据表</button></div></header>

      <section v-if="!connected" class="welcome-state"><div class="welcome-symbol">◌</div><h2>先选择要管理的数据库服务端</h2><p>在左侧“数据库服务端”中填写连接信息。验证通过后，数据库列表会显示在这里，无需离开工作台。</p><button class="primary-button" @click="openServerManager">管理数据库服务端</button></section>
      <section v-else-if="!activeDatabase" class="welcome-state"><div class="welcome-symbol">◈</div><h2>{{ databaseSelectPrompt }}，或创建一个新的工作空间</h2><p>{{ databaseContextCopy }}</p><button class="primary-button" @click="showDatabaseDialog = true">{{ databaseCreateAction }}</button></section>

      <template v-else>
        <section class="database-summary"><template v-if="isOracleConnection"><div><span>Schema</span><strong>{{ activeDatabase.name }}</strong></div><div><span>Service</span><strong>{{ connection.databaseName || 'FREEPDB1' }}</strong></div></template><template v-else><div><span>字符集</span><strong>{{ activeDatabase.charset }}</strong></div><div><span>排序规则</span><strong>{{ activeDatabase.collation }}</strong></div></template><div><span>数据表</span><strong>{{ tables.length }}</strong></div><div><span>视图</span><strong>{{ views.length }}</strong></div><p>{{ databaseSummaryCopy }}</p></section>
        <nav class="object-tabs" aria-label="数据库对象"><button :class="{ active: databasePanel === 'tables' }" @click="databasePanel = 'tables'; selectedView = null">数据表 <span>{{ tables.length }}</span></button><button :class="{ active: databasePanel === 'views' }" @click="databasePanel = 'views'; selectedTable = null">视图 <span>{{ views.length }}</span></button></nav>
        <section v-if="!selectedTable && !selectedView" class="content-section"><div class="section-head"><div><h2>{{ databasePanel === 'tables' ? '数据表' : '视图' }}</h2><p class="object-summary">{{ databasePanel === 'tables' ? tables.length + ' 个对象 · 选择数据表查看字段与数据' : views.length + ' 个视图 · 查看定义与查询结果' }}</p></div><button class="text-button" @click="databasePanel === 'tables' ? showTableDialog = true : openViewDialog()">{{ databasePanel === 'tables' ? '新建表 →' : '新建视图 →' }}</button></div>
          <div v-if="databasePanel === 'tables' && tables.length" class="table-list">
            <div v-for="table in tables" :key="table.name" class="table-row"><button class="table-main" @click="openTable(table)"><span class="table-glyph">▦</span><span><strong>{{ table.name }}</strong><small>{{ table.comment || '暂无表注释' }}</small></span></button><span class="table-meta">{{ table.engine || '—' }}</span><span class="table-meta">约 {{ table.rows }} 行</span><div class="row-actions"><button @click="openTableInfoDialog(table)">编辑</button><button class="danger-text" @click="deleteTable(table)">删除</button></div></div>
          </div>
          <div v-else-if="databasePanel === 'views' && views.length" class="table-list"><div v-for="view in views" :key="view.name" class="table-row"><button class="table-main" @click="openView(view)"><span class="table-glyph view-glyph">◫</span><span><strong>{{ view.name }}</strong><small>{{ view.updatable === 'YES' ? '可更新视图' : '只读视图' }} · {{ view.checkOption || 'NONE' }}</small></span></button><span class="table-meta">{{ view.definer || '—' }}</span><span class="table-meta">VIEW</span><div class="row-actions"><button @click="openView(view).then(() => openViewDialog(view))">编辑</button><button class="danger-text" @click="deleteView(view)">删除</button></div></div></div>
          <div v-else class="empty-state"><span>{{ databasePanel === 'tables' ? '▦' : '◫' }}</span><h3>{{ databasePanel === 'tables' ? '这个' + databaseEntityLabel + '还是空的' : '还没有创建视图' }}</h3><p>{{ databasePanel === 'tables' ? '创建第一张表，定义字段、主键和数据类型。' : '用 SELECT 查询组合已有数据表，建立可复用的数据视图。' }}</p><button class="secondary-button" @click="databasePanel === 'tables' ? showTableDialog = true : openViewDialog()">{{ databasePanel === 'tables' ? '创建数据表' : '创建视图' }}</button></div>
        </section>
        <section v-else-if="selectedTable" class="content-section detail-section"><div class="section-head"><div><button class="back-button" @click="selectedTable = null">← 返回表列表</button><h2>{{ selectedTable.name }}</h2><p>{{ selectedTable.comment || '暂无表注释' }}</p></div><div class="top-actions"><button v-if="activeTab === 'indexes'" class="secondary-button" @click="openIndexDialog()">＋ 新建索引</button><button class="secondary-button" @click="openColumnDialog()">＋ 新增字段</button><button class="secondary-button" @click="openTableInfoDialog(selectedTable)">编辑表信息</button><button class="secondary-button" @click="openTable(selectedTable)">↻ 刷新</button></div></div>
          <div class="tabs" role="tablist"><button :class="{ active: activeTab === 'structure' }" @click="activeTab = 'structure'">表结构 <span>{{ tableStructure.length }}</span></button><button :class="{ active: activeTab === 'data' }" @click="activeTab = 'data'">数据预览 <span>{{ tableRows.length }}</span></button><button :class="{ active: activeTab === 'indexes' }" @click="activeTab = 'indexes'">索引 <span>{{ indexes.length }}</span></button></div>
          <div v-if="busy" class="skeleton-block"><i></i><i></i><i></i><i></i></div>
          <div v-else-if="activeTab === 'structure'" class="data-table-wrap"><table><thead><tr><th>字段</th><th>类型</th><th>可空</th><th>键</th><th>默认值</th><th>属性</th><th>注释</th><th>操作</th></tr></thead><tbody><tr v-for="column in tableStructure" :key="column.name"><td><code>{{ column.name }}</code></td><td>{{ column.type }}</td><td>{{ column.nullable }}</td><td><span v-if="column.key" class="key-badge">{{ column.key }}</span></td><td>{{ column.defaultValue ?? '—' }}</td><td>{{ column.extra || '—' }}</td><td>{{ column.comment || '—' }}</td><td class="field-actions"><button @click="openColumnDialog(column)">编辑</button><button class="danger-text" @click="deleteColumn(column)">删除</button></td></tr></tbody></table></div>
          <div v-else-if="activeTab === 'data'" class="data-workspace"><div class="data-toolbar data-toolbar-rich"><div><strong>完整数据</strong><span>第 {{ rowPage + 1 }} 页 · 每页 {{ rowPageSize }} 条 · 当前 {{ tableRows.length }} 条</span></div><div class="top-actions"><button class="secondary-button" @click="loadRows(rowPage)">↻ 刷新</button><button class="primary-button" @click="openRowDialog()">＋ 新增记录</button></div></div><div class="data-controls"><label class="data-search"><span>⌕</span><input v-model.trim="rowKeyword" @keyup.enter="applyRowQuery" placeholder="输入关键词"></label><button class="primary-button data-search-button" @click="applyRowQuery">搜索</button><select v-model="rowFilterColumn" @change="applyRowQuery" aria-label="筛选字段"><option value="">全部字段</option><option v-for="header in rowHeaders" :key="header" :value="header">{{ header }}</option></select><select v-model="rowSortBy" @change="applyRowQuery" aria-label="排序字段"><option value="">默认排序</option><option v-for="header in rowHeaders" :key="header" :value="header">按 {{ header }} 排序</option></select><button class="secondary-button sort-button" :disabled="!rowSortBy" @click="toggleSortDirection">{{ rowSortDirection === 'ASC' ? '↑ 升序' : '↓ 降序' }}</button><select v-model.number="rowPageSize" @change="changePageSize" aria-label="每页条数"><option :value="25">25 / 页</option><option :value="50">50 / 页</option><option :value="100">100 / 页</option><option :value="250">250 / 页</option><option :value="500">500 / 页</option></select><div class="column-settings"><button class="secondary-button" @click="showColumnPicker = !showColumnPicker">▤ 列显示</button><div v-if="showColumnPicker" class="column-picker"><strong>显示字段</strong><label v-for="header in rowHeaders" :key="header"><input type="checkbox" :checked="visibleColumns.includes(header)" @change="toggleColumn(header)">{{ header }}</label></div></div></div><p v-if="!canSafelyEditRows" class="data-guard">此表未定义主键：仍可新增记录；为避免误更新或误删除，编辑和删除已禁用。请先在“表结构”中设置主键。</p><div ref="dataViewport" class="data-table-wrap data-scroll" @scroll="handleDataScroll"><table v-if="tableRows.length"><thead><tr><th v-for="header in visibleRowHeaders" :key="header">{{ header }}</th><th v-if="canSafelyEditRows">操作</th></tr></thead><tbody><tr v-if="virtualTopHeight" class="virtual-spacer"><td :colspan="dataColumnSpan" :style="{ height: virtualTopHeight + 'px' }"></td></tr><tr v-for="(row, index) in virtualRows" :key="virtualStart + index"><td v-for="header in visibleRowHeaders" :key="header">{{ row[header] ?? 'NULL' }}</td><td v-if="canSafelyEditRows" class="field-actions"><button @click="openRowDialog(row)">编辑</button><button class="danger-text" @click="deleteRow(row)">删除</button></td></tr><tr v-if="virtualBottomHeight" class="virtual-spacer"><td :colspan="dataColumnSpan" :style="{ height: virtualBottomHeight + 'px' }"></td></tr></tbody></table><div v-else class="empty-state compact"><span>∅</span><h3>没有符合条件的记录</h3><p>调整筛选条件，或新增一条记录。</p></div></div><div class="pager"><button class="secondary-button" :disabled="rowPage === 0" @click="loadRows(rowPage - 1)">← 上一页</button><span>第 {{ rowPage + 1 }} 页</span><button class="secondary-button" :disabled="tableRows.length < rowPageSize" @click="loadRows(rowPage + 1)">下一页 →</button></div></div>
          <div v-else class="data-table-wrap"><table><thead><tr><th>索引名称</th><th>类型</th><th>字段</th><th>属性</th><th>操作</th></tr></thead><tbody><tr v-for="index in indexes" :key="index.name"><td><code>{{ index.name }}</code></td><td>{{ index.type }}</td><td>{{ index.columns.join(', ') }}</td><td><span :class="['key-badge', { neutral: !index.unique }]">{{ index.name === 'PRIMARY' ? 'PRIMARY' : index.unique ? 'UNIQUE' : 'NORMAL' }}</span></td><td class="field-actions"><button v-if="index.name !== 'PRIMARY'" @click="openIndexDialog(index)">编辑</button><button v-if="index.name !== 'PRIMARY'" class="danger-text" @click="deleteIndex(index)">删除</button><span v-else>由主键维护</span></td></tr></tbody></table><div v-if="!indexes.length" class="empty-state compact"><span>⌁</span><h3>暂无索引</h3><p>为常用查询字段创建普通索引或唯一索引。</p></div></div>
        </section>
        <section v-else class="content-section detail-section"><div class="section-head"><div><button class="back-button" @click="selectedView = null">← 返回视图列表</button><h2>{{ selectedView.name }}</h2><p>{{ selectedView.updatable === 'YES' ? '可更新视图' : '只读视图' }}</p></div><div class="top-actions"><button class="secondary-button" @click="openViewDialog(selectedView)">编辑视图</button><button class="secondary-button" @click="openView(selectedView)">↻ 刷新</button></div></div><div v-if="busy" class="skeleton-block"><i></i><i></i><i></i></div><template v-else><h3 class="definition-title">视图定义</h3><pre class="sql-definition">{{ selectedViewDefinition }}</pre><h3 class="definition-title">数据预览</h3><div class="data-table-wrap"><table v-if="selectedViewRows.length"><thead><tr><th v-for="header in Object.keys(selectedViewRows[0])" :key="header">{{ header }}</th></tr></thead><tbody><tr v-for="(row, index) in selectedViewRows" :key="index"><td v-for="header in Object.keys(selectedViewRows[0])" :key="header">{{ row[header] ?? 'NULL' }}</td></tr></tbody></table><div v-else class="empty-state compact"><span>∅</span><h3>视图未返回数据</h3><p>检查视图的筛选条件或源表数据。</p></div></div></template></section>
      </template>
    </section>
  </main>

  <div v-if="showDatabaseDialog || showDatabaseInfoDialog || showTableDialog || showTableInfoDialog || showColumnDialog || showServerDialog || showViewDialog || showIndexDialog || showRowDialog" class="modal-backdrop" @click.self="showDatabaseDialog = showDatabaseInfoDialog = showTableDialog = showTableInfoDialog = showColumnDialog = showServerDialog = showViewDialog = showIndexDialog = showRowDialog = false"><section class="modal" role="dialog" aria-modal="true"><header><div><p class="section-label">{{ showServerDialog ? 'DATABASE SERVER' : showDatabaseInfoDialog ? 'DATABASE SETTINGS' : showDatabaseDialog ? (isOracleConnection ? 'NEW SCHEMA' : 'NEW DATABASE') : showTableInfoDialog ? 'TABLE SETTINGS' : showTableDialog ? 'NEW TABLE' : showColumnDialog ? 'TABLE COLUMN' : showViewDialog ? 'DATABASE VIEW' : showIndexDialog ? 'TABLE INDEX' : 'TABLE DATA' }}</p><h2>{{ showServerDialog ? '数据库服务端' : showDatabaseInfoDialog ? '编辑数据库配置' : showDatabaseDialog ? databaseCreateTitle : showTableInfoDialog ? '编辑表信息' : showTableDialog ? '创建数据表' : showColumnDialog ? editingColumn ? '编辑字段' : '新增字段' : showViewDialog ? editingView ? '编辑视图' : '创建视图' : showIndexDialog ? editingIndex ? '编辑索引' : '创建索引' : editingRow ? '编辑记录' : '新增记录' }}</h2></div><button class="icon-button" @click="showDatabaseDialog = showDatabaseInfoDialog = showTableDialog = showTableInfoDialog = showColumnDialog = showServerDialog = showViewDialog = showIndexDialog = showRowDialog = false">×</button></header>
    <form v-if="showServerDialog" @submit.prevent="switchServer" class="dialog-form server-form">
      <p class="server-dialog-note">选择数据库类型并填写服务端连接信息。验证成功后，将切换到新的实例。</p>
      <fieldset class="database-type-field">
        <legend>数据库类型</legend>
        <div class="database-type-options">
          <label v-for="option in databaseTypeOptions" :key="option.value" :class="['database-type-option', { selected: serverForm.databaseType === option.value }]">
            <input v-model="serverForm.databaseType" type="radio" name="databaseType" :value="option.value" @change="changeDatabaseType">
            <span class="database-type-mark" aria-hidden="true">{{ option.mark }}</span>
            <span class="database-type-copy"><strong>{{ option.label }}</strong><small>默认端口 {{ option.meta }}</small></span>
            <span class="database-type-check" aria-hidden="true">✓</span>
          </label>
        </div>
      </fieldset>
      <label>主机地址<input v-model.trim="serverForm.host" required autocomplete="off" placeholder="127.0.0.1"></label>
      <div class="two-fields"><label>端口<input v-model.number="serverForm.port" type="number" min="1" max="65535" required></label><label>账号<input v-model.trim="serverForm.username" required autocomplete="username"></label></div>
      <label v-if="serverDatabaseFieldVisible">{{ serverDatabaseFieldLabel }}<input v-model.trim="serverForm.databaseName" :required="serverDatabaseFieldRequired" autocomplete="off" :placeholder="serverDatabaseFieldPlaceholder"></label>
      <label>密码<input v-model="serverForm.password" required type="password" autocomplete="current-password" :placeholder="`请输入 ${databaseTypeLabel(serverForm.databaseType)} 账号密码`"></label>
      <footer><button type="button" class="secondary-button" @click="showServerDialog = false">取消</button><button class="primary-button" :disabled="connecting">{{ connecting ? '正在验证…' : '验证并切换服务端' }}</button></footer>
    </form>
    <form v-else-if="showDatabaseInfoDialog" @submit.prevent="saveDatabaseInfo" class="dialog-form"><p class="server-dialog-note">修改字符集或排序规则可能影响已有数据的比较和排序结果，请确认后保存。</p><label>数据库名称<input :value="databaseInfoForm.name" disabled></label><div class="two-fields"><label>字符集<select v-model="databaseInfoForm.charset"><option>utf8mb4</option><option>utf8</option></select></label><label>排序规则<select v-model="databaseInfoForm.collation"><option>utf8mb4_unicode_ci</option><option>utf8mb4_general_ci</option></select></label></div><footer><button type="button" class="secondary-button" @click="showDatabaseInfoDialog = false">取消</button><button class="primary-button">保存配置</button></footer></form>
    <form v-else-if="showDatabaseDialog" @submit.prevent="createDatabase" class="dialog-form"><p v-if="isOracleConnection" class="server-dialog-note">Oracle 会创建同名 Schema 用户，并授予建表、视图、序列、触发器等常用对象权限。这个 Schema 就是后续维护表和数据的工作空间。</p><label>{{ databaseNameLabel }}<input v-model.trim="databaseForm.name" required pattern="[A-Za-z0-9_$]+" :placeholder="databaseNamePlaceholder"></label><div v-if="!isOracleConnection" class="two-fields"><label>字符集<select v-model="databaseForm.charset"><option>utf8mb4</option><option>utf8</option></select></label><label>排序规则<select v-model="databaseForm.collation"><option>utf8mb4_unicode_ci</option><option>utf8mb4_general_ci</option></select></label></div><footer><button type="button" class="secondary-button" @click="showDatabaseDialog = false">取消</button><button class="primary-button" :disabled="busy">{{ databaseCreateAction }}</button></footer></form>
    <form v-else-if="showTableInfoDialog" @submit.prevent="saveTableInfo" class="dialog-form"><p class="server-dialog-note">可修改数据表名称与说明。重命名不会改变字段和既有数据。</p><label>表名称<input v-model.trim="tableInfoForm.name" required pattern="[A-Za-z0-9_$]+" placeholder="例如 users"></label><label>表注释<input v-model.trim="tableInfoForm.comment" placeholder="说明该表的业务用途"></label><footer><button type="button" class="secondary-button" @click="showTableInfoDialog = false">取消</button><button class="primary-button">保存表信息</button></footer></form>
    <form v-else-if="showTableDialog" @submit.prevent="createTable" class="dialog-form"><label>表名称<input v-model.trim="tableForm.name" required pattern="[A-Za-z0-9_$]+" placeholder="例如 users"></label><label>表注释<input v-model.trim="tableForm.comment" placeholder="例如 用户信息"></label><div class="columns-editor"><div class="columns-head"><strong>字段定义</strong><button type="button" class="text-button" @click="addColumn">＋ 添加字段</button></div><div v-for="(column, index) in tableForm.columns" :key="index" class="column-edit"><input v-model.trim="column.name" required placeholder="字段名"><select v-model="column.type"><option>bigint</option><option>int</option><option>varchar</option><option>text</option><option>decimal</option><option>datetime</option><option>timestamp</option><option>boolean</option></select><input v-model.trim="column.length" placeholder="长度"><label><input v-model="column.primaryKey" type="checkbox">主键</label><label><input v-model="column.autoIncrement" type="checkbox">自增</label><label><input v-model="column.nullable" type="checkbox">可空</label><button type="button" class="icon-button" :disabled="tableForm.columns.length === 1" @click="removeColumn(index)">×</button></div></div><footer><button type="button" class="secondary-button" @click="showTableDialog = false">取消</button><button class="primary-button" :disabled="busy">创建数据表</button></footer></form>
    <form v-else-if="showColumnDialog" @submit.prevent="saveColumn" class="dialog-form"><label>字段名称<input v-model.trim="columnForm.name" required pattern="[A-Za-z0-9_$]+" placeholder="例如 email"></label><div class="two-fields"><label>数据类型<select v-model="columnForm.type"><option>bigint</option><option>int</option><option>varchar</option><option>text</option><option>decimal</option><option>datetime</option><option>timestamp</option><option>boolean</option></select></label><label>长度 / 精度<input v-model.trim="columnForm.length" placeholder="例如 255 或 10,2"></label></div><label>默认值<input v-model.trim="columnForm.defaultValue" placeholder="留空则不设默认值"></label><label>字段注释<input v-model.trim="columnForm.comment" placeholder="说明字段用途"></label><div class="field-checks"><label><input v-model="columnForm.primaryKey" type="checkbox">主键</label><label><input v-model="columnForm.autoIncrement" type="checkbox">自增</label><label><input v-model="columnForm.nullable" type="checkbox">允许为空</label></div><footer><button type="button" class="secondary-button" @click="showColumnDialog = false">取消</button><button class="primary-button">{{ editingColumn ? '保存字段' : '新增字段' }}</button></footer></form>
    <form v-else-if="showViewDialog" @submit.prevent="saveView" class="dialog-form"><p class="server-dialog-note">当前{{ databaseEntityLabel }}已作为 SQL 执行上下文。输入单条 SELECT 或 WITH 查询，并使用当前空间中真实存在的表名，例如 <code>SELECT * FROM users</code>；不允许多条语句。</p><label>视图名称<input v-model.trim="viewForm.name" required pattern="[A-Za-z0-9_$]+" placeholder="例如 active_users_view"></label><label>查询 SQL<textarea v-model="viewForm.selectSql" class="sql-input" required spellcheck="false" placeholder="SELECT id, name FROM users WHERE status = 'ACTIVE'"></textarea></label><footer><button type="button" class="secondary-button" @click="showViewDialog = false">取消</button><button class="primary-button">{{ editingView ? '保存视图' : '创建视图' }}</button></footer></form>
    <form v-else-if="showIndexDialog" @submit.prevent="saveIndex" class="dialog-form"><label>索引名称<input v-model.trim="indexForm.name" required pattern="[A-Za-z0-9_$]+" placeholder="例如 idx_user_email"></label><label class="checkbox-line"><input v-model="indexForm.unique" type="checkbox">创建唯一索引</label><fieldset class="index-columns"><legend>索引字段（至少选择一个）</legend><label v-for="column in tableStructure" :key="column.name" class="checkbox-line"><input v-model="indexForm.columns" type="checkbox" :value="column.name">{{ column.name }} <small>{{ column.type }}</small></label></fieldset><footer><button type="button" class="secondary-button" @click="showIndexDialog = false">取消</button><button class="primary-button">{{ editingIndex ? '保存索引' : '创建索引' }}</button></footer></form>
    <form v-else @submit.prevent="saveRow" class="dialog-form"><p class="server-dialog-note">{{ editingRow ? '主键字段用于安全定位记录，不能在此直接修改。' : '留空的自增字段会由数据库自动生成。' }}</p><div class="row-editor"><label v-for="column in tableStructure" :key="column.name">{{ column.name }}<small>{{ column.type }}{{ column.nullable === 'YES' ? ' · 可空' : '' }}</small><input v-model="rowValues[column.name]" :disabled="editingRow && column.key === 'PRI'" :required="!editingRow && column.nullable !== 'YES' && !column.extra?.toLowerCase().includes('auto_increment') && !column.extra?.toLowerCase().includes('identity')" :placeholder="column.defaultValue ?? ''"></label></div><footer><button type="button" class="secondary-button" @click="showRowDialog = false">取消</button><button class="primary-button">{{ editingRow ? '保存修改' : '新增记录' }}</button></footer></form>
  </section></div>
  <div v-if="toast" :class="['toast', toast.tone]">{{ toast.tone === 'success' ? '✓' : '!' }} {{ toast.message }}</div>
</template>
