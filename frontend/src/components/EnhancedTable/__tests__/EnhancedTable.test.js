import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import EnhancedTable from '../index.vue'

const columns = [
  { prop: 'id', label: 'ID', width: 80, sortable: true },
  { prop: 'name', label: '名称', minWidth: 140 },
  { prop: 'status', label: '状态', width: 100, slot: true },
  {
    prop: 'type', label: '类型', width: 100,
    filterable: true,
    filterOptions: [
      { label: '类型A', value: 'A' },
      { label: '类型B', value: 'B' },
    ],
  },
]

const testData = [
  { id: 1, name: '测试1', status: 'active', type: 'A' },
  { id: 2, name: '测试2', status: 'inactive', type: 'B' },
]

function mountTable(propsOverrides = {}, slots = {}, options = {}) {
  return mount(EnhancedTable, {
    props: { columns, data: testData, ...propsOverrides },
    slots,
    global: { plugins: [ElementPlus] },
    ...options,
  })
}

describe('EnhancedTable', () => {
  // --- Rendering structure ---

  it('renders the root container', () => {
    const wrapper = mountTable()
    expect(wrapper.find('.enhanced-table').exists()).toBe(true)
  })

  it('renders el-table component', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.exists()).toBe(true)
  })

  it('renders data rows', () => {
    const wrapper = mountTable()
    const rows = wrapper.findAll('.el-table__row')
    expect(rows.length).toBe(2)
  })

  it('renders el-table-column components for each data column', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    // 4 data columns, no selection or expand
    expect(columnComps.length).toBe(4)
  })

  it('shows loading state', () => {
    const wrapper = mountTable({ loading: true })
    expect(wrapper.find('.el-loading-mask').exists()).toBe(true)
  })

  it('does not show loading when loading is false', () => {
    const wrapper = mountTable({ loading: false })
    expect(wrapper.find('.el-loading-mask').exists()).toBe(false)
  })

  // --- Theme ---

  it('applies dark theme class', () => {
    const wrapper = mountTable({ theme: 'dark' })
    expect(wrapper.find('.enhanced-table--dark').exists()).toBe(true)
  })

  it('does not apply dark theme class when light', () => {
    const wrapper = mountTable({ theme: 'light' })
    expect(wrapper.find('.enhanced-table--dark').exists()).toBe(false)
  })

  // --- Selection column ---

  it('renders selection column when selectable is true', () => {
    const wrapper = mountTable({ selectable: true })
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    // 4 data columns + 1 selection column
    expect(columnComps.length).toBe(5)
    const selectionCol = columnComps.find(c => c.props('type') === 'selection')
    expect(selectionCol).toBeTruthy()
  })

  it('does not render selection column by default', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    const selectionCol = columnComps.find(c => c.props('type') === 'selection')
    expect(selectionCol).toBeUndefined()
  })

  // --- Expand column ---

  it('renders expand column when expanded-row slot provided', () => {
    const wrapper = mountTable(
      {},
      {
        'expanded-row': `<template #expanded-row="{ row }"><div class="expanded-content">{{ row.name }}</div></template>`,
      }
    )
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    // 4 data columns + 1 expand column
    expect(columnComps.length).toBe(5)
    const expandCol = columnComps.find(c => c.props('type') === 'expand')
    expect(expandCol).toBeTruthy()
  })

  it('does not render expand column when no slot provided', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    const expandCol = columnComps.find(c => c.props('type') === 'expand')
    expect(expandCol).toBeUndefined()
  })

  // --- Export ---

  it('shows export button when exportable', () => {
    const wrapper = mountTable({ exportable: true })
    expect(wrapper.find('.enhanced-table__toolbar').exists()).toBe(true)
    expect(wrapper.find('.enhanced-table__toolbar').text()).toContain('导出 CSV')
  })

  it('hides export button when not exportable', () => {
    const wrapper = mountTable({ exportable: false })
    expect(wrapper.find('.enhanced-table__toolbar').exists()).toBe(false)
  })

  // --- Props passthrough ---

  it('passes tableClass to el-table', () => {
    const wrapper = mountTable({ tableClass: 'my-custom-table' })
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.classes()).toContain('my-custom-table')
  })

  it('passes maxHeight to el-table', () => {
    const wrapper = mountTable({ maxHeight: 400 })
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('maxHeight')).toBe(400)
  })

  it('passes rowKey to el-table', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('rowKey')).toBe('id')
  })

  it('passes data to el-table', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('data')).toHaveLength(2)
  })

  // --- dataColumns computed ---

  it('dataColumns computed filters out columns with type=selection or type=expand', () => {
    const specialColumns = [
      { prop: 'id', label: 'ID' },
      { type: 'selection' },
      { type: 'expand' },
    ]
    const wrapper = mountTable({ columns: specialColumns, data: testData })
    // dataColumns is a computed property that filters type columns
    // The "selection" and "expand" type columns are filtered from dataColumns
    // but they are not rendered as el-table-column (no v-if conditions met)
    // So only the "id" column is rendered
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    expect(columnComps.length).toBe(1)
    expect(columnComps[0].props('prop')).toBe('id')
  })

  // --- Column props ---

  it('passes column props (sortable, width, align) to el-table-column', () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    const columnComps = table.findAllComponents({ name: 'ElTableColumn' })
    const idCol = columnComps.find(c => c.props('prop') === 'id')
    expect(idCol).toBeTruthy()
    expect(idCol.props('sortable')).toBe(true)
    expect(idCol.props('width')).toBe(80)
  })

  // --- Events ---

  it('emits row-click when el-table emits row-click', async () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('row-click', testData[0])
    expect(wrapper.emitted('row-click')).toBeTruthy()
    expect(wrapper.emitted('row-click')[0][0]).toEqual(testData[0])
  })

  it('emits row-dblclick when el-table emits row-dblclick', async () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('row-dblclick', testData[1])
    expect(wrapper.emitted('row-dblclick')).toBeTruthy()
    expect(wrapper.emitted('row-dblclick')[0][0]).toEqual(testData[1])
  })

  it('emits selection-change when el-table emits selection-change', async () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('selection-change', [testData[0]])
    expect(wrapper.emitted('selection-change')).toBeTruthy()
    expect(wrapper.emitted('selection-change')[0][0]).toEqual([testData[0]])
  })

  it('emits sort-change when el-table emits sort-change', async () => {
    const wrapper = mountTable()
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('sort-change', { prop: 'id', order: 'ascending' })
    expect(wrapper.emitted('sort-change')).toBeTruthy()
    expect(wrapper.emitted('sort-change')[0][0]).toEqual({ prop: 'id', order: 'ascending' })
  })

  // --- Context menu ---

  describe('context menu', () => {
    let wrapper

    afterEach(() => {
      if (wrapper) {
        wrapper.unmount()
        wrapper = null
      }
    })

    // The context menu is teleported to body, so we need attachTo
    function mountWithContextMenu(contextMenu, propsOverrides = {}) {
      wrapper = mount(EnhancedTable, {
        props: { columns, data: testData, contextMenu, ...propsOverrides },
        global: { plugins: [ElementPlus] },
        attachTo: document.body,
      })
      return wrapper
    }

    it('context menu is hidden by default', () => {
      mountWithContextMenu([{ label: '编辑', command: 'edit' }])
      const menu = document.querySelector('.enhanced-table__context-menu')
      // In jsdom with transition stub, teleported content may or may not render
      // depending on transition stub behavior. The menuVisible is false, so
      // the inner div should not be rendered.
      expect(wrapper.vm.menuVisible).toBe(false)
    })

    it('shows context menu when menuVisible is true', async () => {
      mountWithContextMenu([{ label: '编辑', command: 'edit' }])
      wrapper.vm.menuVisible = true
      wrapper.vm.contextMenuRow = testData[0]
      await wrapper.vm.$nextTick()

      const menuItems = document.querySelectorAll('.context-menu-item')
      expect(menuItems.length).toBe(1)
      expect(menuItems[0].textContent).toContain('编辑')
    })

    it('emits context-command when menu item is clicked', async () => {
      mountWithContextMenu([{ label: '编辑', command: 'edit' }])
      wrapper.vm.menuVisible = true
      wrapper.vm.contextMenuRow = testData[0]
      await wrapper.vm.$nextTick()

      const menuItem = document.querySelector('.context-menu-item')
      expect(menuItem).toBeTruthy()
      menuItem.click()
      await wrapper.vm.$nextTick()

      expect(wrapper.emitted('context-command')).toBeTruthy()
      expect(wrapper.emitted('context-command')[0][0]).toBe('edit')
      expect(wrapper.emitted('context-command')[0][1]).toEqual(testData[0])
    })

    it('closes context menu on document click', async () => {
      mountWithContextMenu([{ label: '编辑', command: 'edit' }])
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      document.dispatchEvent(new MouseEvent('click'))
      await wrapper.vm.$nextTick()

      expect(wrapper.vm.menuVisible).toBe(false)
    })

    it('filters context menu items with visible function', async () => {
      const contextMenu = [
        { label: '编辑', command: 'edit', visible: () => true },
        { label: '删除', command: 'delete', visible: (row) => row && row.status === 'active' },
        { label: '始终显示', command: 'always', visible: true },
        { label: '始终隐藏', command: 'never', visible: false },
      ]
      mountWithContextMenu(contextMenu)
      wrapper.vm.contextMenuRow = testData[0] // status: 'active'
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      const items = document.querySelectorAll('.context-menu-item')
      expect(items.length).toBe(3)
      expect(Array.from(items).map(i => i.textContent.trim())).toEqual(['编辑', '删除', '始终显示'])
    })

    it('filters context menu items when visible function returns false', async () => {
      const contextMenu = [
        { label: '编辑', command: 'edit', visible: () => true },
        { label: '删除', command: 'delete', visible: (row) => row && row.status === 'active' },
      ]
      mountWithContextMenu(contextMenu)
      wrapper.vm.contextMenuRow = testData[1] // status: 'inactive'
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      const items = document.querySelectorAll('.context-menu-item')
      expect(items.length).toBe(1)
      expect(items[0].textContent.trim()).toBe('编辑')
    })

    it('renders divider before items with divided=true', async () => {
      const contextMenu = [
        { label: '编辑', command: 'edit' },
        { label: '删除', command: 'delete', divided: true },
      ]
      mountWithContextMenu(contextMenu)
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      const divider = document.querySelector('.context-menu-divider')
      expect(divider).toBeTruthy()
    })

    it('applies danger class to danger menu items', async () => {
      const contextMenu = [
        { label: '删除', command: 'delete', danger: true },
      ]
      mountWithContextMenu(contextMenu)
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      const item = document.querySelector('.context-menu-item')
      expect(item.classList.contains('is-danger')).toBe(true)
    })

    it('shows no menu items when contextMenu is empty', async () => {
      mountWithContextMenu([])
      wrapper.vm.menuVisible = true
      await wrapper.vm.$nextTick()

      const items = document.querySelectorAll('.context-menu-item')
      expect(items.length).toBe(0)
    })
  })

  // --- Filter ---

  it('initializes filter state for filterable columns', () => {
    const wrapper = mountTable()
    expect(wrapper.vm.filterState['type']).toEqual([])
  })

  it('isFilterActive returns false when no filter applied', () => {
    const wrapper = mountTable()
    expect(wrapper.vm.isFilterActive('type')).toBe(false)
  })

  it('isFilterActive returns true when filter is applied', () => {
    const wrapper = mountTable()
    wrapper.vm.activeFilters['type'] = ['A']
    expect(wrapper.vm.isFilterActive('type')).toBe(true)
  })

  it('resetFilter clears filter state and emits filter-change', () => {
    const wrapper = mountTable()
    wrapper.vm.filterState['type'] = ['A']
    wrapper.vm.activeFilters['type'] = ['A']
    wrapper.vm.resetFilter('type')
    expect(wrapper.vm.filterState['type']).toEqual([])
    expect(wrapper.vm.activeFilters['type']).toEqual([])
    expect(wrapper.emitted('filter-change')).toBeTruthy()
    expect(wrapper.emitted('filter-change')[0][0]).toEqual({ prop: 'type', values: [] })
  })

  it('applyFilter copies filterState to activeFilters and emits filter-change', () => {
    const wrapper = mountTable()
    wrapper.vm.filterState['type'] = ['A', 'B']
    wrapper.vm.applyFilter('type')
    expect(wrapper.vm.activeFilters['type']).toEqual(['A', 'B'])
    expect(wrapper.emitted('filter-change')).toBeTruthy()
    expect(wrapper.emitted('filter-change')[0][0]).toEqual({ prop: 'type', values: ['A', 'B'] })
  })

  // --- Filtered data ---

  it('returns all data when no filters active', () => {
    const wrapper = mountTable()
    expect(wrapper.vm.filteredData).toHaveLength(2)
  })

  it('filters data when activeFilters are set', async () => {
    const wrapper = mountTable()
    wrapper.vm.activeFilters['type'] = ['A']
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.filteredData).toHaveLength(1)
    expect(wrapper.vm.filteredData[0].type).toBe('A')
  })

  it('filters data with multiple values (OR logic)', async () => {
    const wrapper = mountTable()
    wrapper.vm.activeFilters['type'] = ['A', 'B']
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.filteredData).toHaveLength(2)
  })

  it('returns empty array when filter matches nothing', async () => {
    const wrapper = mountTable()
    wrapper.vm.activeFilters['type'] = ['Z']
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.filteredData).toHaveLength(0)
  })

  // --- CSV Export ---

  it('handleExport creates a download link', () => {
    const wrapper = mountTable({ exportable: true })
    const createElementSpy = vi.spyOn(document, 'createElement')
    wrapper.vm.handleExport()
    expect(createElementSpy).toHaveBeenCalledWith('a')
    createElementSpy.mockRestore()
  })

  it('handleExport does nothing when data is empty', () => {
    const wrapper = mountTable({ exportable: true, data: [] })
    const createElementSpy = vi.spyOn(document, 'createElement')
    wrapper.vm.handleExport()
    expect(createElementSpy).not.toHaveBeenCalledWith('a')
    createElementSpy.mockRestore()
  })

  // --- Row class name ---

  it('passes rowClassName to el-table', () => {
    const rowClassName = 'custom-row'
    const wrapper = mountTable({ rowClassName })
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('rowClassName')).toBe(rowClassName)
  })

  // --- visibleItems computed ---

  it('visibleItems returns all items when no visible property', async () => {
    const contextMenu = [
      { label: '编辑', command: 'edit' },
      { label: '删除', command: 'delete' },
    ]
    const wrapper = mountTable({ contextMenu })
    wrapper.vm.contextMenuRow = testData[0]
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.visibleItems.length).toBe(2)
  })

  it('visibleItems handles undefined contextMenu', () => {
    const wrapper = mountTable({ contextMenu: undefined })
    expect(wrapper.vm.visibleItems).toEqual([])
  })
})
