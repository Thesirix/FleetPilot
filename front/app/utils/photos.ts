import { accessToken } from './auth'
import { newId, type OutboxPhoto } from './outbox'

/** Longest side of a photo sent by the app: enough to read a scratch or a plate, about 300 kB in JPEG. */
const MAX_SIDE = 1600
const QUALITY = 0.82

/**
 * Resizes a photo taken with the phone before it is stored and sent: a 12 megapixel picture weighs 4 MB, the server
 * accepts 2 MB at most and the driver may be on a slow mobile network.
 */
export async function preparePhoto(file: File): Promise<OutboxPhoto> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const blob = await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(b => (b ? resolve(b) : reject(new Error('Image not readable'))), 'image/jpeg', QUALITY))
  return { id: newId(), bytes: await blob.arrayBuffer(), contentType: 'image/jpeg' }
}

/**
 * Object URL of a photo stored on the server. The image needs the access token, which an <img> tag cannot send:
 * it is fetched, then shown from memory. The caller revokes the URL when it is no longer displayed.
 */
export async function photoUrl(photoId: string): Promise<string | null> {
  const token = await accessToken()
  if (!token) {
    return null
  }
  const response = await fetch(`/api/v1/photos/${photoId}`, { headers: { Authorization: `Bearer ${token}` } })
  return response.ok ? URL.createObjectURL(await response.blob()) : null
}
