# TEST_PLAN.md

## Layers

| Layer | What it proves | Command |
|---|---|---|
| Domain unit tests (`:core`) | grouping, sorting, formatting, search, similarity — with no device | `./gradlew :core:test` |
| App unit tests | the app module compiles and its JVM-visible code runs | `./gradlew :app:testDebugUnitTest` |
| Lint | correctness and accessibility rules, fatal | `./gradlew :app:lintDebug` |
| Instrumented Compose tests | real navigation, real localisation, real view switching on an emulator | `./gradlew :app:connectedDebugAndroidTest` |
| Launch + screenshot | the app reaches the foreground and draws what it claims | `.ci/run-connected-tests.sh` |
| Reference comparison | structural geometry against the captured reference | `tools/compare_reference.py` |

## Instrumented tests

| Test | Asserts |
|---|---|
| `ShellUiTest.appLaunchesAndShowsAllFourTabs` | all four tabs exist |
| `ShellUiTest.photosTabIsTheStartDestination` | Photos is the start destination |
| `ShellUiTest.everyTabCanBeSelected` | each tab is reachable and remains selected |
| `ShellUiTest.albumsTabShowsItsOwnTitle` | switching really changes the screen |
| `ShellUiTest.anUnimplementedTabSaysSoInsteadOfShowingFakeContent` | an unfinished tab says so |
| `AlbumNavigationTest.tappingAnAlbumOpensItsContentsInsteadOfTheViewer` | a regression guard for bug #7 |
| `AlbumNavigationTest.backFromAnAlbumReturnsToTheAlbumsTab` | back navigation |
| `LocalisationTest.persianTabLabelsExistInResources` | Persian strings exist, differ from English, and win |
| `LocalisationTest.englishIsTheDefaultAndStillRenders` | English default |
| `MonthViewTest.theViewSwitchIsReachable` | the month/day control exists |
| `MonthViewTest.switchingToMonthAndBackKeepsTheGridAlive` | state survives a switch |
| `MonthViewTest.repeatedSwitchingDoesNotCrashTheScreen` | no crash under repeated switching |

The instrumented tests deliberately avoid depending on the device's library, except
where the CI harness has installed fixtures and the test says so.

## Harness rules

The screenshot harness earned these rules the hard way:

1. **Never report a screenshot you did not take.** If the app is not in the
   foreground, capture the failure and say so (this caught bugs #1 and #2).
2. **Mask the status bar before comparing two frames.** A clock that ticked is
   otherwise enough to make "they differ" true (this caught a false positive).
3. **Never let a stale file decide a verdict.** Pulled images are deleted first.
4. **Measure where the control is; do not assume.** The view-switch tap missed by
   90 px until its position was read from a screenshot.
5. **A gesture that cannot be driven is a gesture that is not verified.** Hence the
   toolbar switch beside the pinch.

## What this plan does not cover

No test runs against a physical POCO X3 Pro. No frame-timing assertion exists. No
test covers the editor, video, Discover, Highlights or search, because those
features do not exist yet.
