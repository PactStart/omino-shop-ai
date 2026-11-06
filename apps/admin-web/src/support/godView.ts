import { apiPost, errorMessage, isApiError } from '../api/http'

export async function issueGodViewToken(userId: string): Promise<string> {
  try {
    const body = await apiPost<{ token: string }>('/v1/god-view/tokens', { userId })
    return body.data.token
  } catch (error) {
    if (isApiError(error) && error.code === 40303) {
      throw new Error('上帝视角未开启')
    }
    throw new Error(errorMessage(error))
  }
}
