# The band viewer

The band "on a turntable" at the top of the Device tab: drag sideways to
turn it, fling to spin it, tap to stop, and when left alone it turns slowly
by itself. It plays a folder of pre-rendered frames of the band. **The real
frames do not exist yet**: until Jul renders them, the viewer shows a still
vector placeholder. Dropping the frames in needs no code change.

| | |
|---|---|
| **Branch** | `feature/band-viewer`, merged into `mvvm-main` |
| **Code** | `ui/bandviewer/`: `BandViewer` (the interface and its factory), `TurntableBandView` (the view), `TurntableMotion` (the maths), `TurntableFrames` + `AssetFrameSource` (the frame folder) |
| **Placeholder** | `app/src/main/res/drawable/band_placeholder.xml` |
| **Frames go in** | `app/src/main/assets/band_turntable/` (does not exist yet) |
| **Tests** | `TurntableMotionTest` (25), `TurntableFramesTest` (4), `TurntableBandViewTest` (9, Robolectric) |
| **Related** | [device-tab.md](device-tab.md), [handoff.md](handoff.md) §3 and §4 ("Band model and turntable frames") |

**Purely visual.** It shows no data and collects nothing. It has no
repository, no Firebase, no network and no storage (not even the hint),
and adds no permission and no dependency. The Privacy Notice and
`CONSENT_VERSION` are unaffected.

---

## 1. How it works

```
DeviceFragment ── knows only ──► BandViewer (interface)
                                     │ create(context)
                                     ▼
                              TurntableBandView ── TurntableMotion (pure maths)
                                     │
                                     └── FrameSource ── AssetFrameSource
                                                        assets/band_turntable/*.webp
```

- **`BandViewer`** is all the Device tab sees: `view`, `setActive(Boolean)`,
  `release()`, and `BandViewer.create(context)`. A real-time 3D (`.glb`)
  viewer could implement the same interface and be returned from `create`
  without touching the tab. That would need a dependency check against
  compileSdk 34 and a release manifest re-check first.
- **`TurntableBandView`** is a custom `View` built in code (never from XML),
  added to `fl_device_band_viewer` (220 dp, the screen's own `surface_bg`,
  between the account header and the band card).
- **`TurntableMotion`** holds every rule: position in frames, drag, fling,
  auto-rotation, wrap-around. Time is passed in, so it is a plain JVM test.
- **How many frames there are is never written down.** `AssetFrameSource`
  lists the folder once, at creation. `TurntableFrames.select` keeps the files
  named `band_turn_NNN.webp` and orders them by number. The count is however
  many there are.

**Which image shows:**

| Frames in the folder | Viewer |
|---|---|
| 0 (today) | The vector placeholder, still. Touches pass straight through. |
| 1 | That image, still. Touches pass straight through. |
| 2 or more | The turntable. |
| A frame that fails to decode | The placeholder for that frame only. Never a crash. |

**Why assets and not `res/drawable-nodpi`:** an asset folder can be listed
at run time, so 36 or 72 frames need no constant and no code change. The
files are used byte for byte: no density scaling, no `R` ids, nothing for
the resource shrinker to drop as unused, and no `getIdentifier` lookups (which
lint flags). The placeholder is a resource because it is a vector, and
vectors cannot be assets.

---

## 2. Frames: the spec

| | |
|---|---|
| **Folder** | `app/src/main/assets/band_turntable/` |
| **Names** | `band_turn_000.webp`, `band_turn_001.webp`, ... Three digits, from 000, in turning order. Anything else in the folder is ignored. |
| **Count** | 36 to 72 (every 10° to every 5°). Any count works; 72 turns most smoothly. |
| **Rotation** | One full 360° turn around the band's **vertical** axis, ending one step before the first frame (do not repeat frame 000 at the end). |
| **Direction** | Each next frame has the band turned so that **its front moves to the viewer's right**. In Blender, with the camera on −Y looking along +Y and Z up, that is a **positive** Z rotation. Get it backwards and the band turns against the finger. |
| **Framing** | The same camera, scale and centre in every frame; the band standing upright, its base near the bottom edge (the viewer stands it on a drawn shadow, so render no floor and no shadow). |
| **Background** | Transparent (alpha). The viewer's surroundings are the theme's own colour, so one set works in light and dark mode. |
| **Size** | About 400 to 600 px square (the view is 220 dp tall; 600 px is sharp on xxhdpi). Every frame the same size. |
| **Format** | WebP with alpha, lossy at about quality 80 or lossless if it is small enough. |
| **Total** | About 1 to 2 MB for the set. |

Frames are decoded only as they are needed (the one on screen and two
either side, at most six held at once). At 600 × 600 that is up to about
8.6 MB of memory while the tab is on screen, and none once it is not.

---

## 3. Replacing the placeholder

1. Render the frames to the spec above.
2. Create `app/src/main/assets/band_turntable/` and copy them in.
3. Build and run. The viewer finds them, turns, shows "Drag to rotate", and
   TalkBack gets "Next angle" and "Previous angle".

No code change, no constant to bump. To go back to the placeholder, empty
the folder. `band_placeholder.xml` stays either way, as the fallback for a
broken frame. To change how the placeholder looks, edit that file (it is
drawn in theme colours, `surface_muted`, `text_secondary` and `brand_green`,
so it follows dark mode).

**Never commit stand-in frames** dressed up as the real ones. The concept
image is not in the repo; if a single still is wanted before the turntable
exists, put it in the folder as `band_turn_000.webp` alone and the viewer
shows it still.

---

## 4. Interaction

| Input | What happens |
|---|---|
| Horizontal drag | Turns it: one full turn per 1.5 view widths of drag, wrapping at both ends. Drag right, the front moves right. |
| Fling | Keeps spinning and slows smoothly (`v·e^(−t/τ)`, τ = 350 ms), capped at 3 turns a second. |
| Tap | Stops a spin. |
| Left alone 4 s | Turns slowly by itself, one turn every 20 s. |
| Vertical swipe starting on the band | Scrolls the Device tab, as anywhere else. |
| TalkBack | Reads "GuardBand band, interactive view. Drag to rotate."; actions **Next angle** and **Previous angle** turn it an eighth of a turn. |

- **Only sideways drags are claimed.** The view asks the parent not to
  intercept (`requestDisallowInterceptTouchEvent`) only once the finger has
  moved past the touch slop **and** more sideways than up or down. A vertical
  swipe is intercepted by the `ScrollView` as usual, and the viewer is sent a
  cancel. A still image claims no touch at all.
- **The hint**, "Drag to rotate", shows under the band until the first drag
  (or accessibility turn), then fades over 400 ms. It is remembered in
  memory only (a static flag) for the life of the process: never stored, so it
  shows again after the app restarts. It is not shown for a still image.
- **"Remove animations" on** (and battery saver, from Android 8): no fling,
  no auto-rotation, the hint vanishes instead of fading. Dragging and the
  TalkBack actions still work. Checked each time the tab becomes active.

---

## 5. Battery, memory and lifecycle

- **No loop and no timer.** A spin redraws on the view's own frame callback
  (`postInvalidateOnAnimation`). Waiting to idle and the slow auto-turn use
  one delayed redraw at a time (`postDelayed`), timed to the next frame change
  (about every 280 ms with 72 frames). A still image schedules nothing.
- **Active only while visible.** `DeviceFragment` calls `setActive(true)` in
  `onResume` when the tab is showing, and `setActive(false)` in `onPause` and
  when the host hides the tab (another tab, a pushed screen, the app in the
  background). Inactive, every callback is removed, the decode thread is shut
  down and every decoded frame is let go.
- **No leaks.** `onDestroyView` calls `release()`, and so does the view's own
  `onDetachedFromWindow`. A decode that finishes after a release is dropped:
  each one carries a generation number. A theme change recreates the Fragment,
  and with it a new viewer.
- **One background thread**, a daemon, created on the first decode and shut
  down on release.

---

## 6. Size

| | Added to the APK |
|---|---|
| Placeholder (`band_placeholder.xml`) | 1,664 bytes (587 compressed) |
| Viewer code | about 44 KB of class files before dexing; roughly 10 to 15 KB in the APK |
| **72 real frames** (expected) | **about 1 to 2 MB** (500 px transparent WebP, about 15 to 28 KB each). WebP is already compressed, so the APK does not shrink it further. |

---

## 7. Not covered by tests

The drawing itself (the shadow, the scaling, the fade), real WebP decoding
from assets, and how a fling feels on glass. `TurntableBandViewTest` runs
the view under Robolectric with fake frames: the placeholder build, one
frame, no frames, broken frames, drags both ways with wrap-around, the
vertical swipe left to the scroll, the TalkBack actions, and every order of
activate, pause, release and detach.

---

## 8. Phone checks

**With the placeholder (today's build):**

- [ ] The Device tab loads; the placeholder band is centred under the
      header with a soft shadow under it, and no hint shows.
- [ ] Swiping up or down starting on the band scrolls the tab.
- [ ] Dark mode: the placeholder and shadow (a faint glow) read well; switching
      the theme with the tab open brings it back without a crash.
- [ ] TalkBack reads "GuardBand band" and offers no turning actions.

**Once real frames are in the folder:**

- [ ] "Drag to rotate" shows under the band, and fades on the first drag.
      Switch tabs and back: it does not come back. Restart the app: it does.
- [ ] Drag right: the front moves right. Drag left: it turns back. Keep going
      both ways past a full turn: no jump at the seam.
- [ ] Fling both ways: it spins on and slows smoothly. Tap during a spin: it
      stops where it is.
- [ ] Leave it 4 s: it turns slowly by itself. Touch it: it stops.
- [ ] Start a vertical swipe on the band: the tab scrolls, the band does not
      turn. A diagonal swipe goes to whichever direction is stronger.
- [ ] Switch to another tab, open Settings, background the app: the
      auto-turn stops (no redraws in the GPU profiler or "Show surface
      updates"). Come back: it waits 4 s, then turns again.
- [ ] Dark mode: the frames' transparent background shows the theme.
- [ ] TalkBack: "GuardBand band, interactive view. Drag to rotate."; the
      actions menu offers Next angle and Previous angle, and each turns an
      eighth. Swiping with TalkBack moves focus on, never gets stuck on the
      band.
- [ ] Developer options → "Remove animations" (or Animator duration scale
      off): no auto-turn and no fling, the hint disappears at once, dragging
      still turns it.
- [ ] Rename one frame to break it (e.g. a 0-byte `band_turn_010.webp`): that
      angle shows the placeholder; nothing crashes.
