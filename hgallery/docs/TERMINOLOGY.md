# HGallery Terminology

Project list of approved terms. Version 1. Applies to documents and code comments.

Reference for sentence rules: ASD-STE100 Issue 9 (2025-01-15).
This list gives the words this project uses. It does not certify compliance with
that standard. A certified review needs an approved dictionary and a reviewer.
This project has neither.

## 1. Rules for the documents

1. Write one idea in one sentence.
2. Use the active voice: "The player releases the surface", not "The surface is
   released".
3. Keep sentences below 25 words.
4. Use "must" for a requirement. Use "can" for a possibility.
5. Start a procedural step with a verb in the imperative: "Open the clip."
6. Do not use a metaphor when a plain noun is available. "The surface shows the
   picture" is better than "the surface owns the pixels".
7. Write American English: `color`, `center`, `behavior`, `initialize`.
8. Do not start a sentence with "There is" or "There are".
9. Give one name to one thing. Do not use a synonym for variety.
10. Put the condition before the command: "If the clip is muted, tap the sound
    control."

## 2. Approved terms

| Use this term | Do not use | Meaning in this project |
|---|---|---|
| **clip** | movie, footage, film, content | One video item in the timeline. |
| **photograph** | picture, image, photo (in documentation), shot | One still image item. |
| **item** | media object, asset, entry | One row from MediaStore, photo or clip. |
| **timeline** | roll, camera roll, stream, feed | The list of all items in date order. |
| **gallery** | library, collection screen | The grid screen named "Photos". |
| **viewer** | previewer, full screen view | The screen that shows photographs at full size. |
| **player** | video player screen, playback screen | The screen that plays one clip. |
| **session** | instance, context | One continuous use of a player from open to close. |
| **surface** | texture view, render target, canvas | The platform view that draws the decoded frames. |
| **poster** | thumbnail frame, first frame image | The still frame that represents a clip. |
| **chrome** | controls, overlay, UI layer | The bars and buttons drawn above the media. |
| **autoplay** | auto start, autostart, instant play | Start playback without a tap. |
| **autoplay next** | continuous play, chain play, advance | The setting that starts the next clip when one ends. |
| **muted** | silent, sound off | The player volume is zero, and playback continues. |
| **direct open** | tap open, manual open | The user chooses a clip by tapping it. |
| **swipe onto** | page onto, scroll onto | The viewer moves to a clip by the user's swipe. |
| **hand-over** | handoff, transfer, delegation | The viewer closes a page and the player takes the item. |
| **placeholder** | dummy, stub, mock | A stand-in that a user can see. A placeholder is a defect. |
| **detekt** | linter, static analyzer | The static analysis tool this project runs in CI. |
| **CI** | pipeline, build server, runner | GitHub Actions on this repository. |
| **evidence** | proof, screenshot folder | The files under `hgallery/evidence/`. |

## 3. Words this project must not use in a status claim

| Word | Why it is banned here |
|---|---|
| **seamless** | A quality claim with no measurement behind it. |
| **instant** | A speed claim. Give the milliseconds, or do not make the claim. |
| **buttery**, **smooth** | A frame-rate claim. Give the frame timing, or do not make it. |
| **verified** | Use it only for a result that a named test run produced. |
| **working** / **fixed** | Say what was checked, and where. |
| **production-ready** | This application has no release process that earns the phrase. |
| **fully compliant** | Do not apply it to ASD-STE100 without a certified review. |

## 4. Names that must stay exact

- Package: `dev.hgallery.app`
- Design system: NOIR
- Motion system: NOIR Motion Engine
- Launcher icon: H Aperture Signature
- Splash sequence: HGallery Ignition
- Modules: `:core` (platform free) and `:app` (Android).
- Test tag constants: `HgTestTags`.
- Rule objects in `core`: `PlayerPolicy`, `VideoQueue`, `Selection`,
  `AlbumBuilder`, `SearchFilters`, `TrashPolicy`.

## 5. Sentence examples

Correct: "The player keeps one surface. It replaces the media item. The chrome
does not blink."

Incorrect: "By reusing the underlying texture surface across media transitions,
we ensure a seamless visual experience." Reason: passive construction, a
metaphor, and an unmeasured quality claim in one sentence.
