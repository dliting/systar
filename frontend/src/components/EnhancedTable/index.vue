<template>
  <div class="enhanced-table" :class="{ 'enhanced-table--dark': theme === 'dark' }">
    <!-- Toolbar for export button -->
    <div v-if="exportable" class="enhanced-table__toolbar">
      <el-button size="small" @click="handleExport">
        <el-icon><Download /></el-icon> 导出 CSV
      </el-button>
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="filteredData"
      :row-key="rowKey"
      :default-expand-all="defaultExpandAll"
      :row-class-name="rowClassName"
      :max-height="maxHeight"
      :class="tableClass"
      @sort-change="onSortChange"
      @row-click="(row) => emit('row-click', row)"
      @row-dblclick="(row) => emit('row-dblclick', row)"
      @selection-change="(rows) => emit('selection-change', rows)"
      @row-contextmenu="onContextMenu"
      @expand-change="(row, expandedRows) => emit('expand-change', row, expandedRows)"
    >
      <!-- Selection column -->
      <el-table-column v-if="selectable" type="selection" width="50" align="center" />

      <!-- Expand column (only if expanded-row slot provided) -->
      <el-table-column v-if="$slots['expanded-row']" type="expand">
        <template #default="scope">
          <slot name="expanded-row" v-bind="scope" />
        </template>
      </el-table-column>

      <!-- Data columns -->
      <el-table-column
        v-for="col in dataColumns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align"
        :fixed="col.fixed"
        :show-overflow-tooltip="col.showOverflowTooltip"
        :sortable="col.sortable"
      >
        <!-- Filter header (replaces default header) -->
        <template #header v-if="col.filterable">
          <div class="enhanced-table__filter-header">
            <span>{{ col.label }}</span>
            <el-popover trigger="click" :width="180">
              <template #reference>
                <el-icon class="filter-icon" :class="{ 'is-active': isFilterActive(col.prop) }">
                  <Filter />
                </el-icon>
              </template>
              <div class="enhanced-table__filter-panel">
                <el-checkbox-group v-model="filterState[col.prop]">
                  <el-checkbox v-for="opt in col.filterOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </el-checkbox>
                </el-checkbox-group>
                <div class="filter-actions">
                  <el-button size="small" text @click="resetFilter(col.prop)">重置</el-button>
                  <el-button size="small" type="primary" @click="applyFilter(col.prop)">确定</el-button>
                </div>
              </div>
            </el-popover>
          </div>
        </template>
        <!-- Custom slot column -->
        <template #default="scope" v-if="col.slot">
          <slot :name="`column-${col.prop}`" v-bind="scope" />
        </template>
      </el-table-column>
    </el-table>

    <!-- Right-click context menu (teleported to body) -->
    <teleport to="body">
      <transition name="context-menu">
        <div
          v-if="menuVisible"
          class="enhanced-table__context-menu"
          :class="{ 'is-dark': theme === 'dark' }"
          :style="menuStyle"
          @click.stop
        >
          <template v-for="(item, i) in visibleItems" :key="item.command">
            <div v-if="i > 0 && item.divided" class="context-menu-divider"></div>
            <div
              class="context-menu-item"
              :class="{ 'is-danger': item.danger }"
              @click="onMenuClick(item.command)"
            >
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </template>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, reactive } from 'vue'
import { Download, Filter } from '@element-plus/icons-vue'

const BOM_PREFIX = '﻿'
const CSV_SEPARATOR = ','
const CSV_QUOTE = '"'

const props = defineProps({
  data: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rowKey: { type: [String, Function], default: 'id' },
  theme: { type: String, default: 'light' },
  contextMenu: { type: Array, default: () => [] },
  exportable: { type: Boolean, default: false },
  exportFileName: { type: String, default: 'export' },
  selectable: { type: Boolean, default: false },
  defaultExpandAll: { type: Boolean, default: false },
  tableClass: { type: String, default: '' },
  rowClassName: { type: [String, Function], default: '' },
  maxHeight: { type: [String, Number], default: undefined },
})

const emit = defineEmits([
  'sort-change',
  'filter-change',
  'context-command',
  'selection-change',
  'row-click',
  'row-dblclick',
  'expand-change',
])

const tableRef = ref(null)

// --- Context menu state ---
const menuVisible = ref(false)
const menuStyle = ref({})
const contextMenuRow = ref(null)

// --- Filter state ---
const filterState = reactive({})
const activeFilters = reactive({})

// Initialize filter state from column definitions
function initFilterState() {
  for (const key of Object.keys(filterState)) {
    delete filterState[key]
  }
  for (const key of Object.keys(activeFilters)) {
    delete activeFilters[key]
  }
  for (const col of props.columns) {
    if (col.filterable && col.prop) {
      filterState[col.prop] = []
      activeFilters[col.prop] = []
    }
  }
}

watch(() => props.columns, initFilterState, { immediate: true, deep: true })

// --- Computed ---
const dataColumns = computed(() =>
  props.columns.filter(col => col.type !== 'expand' && col.type !== 'selection')
)

function getNestedValue(obj, path) {
  if (!path || !obj) return obj
  const keys = path.split('.')
  let result = obj
  for (const key of keys) {
    if (result == null) return undefined
    result = result[key]
  }
  return result
}

const filteredData = computed(() => {
  const hasActiveFilters = Object.entries(activeFilters).some(([, v]) => v && v.length > 0)
  if (!hasActiveFilters) return props.data

  return props.data.filter(row => {
    return Object.entries(activeFilters).every(([prop, values]) => {
      if (!values || values.length === 0) return true
      return values.includes(getNestedValue(row, prop))
    })
  })
})

const visibleItems = computed(() => {
  if (!props.contextMenu) return []
  return props.contextMenu.filter(item => {
    if (item.visible === undefined) return true
    if (typeof item.visible === 'function') {
      return item.visible(contextMenuRow.value)
    }
    return item.visible
  })
})

// --- Methods ---
function isFilterActive(prop) {
  return activeFilters[prop] && activeFilters[prop].length > 0
}

function resetFilter(prop) {
  filterState[prop] = []
  activeFilters[prop] = []
  emit('filter-change', { prop, values: [] })
}

function applyFilter(prop) {
  activeFilters[prop] = [...filterState[prop]]
  emit('filter-change', { prop, values: activeFilters[prop] })
}

function onSortChange({ prop, order }) {
  emit('sort-change', { prop, order })
}

function onContextMenu(row, _column, event) {
  event.preventDefault()
  if (props.contextMenu.length === 0) return

  contextMenuRow.value = row
  menuStyle.value = {
    position: 'fixed',
    left: `${event.clientX}px`,
    top: `${event.clientY}px`,
    zIndex: 3000,
  }
  menuVisible.value = true
}

function onMenuClick(command) {
  menuVisible.value = false
  emit('context-command', command, contextMenuRow.value)
}

function closeContextMenu() {
  menuVisible.value = false
}

function handleExport() {
  if (!props.data || props.data.length === 0) return

  const cols = dataColumns.value
  const header = cols.map(col => csvEscape(col.label || col.prop)).join(CSV_SEPARATOR)
  const rows = props.data.map(row =>
    cols.map(col => csvEscape(String(getNestedValue(row, col.prop) ?? ''))).join(CSV_SEPARATOR)
  )
  const csvContent = BOM_PREFIX + header + '\n' + rows.join('\n')

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${props.exportFileName}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function csvEscape(value) {
  if (value.includes(CSV_SEPARATOR) || value.includes(CSV_QUOTE) || value.includes('\n')) {
    return CSV_QUOTE + value.replace(new RegExp(CSV_QUOTE, 'g'), CSV_QUOTE + CSV_QUOTE) + CSV_QUOTE
  }
  return value
}

// --- Lifecycle ---
onMounted(() => {
  document.addEventListener('click', closeContextMenu)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeContextMenu)
})
</script>

<style scoped>
.enhanced-table {
  width: 100%;
}

.enhanced-table__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.enhanced-table__filter-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.enhanced-table__filter-header .filter-icon {
  cursor: pointer;
  color: var(--el-text-color-placeholder);
  transition: color 0.2s;
}

.enhanced-table__filter-header .filter-icon.is-active {
  color: var(--el-color-primary);
}

.enhanced-table__filter-panel {
  padding: 4px 0;
}

.enhanced-table__filter-panel .el-checkbox {
  display: block;
  margin: 4px 12px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* Context menu */
.enhanced-table__context-menu {
  position: fixed;
  min-width: 140px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  box-shadow: var(--el-box-shadow-light);
  padding: 4px 0;
  z-index: 3000;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
  transition: background-color 0.15s;
}

.context-menu-item:hover {
  background-color: var(--el-fill-color-light);
}

.context-menu-item.is-danger {
  color: var(--el-color-danger);
}

.context-menu-item.is-danger:hover {
  background-color: var(--el-color-danger-light-9);
}

.context-menu-divider {
  height: 1px;
  margin: 4px 0;
  background-color: var(--el-border-color-lighter);
}

/* Context menu transition */
.context-menu-enter-active,
.context-menu-leave-active {
  transition: opacity 0.15s ease;
}

.context-menu-enter-from,
.context-menu-leave-to {
  opacity: 0;
}

/* Dark theme */
.enhanced-table--dark :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(10, 14, 23, 0.6);
  --el-table-row-hover-bg-color: rgba(79, 195, 247, 0.05);
  --el-table-border-color: rgba(79, 195, 247, 0.06);
  --el-table-header-text-color: #8892b0;
  --el-table-text-color: #ccd6f6;
}

/* Dark theme context menu (teleported to body, uses is-dark class) */
.enhanced-table__context-menu.is-dark {
  background: #141b2d;
  border-color: rgba(79, 195, 247, 0.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
}
.enhanced-table__context-menu.is-dark .context-menu-item {
  color: #ccd6f6;
}
.enhanced-table__context-menu.is-dark .context-menu-item:hover {
  background-color: rgba(79, 195, 247, 0.08);
}
.enhanced-table__context-menu.is-dark .context-menu-item.is-danger {
  color: #ff5252;
}
.enhanced-table__context-menu.is-dark .context-menu-item.is-danger:hover {
  background-color: rgba(255, 82, 82, 0.1);
}
.enhanced-table__context-menu.is-dark .context-menu-divider {
  background-color: rgba(79, 195, 247, 0.1);
}
</style>
