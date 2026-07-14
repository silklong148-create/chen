const request = async (url, options = {}) => {
  const response = await fetch(url, { credentials: 'include', headers: { 'Content-Type': 'application/json' }, ...options })
  const payload = await response.json().catch(() => ({ message: '服务返回了无效响应' }))
  if (!response.ok || !payload.success) throw new Error(payload.message || '请求失败')
  return payload.data
}

export const api = {
  register: (data) => request('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  session: () => request('/api/auth/session'),
  status: () => request('/api/connections/status'),
  connect: (data) => request('/api/connections/test', { method: 'POST', body: JSON.stringify(data) }),
  disconnect: () => request('/api/connections', { method: 'DELETE' }),
  databases: () => request('/api/databases'),
  createDatabase: (data) => request('/api/databases', { method: 'POST', body: JSON.stringify(data) }),
  updateDatabase: (name, data) => request(`/api/databases/${name}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteDatabase: (name) => request(`/api/databases/${name}`, { method: 'DELETE' }),
  tables: (database) => request(`/api/databases/${database}/tables`),
  createTable: (database, data) => request(`/api/databases/${database}/tables`, { method: 'POST', body: JSON.stringify(data) }),
  updateTable: (database, table, data) => request(`/api/databases/${database}/tables/${table}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteTable: (database, table) => request(`/api/databases/${database}/tables/${table}`, { method: 'DELETE' }),
  addColumn: (database, table, data) => request(`/api/databases/${database}/tables/${table}/columns`, { method: 'POST', body: JSON.stringify(data) }),
  updateColumn: (database, table, column, data) => request(`/api/databases/${database}/tables/${table}/columns/${column}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteColumn: (database, table, column) => request(`/api/databases/${database}/tables/${table}/columns/${column}`, { method: 'DELETE' }),
  structure: (database, table) => request(`/api/databases/${database}/tables/${table}/structure`),
  rows: (database, table, { limit = 100, offset = 0, keyword = '', filterColumn = '', sortBy = '', sortDirection = 'ASC' } = {}) => {
    const query = new URLSearchParams({ limit, offset, sortDirection })
    if (keyword.trim()) query.set('keyword', keyword.trim())
    if (filterColumn) query.set('filterColumn', filterColumn)
    if (sortBy) query.set('sortBy', sortBy)
    return request(`/api/databases/${database}/tables/${table}/rows?${query}`)
  },
  createRow: (database, table, values) => request(`/api/databases/${database}/tables/${table}/rows`, { method: 'POST', body: JSON.stringify({ values }) }),
  updateRow: (database, table, key, values) => request(`/api/databases/${database}/tables/${table}/rows`, { method: 'PUT', body: JSON.stringify({ key, values }) }),
  deleteRow: (database, table, key) => request(`/api/databases/${database}/tables/${table}/rows`, { method: 'DELETE', body: JSON.stringify({ key, values: key }) }),
  views: (database) => request(`/api/databases/${database}/views`),
  view: (database, view) => request(`/api/databases/${database}/views/${view}`),
  createView: (database, data) => request(`/api/databases/${database}/views`, { method: 'POST', body: JSON.stringify(data) }),
  updateView: (database, view, data) => request(`/api/databases/${database}/views/${view}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteView: (database, view) => request(`/api/databases/${database}/views/${view}`, { method: 'DELETE' }),
  viewRows: (database, view) => request(`/api/databases/${database}/views/${view}/rows`),
  indexes: (database, table) => request(`/api/databases/${database}/tables/${table}/indexes`),
  createIndex: (database, table, data) => request(`/api/databases/${database}/tables/${table}/indexes`, { method: 'POST', body: JSON.stringify(data) }),
  updateIndex: (database, table, index, data) => request(`/api/databases/${database}/tables/${table}/indexes/${index}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteIndex: (database, table, index) => request(`/api/databases/${database}/tables/${table}/indexes/${index}`, { method: 'DELETE' })
}
