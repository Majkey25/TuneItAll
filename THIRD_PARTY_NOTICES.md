# Third-party notices

TuneItAll is proprietary software. The components and assets below retain their
own licenses.

## Runtime components

- AndroidX Core, Activity, Lifecycle, Compose, and Material 3: Apache License 2.0
- Material Design settings and back icons by Google: Apache License 2.0
- Kotlin standard library and Kotlin coroutines: Apache License 2.0

## Chord data

TuneItAll bundles `guitar.json` and `ukulele.json` from
`tombatossals/chords-db` at commit
`df06fa7b425cf5fd29485ff6591236b3557e3fac`.

- Upstream project: <https://github.com/tombatossals/chords-db>
- License: MIT
- Copyright: 2016 David Rubert
- Bundled `guitar.json` SHA-256:
  `cfe439962b2f444d2c341b1f0261403b4c3a3416e321147286fc608922699974`
- Bundled `ukulele.json` SHA-256:
  `233b7018ec35785a8bfa985bad90f4745cee04614c0fd1d5b819cff7406ec601`

MIT License

Copyright (c) 2016 David Rubert

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Visual assets

- `Gibson Les Paul` and `gibson explorer` headstock icons by Alessandro
  Curci, from Noun Project: CC BY 3.0. TuneItAll bundles the attributed PNG
  downloads and applies only theme tinting and layout scaling.
  <https://thenounproject.com/icon/gibson-les-paul-1506746/>
  <https://thenounproject.com/icon/gibson-explorer-1506732/>
  - Les Paul PNG SHA-256:
    `2b1ffdaf3b7007641a8606cfc7005fa2e06353fa877fbe26581fb943a637379c`
  - Explorer PNG SHA-256:
    `beba0384eea08bbf4aa1d5cd35be883bc81e887ade3b14980267c2f4b0db871b`
- `Metronome` by SVG Repo: CC0 1.0. TuneItAll keeps the original SVG in
  `assets/source/metronome-cc0.svg` and uses an animation-ready native vector
  derivative with a separate audio-synchronized arm.
  <https://www.svgrepo.com/svg/132374/metronome>
  - SVG SHA-256:
    `7ea0f898aec83ca2237c0f714b63ecc99e333bec69c945ab464bdef9e7ac3356`

Gibson, Les Paul, and Explorer names and marks belong to their respective
owners. Their use identifies the referenced headstock designs and does not
imply endorsement.

## Build and test components

- Android Gradle Plugin and Gradle: Apache License 2.0
- JUnit 4: Eclipse Public License 1.0
- JSON-java (`org.json`, test scope only): subject to its upstream license
- AndroidX Test and Espresso: Apache License 2.0

Dependency versions are defined in `app/build.gradle.kts`, `build.gradle.kts`,
and `gradle/wrapper/gradle-wrapper.properties`.

Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>

Eclipse Public License 1.0: <https://www.eclipse.org/legal/epl-v10.html>

Creative Commons CC0 1.0: <https://creativecommons.org/publicdomain/zero/1.0/>

Creative Commons BY 3.0: <https://creativecommons.org/licenses/by/3.0/>
