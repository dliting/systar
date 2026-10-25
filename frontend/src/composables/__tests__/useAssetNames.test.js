import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/iot/asset', () => ({
  getAssetTree: vi.fn()
}))

import { getAssetTree } from '@/api/iot/asset'
import { useAssetNames } from '@/composables/useAssetNames'

// Kind forest as returned by getAssetTree('kind'): synthetic KIND roots with
// ASSET children (id/caption/name) and GROUP nodes whose ids collide with
// asset ids and must not enter the name map.
const kindForest = [
  {
    key: 'KIND:DEVICE', nodeKind: 'KIND', assetKind: 'DEVICE', children: [
      { key: 'ASSET:10', nodeKind: 'ASSET', id: 10, caption: '空调主机', name: 'hvac_1', children: [
        { key: 'ASSET:11', nodeKind: 'ASSET', id: 11, caption: null, name: 'temp_101', children: [] }
      ] },
      { key: 'GROUP:10', nodeKind: 'GROUP', id: 10, caption: '分组撞名', children: [] }
    ]
  },
  { key: 'KIND:SERVICE', nodeKind: 'KIND', assetKind: 'SERVICE', children: [] }
]

describe('useAssetNames', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAssetTree.mockResolvedValue({ data: kindForest })
  })

  it('loads the kind forest once and maps asset ids to captions', async () => {
    const { load, names } = useAssetNames()
    await load()
    expect(getAssetTree).toHaveBeenCalledWith('kind')
    expect(names.value[10]).toBe('空调主机')
    expect(names.value[11]).toBe('temp_101')
  })

  it('skips group nodes so their colliding ids never shadow asset names', async () => {
    const { load, nameOf } = useAssetNames()
    await load()
    expect(nameOf(10)).toBe('空调主机')
  })

  it('falls back to #id for ids absent from the map', async () => {
    const { load, nameOf } = useAssetNames()
    await load()
    expect(nameOf(999)).toBe('#999')
  })

  it('returns an empty string for missing ids', async () => {
    const { nameOf } = useAssetNames()
    expect(nameOf(null)).toBe('')
    expect(nameOf(undefined)).toBe('')
  })

  it('does not refetch on repeated loads', async () => {
    const { load } = useAssetNames()
    await load()
    await load()
    expect(getAssetTree).toHaveBeenCalledTimes(1)
  })

  it('keeps nameOf working on the fallback path when loading fails', async () => {
    getAssetTree.mockRejectedValue(new Error('boom'))
    const { load, nameOf, loading } = useAssetNames()
    await load()
    expect(nameOf(10)).toBe('#10')
    expect(loading.value).toBe(false)
  })
})
