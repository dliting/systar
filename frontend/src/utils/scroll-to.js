export function scrollTo(y, duration = 500) {
  const start = window.scrollY
  const diff = y - start
  const startTime = performance.now()

  function step(currentTime) {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)
    const ease = 1 - Math.pow(1 - progress, 3) // ease-out cubic
    window.scrollTo(0, start + diff * ease)
    if (progress < 1) {
      requestAnimationFrame(step)
    }
  }

  requestAnimationFrame(step)
}
