import { ref } from 'vue'
import { getAssetTree, listGroupTrees } from '@/api/iot/asset'

export const KIND_TREE         = 'kind'
const KIND_TREE_CAPTION        = '按类型'

/**
 * Shared asset-tree state: tree list (kind + user trees), current selector,
 * forest data and loading flag. One instance per consuming view.
 *
 * @returns {{ trees: Ref<Array>, currentTree: Ref<string>, currentTreeCaption: Ref<string>,
 *             forest: Ref<Array>, loading: Ref<boolean>,
 *             init: () => Promise<void>, refresh: () => Promise<void>,
 *             switchTree: (tree: string) => Promise<void> }}
 */
export function useAssetTree() {
  const trees              = ref([])
  const currentTree        = ref(KIND_TREE)
  const currentTreeCaption = ref(KIND_TREE_CAPTION)
  const forest             = ref([])
  const loading            = ref(false)

  async function loadForest() {
    loading.value = true
    try {
      const res    = await getAssetTree(currentTree.value)
      forest.value = Array.isArray(res.data) ? res.data : []
    } finally {
      loading.value = false
    }
  }

  async function loadTrees() {
    const res   = await listGroupTrees()
    trees.value = res.data || []
  }

  async function init() {
    await Promise.all([loadTrees(), loadForest()])
  }

  async function switchTree(tree) {
    if (tree === currentTree.value) return
    const prevTree           = currentTree.value
    const prevCaption        = currentTreeCaption.value
    currentTree.value        = tree
    currentTreeCaption.value = tree === KIND_TREE
      ? KIND_TREE_CAPTION
      : (trees.value.find(t => String(t.id) === String(tree))?.caption || tree)
    try {
      await loadForest()
    } catch (e) {
      currentTree.value        = prevTree
      currentTreeCaption.value = prevCaption
      throw e
    }
  }

  async function refresh() {
    await Promise.all([loadTrees(), loadForest()])
  }

  return { trees, currentTree, currentTreeCaption, forest, loading, init, refresh, switchTree }
}
