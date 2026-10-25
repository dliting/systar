import { ref } from 'vue'
import { getAssetTree } from '@/api/iot/asset'

/**
 * id → operator-facing asset name map built from the kind forest. Lists that
 * store raw monitor/device ids (alarm rules, correlations, silences) resolve
 * them through nameOf; unknown ids fall back to '#id' so a row is never blank.
 *
 * Group nodes are skipped: their ids live in t_group and collide with asset
 * ids, which would let a group caption shadow an asset name.
 *
 * @returns {{ names: Ref<Object>, loading: Ref<boolean>,
 *             load: () => Promise<void>, nameOf: (id: *) => string }}
 */
export function useAssetNames() {
  const names   = ref({})
  const loading = ref(false)
  let loaded    = false

  function collect(node, map) {
    if (!node) return
    if (node.nodeKind === 'ASSET' && node.id != null) {
      map[node.id] = node.caption || node.name
    }
    for (const child of node.children || []) {
      collect(child, map)
    }
  }

  async function load() {
    if (loaded) return
    loading.value = true
    try {
      const res    = await getAssetTree('kind')
      const map    = {}
      for (const root of res.data || []) {
        collect(root, map)
      }
      names.value = map
      loaded      = true
    } catch (e) {
      // Keep the '#id' fallback working when the forest cannot be loaded.
    } finally {
      loading.value = false
    }
  }

  function nameOf(id) {
    if (id === null || id === undefined || id === '') return ''
    return names.value[id] || `#${id}`
  }

  return { names, loading, load, nameOf }
}
