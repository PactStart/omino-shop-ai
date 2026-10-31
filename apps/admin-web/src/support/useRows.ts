import { ref } from 'vue'
import { errorMessage } from '../api/http'
import { readPage } from './format'

export function useRows() {
  const rows = ref<Record<string, unknown>[]>([])
  const error = ref('')
  const loading = ref(false)
  const page = ref(1)
  const size = ref(20)
  const total = ref(0)
  const paged = ref(false)

  async function load(request: () => Promise<unknown>): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const data = await request()
      const parsed = readPage<Record<string, unknown>>(data, page.value, size.value)
      rows.value = parsed.rows
      paged.value = parsed.paged
      total.value = parsed.total
      if (parsed.paged) {
        page.value = parsed.page
        size.value = parsed.size
      }
    } catch (reason) {
      rows.value = []
      total.value = 0
      paged.value = false
      error.value = errorMessage(reason)
    } finally {
      loading.value = false
    }
  }

  return { rows, error, loading, page, size, total, paged, load }
}
