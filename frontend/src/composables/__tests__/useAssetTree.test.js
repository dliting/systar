import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/iot/asset', () => ({
  getAssetTree: vi.fn(),
  listGroupTrees: vi.fn()
}))

import { getAssetTree, listGroupTrees } from '@/api/iot/asset'
import { useAssetTree } from '@/composables/useAssetTree'

const kindForest = [
  { key: 'KIND:SERVICE', nodeKind: 'ASSET', assetKind: 'SERVICE', children: [] },
  { key: 'KIND:DEVICE', nodeKind: 'ASSET', assetKind: 'DEVICE', children: [] }
]

describe('useAssetTree', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listGroupTrees.mockResolvedValue({ data: [{ id: 1, name: 'region', caption: '按区域', sequence: 1 }] })
    getAssetTree.mockResolvedValue({ data: kindForest })
  })

  it('defaults to the kind tree and loads forest + trees', async () => {
    const { trees, currentTree, forest, init } = useAssetTree()
    await init()
    expect(getAssetTree).toHaveBeenCalledWith('kind')
    expect(currentTree.value).toBe('kind')
    expect(trees.value).toHaveLength(1)
    expect(forest.value).toEqual(kindForest)
  })

  it('switching tree refetches with the tree id', async () => {
    getAssetTree.mockResolvedValue({ data: [{ key: 'GROUP:1', children: [] }] })
    const { switchTree, forest, init } = useAssetTree()
    await init()
    await switchTree('1')
    expect(getAssetTree).toHaveBeenLastCalledWith('1')
    expect(forest.value[0].key).toBe('GROUP:1')
  })

  it('restores selector state and allows retry when loading the new tree fails', async () => {
    const { currentTree, currentTreeCaption, switchTree, init } = useAssetTree()
    await init()
    getAssetTree.mockRejectedValueOnce(new Error('load failed'))
    await expect(switchTree('1')).rejects.toThrow('load failed')
    expect(currentTree.value).toBe('kind')
    expect(currentTreeCaption.value).toBe('按类型')
    await switchTree('1')
    expect(getAssetTree).toHaveBeenLastCalledWith('1')
  })

  it('defaults forest to empty when the payload is not an array', async () => {
    getAssetTree.mockResolvedValue({ data: null })
    const { forest, init } = useAssetTree()
    await init()
    expect(forest.value).toEqual([])
  })

  it('ignores switching to the current tree', async () => {
    const { switchTree, init } = useAssetTree()
    await init()
    const callsAfterInit = getAssetTree.mock.calls.length
    await switchTree('kind')
    expect(getAssetTree.mock.calls.length).toBe(callsAfterInit)
  })

  it('propagates load failure and ends loading via finally', async () => {
    getAssetTree.mockRejectedValue(new Error('boom'))
    const { loading, init } = useAssetTree()
    await expect(init()).rejects.toThrow('boom')
    expect(loading.value).toBe(false)
  })

  it('falls back to the raw tree id as caption for unknown trees', async () => {
    const { currentTreeCaption, switchTree, init } = useAssetTree()
    await init()
    await switchTree('99')
    expect(currentTreeCaption.value).toBe('99')
  })

  it('refresh reloads both the tree list and the forest', async () => {
    const { refresh, init } = useAssetTree()
    await init()
    expect(listGroupTrees).toHaveBeenCalledTimes(1)
    expect(getAssetTree).toHaveBeenCalledTimes(1)
    await refresh()
    expect(listGroupTrees).toHaveBeenCalledTimes(2)
    expect(getAssetTree).toHaveBeenCalledTimes(2)
  })
})
