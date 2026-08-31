const STORAGE_PREFIX = 'form-'

/**
 * Provides persistent form field defaults via localStorage.
 *
 * Merging priority: memory (saved by user) > parent > typeDefaults
 *
 * @param {string} storageKey - unique key identifying the form type
 * @returns {{ defaults: (parent?: Object, typeDefaults?: Object) => Object,
 *              saveDefaults: (values: Object, fields?: string[]) => void,
 *              clearDefaults: () => void }}
 */
export function useFormDefaults(storageKey) {
  const fullKey = STORAGE_PREFIX + storageKey

  /**
   * Read persisted defaults from localStorage.
   * Returns an empty object when data is missing or corrupted.
   * @returns {Object}
   */
  function load() {
    try {
      const raw = localStorage.getItem(fullKey)
      return raw ? JSON.parse(raw) : {}
    } catch {
      return {}
    }
  }

  /**
   * Returns merged defaults with priority: memory > parent > typeDefaults.
   * @param {Object} [parent] - defaults inherited from parent asset
   * @param {Object} [typeDefaults] - defaults from asset type definition
   * @returns {Object}
   */
  function defaults(parent, typeDefaults) {
    return { ...typeDefaults, ...parent, ...load() }
  }

  /**
   * Persists form values to localStorage.
   * @param {Object} values - all form field values
   * @param {string[]} [fields] - optional whitelist of field names to persist
   */
  function saveDefaults(values, fields) {
    const toSave = fields
      ? Object.fromEntries(
          fields
            .filter(key => key in values)
            .map(key => [key, values[key]])
        )
      : { ...values }
    localStorage.setItem(fullKey, JSON.stringify(toSave))
  }

  /**
   * Removes saved data from localStorage.
   */
  function clearDefaults() {
    localStorage.removeItem(fullKey)
  }

  return { defaults, saveDefaults, clearDefaults }
}
