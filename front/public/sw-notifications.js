// Loaded by the service worker of the PWA (workbox importScripts): a tap on a notification of the driver app brings
// the app to the front on the page given by the notification, or opens it.
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/conducteur'
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of windows) {
      if ('focus' in client) {
        await client.focus()
        if ('navigate' in client) {
          await client.navigate(url)
        }
        return
      }
    }
    await self.clients.openWindow(url)
  })())
})
