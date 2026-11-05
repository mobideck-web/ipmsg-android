# Live camera guide

1. Approve the intended peer. From its chat, choose Attach → Live camera and allow camera permission.
2. Read the owner's sharing confirmation: the receiver can record/take photos during the session, with activity alerts on the phone. Keep the camera screen open.
3. Open Certificate & setup and share the temporary viewer link. On the selected PC, inspect the site's certificate and compare the entire SHA-256 fingerprint with the phone through a trusted channel.
4. Only after a match, use a site-specific exception if the browser permits it. Never install a general phone-controlled CA or disable certificate checks globally. If policy prevents setup, use ordinary file/photo sharing. Reverify after IP/certificate changes.

## View and quality

The link opens in wide view within the browser window, without making the browser full screen. The image fills the receiver's viewing ratio using centered cropping, without stretching. Exit wide view shows the entire image.

On the phone, tap Quality for the 10–100% slider. Changes retain the link and adjust output dimensions/JPEG compression. At 100%, the stream uses the largest common preview/YUV output advertised by the selected rear camera. This is native stream resolution, not the phone's full-resolution still-photo mode. High resolution may reduce frame rate and increase heat/bandwidth. Performance is not benchmarked.

## Receiver captures

Take photo downloads a JPEG in the receiver's browser. Record creates silent WebM there; Stop recording triggers its download. New captures do not create phone-side files or chat attachments. The sender receives activity alerts and an on-screen indicator. Old saved recordings are retained.

Keep the page open until download completes. Clips stop/download after five minutes to bound browser memory. Browser codec/download policy applies. A clip keeps its initial canvas dimensions if stream quality changes; start another clip for a new size. Display cropping does not modify the underlying source frame used for capture.

Links authenticate once and sessions expire after at most ten minutes. Leaving/locking, network change, expiration or revocation ends access. Camera alerts must stay enabled. Stop camera ends the owner's session immediately.

No live audio. Alerts cover the built-in viewer controls; independent OS screenshots or third-party recording cannot be detected. Android controls alert visibility and sound. Current phone/browser acceptance remains pending: [validation](../TESTING.md).
