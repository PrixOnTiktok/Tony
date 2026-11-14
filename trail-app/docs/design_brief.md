# Design brief (research summary)

Target: Google Pixel 11 Pro: 6.3" 1280×2856 OLED, ~412×915 dp, up to 3,600 nits, Android 17 (API 37).
Edge-to-edge is mandatory (API 36+), predictive back is on by default, native libraries must be 16 KB page-aligned.

## Principles (from onX Offroad, Gaia, OsmAnd, Organic Maps, Komoot reviews + Material 3 / Android for Cars guidance)
1. Map first: full-bleed map, one primary action, all controls in the bottom thumb zone.
2. Trail class shown by colour **and** line pattern **and** text badge (never colour alone).
3. Offline state + data date always visible but small.
4. Restore the last camera position after the app is killed.
5. Turn announcements early (voice + vibration) with a second-turn preview when turns are close.
6. Dark map at dusk; it also saves OLED power.
7. Battery: 1 s GPS only while navigating, 3 s while browsing; screen kept on only while navigating.
8. No hairline icons, light-grey text or translucent panels over the map. They vanish in sunlight.
9. No precise taps while riding: 60–64 dp buttons, ending navigation needs a hold.
10. Don't add layers or features just because other apps have them.

## Trail colours (colour-blind checked; worst-case ΔE ≥ 32 for deutan/protan/tritan)
| Class | Light | Dark | Line |
|---|---|---|---|
| Free | #0A9A5E | #6EF0B4 | solid |
| Maybe (pass / permission) | #F2A900 | #FFCF33 | long dash |
| Road: ATVs allowed | #1F45D6 | #6F8FFF | thin, white centre line |
| No ATVs | #7A1A5C | #D6336C | dotted |

Route: cyan #00B3FF with white casing. Muted basemap (land #F1EEE8 / #1C1F22) so trails stand out.

## Touch & type
Riding controls 60–76 dp, nothing under 48 dp. Navigation distance 40 sp bold, street 22 sp; body 15–17 sp.
