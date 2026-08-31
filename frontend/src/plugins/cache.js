const session = {
  getJSON(key) {
    try { return JSON.parse(sessionStorage.getItem(key)) } catch (e) { return null }
  },
  setJSON(key, value) {
    try { sessionStorage.setItem(key, JSON.stringify(value)) } catch (e) {}
  }
}

const local = {
  getJSON(key) {
    try { return JSON.parse(localStorage.getItem(key)) } catch (e) { return null }
  },
  setJSON(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)) } catch (e) {}
  }
}

export default { session, local }
