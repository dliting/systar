import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/asset', async (importOriginal) => ({
  ...await importOriginal(),
  createGroup: vi.fn().mockResolvedValue(),
  createGroupTree: vi.fn().mockResolvedValue(),
  deleteGroup: vi.fn().mockResolvedValue(),
  deleteGroupTree: vi.fn().mockResolvedValue(),
  listGroupTrees: vi.fn().mockResolvedValue({ data: [] }),
  listGroups: vi.fn().mockResolvedValue({ data: [] }),
  updateGroup: vi.fn().mockResolvedValue()
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
    ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() }
  }
})

vi.mock('@/utils/errorHandler', () => ({
  showSystarError: vi.fn()
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { showSystarError } from '@/utils/errorHandler'
import {
  createGroup, createGroupTree, deleteGroup, deleteGroupTree,
  listGroupTrees, listGroups, updateGroup
} from '@/api/iot/asset'
import GroupTreeManageDialog from '../GroupTreeManageDialog.vue'

/** Real el-dialog emits `open` only on a modelValue transition, never on initial true. */
async function openDialog() {
  const wrapper = mount(GroupTreeManageDialog, {
    props: { modelValue: false },
    global: { plugins: [ElementPlus] }
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  return wrapper
}

/** The dialog preselects the first tree when one exists. */
async function openDialogWithTree() {
  listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
  return openDialog()
}

function clickButton(wrapper, text) {
  const button = wrapper.findAll('button').find(b => b.text() === text)
  if (!button) throw new Error(`button not found: ${text}`)
  return button.trigger('click')
}

function clickRowAction(wrapper, rowIndex, text) {
  const row    = wrapper.findAll('.el-table__row')[rowIndex]
  const button = row && row.findAll('button').find(b => b.text() === text)
  if (!button) throw new Error(`row action not found: ${text}`)
  return button.trigger('click')
}

describe('GroupTreeManageDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('surfaces an error when loading the tree list fails on open', async () => {
    listGroupTrees.mockRejectedValueOnce(new Error('tree boom'))
    await openDialog()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'tree boom' }), '加载分组树失败')
  })

  it('surfaces an error when loading the group rows fails on open', async () => {
    listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
    listGroups.mockRejectedValueOnce(new Error('groups boom'))
    await openDialog()
    expect(showSystarError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'groups boom' }), '加载分组失败')
  })

  it('auto-selects the first tree and renders its group rows on open', async () => {
    listGroupTrees.mockResolvedValueOnce({ data: [{ id: 1, name: 'region', caption: '按区域' }] })
    listGroups.mockResolvedValueOnce({ data: [{ id: 5, caption: '一楼', level: 1, assetIds: [1, 2] }] })
    const wrapper = await openDialog()
    expect(listGroups).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.el-table__row').length).toBe(1)
    expect(wrapper.text()).toContain('一楼')
    expect(showSystarError).not.toHaveBeenCalled()
  })
})

describe('GroupTreeManageDialog CRUD flows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('createTree prompts name then caption, creates the tree and emits changed', async () => {
    const wrapper = await openDialog()
    ElMessageBox.prompt
      .mockResolvedValueOnce({ value: 'floor3' })
      .mockResolvedValueOnce({ value: '三楼' })
    await clickButton(wrapper, '新建树')
    await flushPromises()
    expect(createGroupTree).toHaveBeenCalledWith({ name: 'floor3', caption: '三楼', sequence: 1 })
    expect(listGroupTrees).toHaveBeenCalledTimes(2) // reload after the create
    expect(wrapper.emitted('changed')).toHaveLength(1)
    expect(showSystarError).not.toHaveBeenCalled()
  })

  it('removeTree confirms then deletes the selected tree and emits changed', async () => {
    const wrapper = await openDialogWithTree()
    ElMessageBox.confirm.mockResolvedValueOnce()
    await clickButton(wrapper, '删除树')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      '删除整棵树？需先删空全部分组。', '提示', { type: 'warning' })
    expect(deleteGroupTree).toHaveBeenCalledWith(1)
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('createGroupRow prompts a caption and creates a top-level group', async () => {
    const wrapper = await openDialogWithTree()
    ElMessageBox.prompt.mockResolvedValueOnce({ value: '三楼' })
    await clickButton(wrapper, '新建分组')
    await flushPromises()
    expect(createGroup).toHaveBeenCalledTimes(1)
    const payload = createGroup.mock.calls[0][0]
    expect(payload).toMatchObject({ treeId: 1, caption: '三楼', parent: 0 })
    expect(payload.name).toMatch(/^group_\d+$/)
    expect(listGroups).toHaveBeenLastCalledWith(1)
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('renameRow prefills the current caption and updates the group', async () => {
    listGroups.mockResolvedValueOnce(
      { data: [{ id: 5, treeId: 1, name: 'g1', caption: '一楼', level: 1, assetIds: [] }] })
    const wrapper = await openDialogWithTree()
    ElMessageBox.prompt.mockResolvedValueOnce({ value: '一层' })
    await clickRowAction(wrapper, 0, '改名')
    await flushPromises()
    expect(ElMessageBox.prompt).toHaveBeenCalledWith('分组显示名', '重命名', { inputValue: '一楼' })
    expect(updateGroup).toHaveBeenCalledWith(5, { name: 'g1', caption: '一层' })
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('removeRow confirms then deletes the group and reloads', async () => {
    listGroups.mockResolvedValueOnce(
      { data: [{ id: 5, treeId: 1, name: 'g1', caption: '一楼', level: 1, assetIds: [] }] })
    const wrapper = await openDialogWithTree()
    ElMessageBox.confirm.mockResolvedValueOnce()
    await clickRowAction(wrapper, 0, '删除')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledWith('删除分组「一楼」？', '提示', { type: 'warning' })
    expect(deleteGroup).toHaveBeenCalledWith(5)
    expect(listGroups).toHaveBeenLastCalledWith(1)
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('cancelling a prompt calls no API, reports no error and emits nothing', async () => {
    const wrapper = await openDialog()
    ElMessageBox.prompt.mockRejectedValueOnce('cancel')
    await clickButton(wrapper, '新建树')
    await flushPromises()
    expect(createGroupTree).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
    expect(showSystarError).not.toHaveBeenCalled()
    expect(wrapper.emitted('changed')).toBeUndefined()
  })

  it('a backend rejection inside a flow surfaces via ElMessage without changed', async () => {
    const wrapper = await openDialog()
    ElMessageBox.prompt
      .mockResolvedValueOnce({ value: 'floor3' })
      .mockResolvedValueOnce({ value: '三楼' })
    createGroupTree.mockRejectedValueOnce(new Error('dup tree'))
    await clickButton(wrapper, '新建树')
    await flushPromises()
    expect(ElMessage.error).toHaveBeenCalledWith('dup tree')
    expect(wrapper.emitted('changed')).toBeUndefined()
  })
})
