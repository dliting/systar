import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TreePanel from '../index.vue'

function mountPanel(propsData = {}) {
  return mount(TreePanel, {
    props: propsData,
    global: { plugins: [ElementPlus] }
  })
}

function findTree(wrapper) {
  return wrapper.findComponent({ name: 'ElTree' })
}

describe('TreePanel drag passthrough', () => {
  it('keeps el-tree non-draggable by default', () => {
    const wrapper = mountPanel()
    expect(findTree(wrapper).props('draggable')).toBe(false)
  })

  it('passes draggable through to el-tree', () => {
    const wrapper = mountPanel({ draggable: true })
    expect(findTree(wrapper).props('draggable')).toBe(true)
  })

  it('passes allow-drop through to el-tree', () => {
    const allowDrop = () => false
    const wrapper = mountPanel({ draggable: true, allowDrop })
    expect(findTree(wrapper).props('allowDrop')).toBe(allowDrop)
  })

  it('forwards node-drop from el-tree', () => {
    const wrapper = mountPanel()
    findTree(wrapper).vm.$emit('node-drop', 'd', 't', 'inner', {})
    expect(wrapper.emitted('node-drop')[0]).toEqual(['d', 't', 'inner', {}])
  })

  it('forwards node-contextmenu from el-tree', () => {
    const wrapper = mountPanel()
    findTree(wrapper).vm.$emit('node-contextmenu', 'evt', { id: 1 }, {}, {})
    expect(wrapper.emitted('node-contextmenu')[0]).toEqual(['evt', { id: 1 }, {}, {}])
  })
})
