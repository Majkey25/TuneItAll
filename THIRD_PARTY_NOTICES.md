# Third-party notices

TuneItAll is proprietary software, but it uses third-party components under
their own licences.

Runtime components:

- AndroidX Core, Activity, Lifecycle, and Compose — Apache License 2.0
- Jetpack Compose Material 3 — Apache License 2.0
- Kotlin standard library — Apache License 2.0
- Kotlin coroutines — Apache License 2.0

Build and test components:

- Android Gradle Plugin and Gradle — Apache License 2.0
- JUnit 4 — Eclipse Public License 1.0
- JSON-java (`org.json`, test scope only) — subject to its upstream licence
- AndroidX Test and Espresso — Apache License 2.0

Dependency versions are defined in `app/build.gradle.kts`, `build.gradle.kts`,
and `gradle/wrapper/gradle-wrapper.properties`. Transitive dependency notices
must be regenerated and reviewed from the final release bundle before each
public distribution.

Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>

Eclipse Public License 1.0: <https://www.eclipse.org/legal/epl-v10.html>
