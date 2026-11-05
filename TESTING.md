# IPMsg 0.7.3 RC1 — compile-only delivery

November 5, 2026. Version 0.7.3 / code 11, application app.lantern.lan. Application ID, data schema and signing lineage retained.

Only production behavior change: the wide live-camera image uses centered CSS cover scaling to fill the receiver viewport's aspect ratio, cropping excess edges without stretching. Normal fit view remains uncropped. No browser full-screen API is used.

No unit tests, instrumentation tests, emulator checks, browser checks or physical-phone checks were run, at the user's request. Compilation/shrinking, signing verification and package integrity operations do not establish runtime behavior. Visual acceptance and existing physical-device checks remain pending.
