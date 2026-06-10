# Contributing to Pursi

Thank you for considering contributing to Pursi!

## License of Contributions

By submitting a pull request or otherwise contributing code, documentation,
data, or other materials to this project, you agree that your contributions
are licensed under the GNU General Public License v3-or-later, the same
license that covers the rest of the project. You retain copyright on your
own contributions. You confirm that the work is yours to contribute, or
that you have permission from the copyright holder.

# Contributing to Pursi

Thank you for considering contributing to Pursi!

## Development Setup

1. Clone the repository
2. Open in Android Studio (or IntelliJ IDEA with Android plugin)
3. Ensure you have Java 17+ and Android SDK 35
4. Copy `local.properties.example` to `local.properties` and configure as needed
5. Build: `./gradlew assembleDebug`

## Pull Request Process

1. Fork the repository and create a feature branch (`feature/your-feature-name`)
2. Make your changes following the existing code style
3. Ensure the project compiles: `./gradlew compileDebugKotlin`
4. Run existing tests: `./gradlew testDebug`
5. If adding UI, test on both phone and tablet form factors
6. Submit a PR with a clear description of what and why

## Adding a New Country / Data Source

### Quick Path: JSON-only (no Kotlin code)

For **WMTS chart layers** or **standard REST APIs**, just add a JSON file:

```bash
assets/providers/charts/<country>-<source>.json
```

See `assets/providers/charts/no-kartverket.json` for an example.

### Standard Path: PropertyMapper (~30 lines of Kotlin)

If you have a **WFS service with standard IALA/IHO-compliant data** (most national hydrographic offices):

1. Add a JSON config with the WFS endpoints:
   ```bash
   assets/providers/marine_features/<country>-<source>.json
   ```

2. Create a `PropertyMapper` to translate local WFS property keys → IALA standard:
   ```kotlin
   datasource/<country>/<Country>PropertyMapper.kt
   ```
   See `datasource/fi/FinnishPropertyMapper.kt` as reference.

3. Register in `di/DataSourceModule.kt`:
   ```kotlin
   @Provides @IntoSet
   fun provideXxxPropertyMapper(): PropertyMapper = XxxPropertyMapper()
   ```
   The generic `IalaFeatureRenderer` handles rendering automatically.

### Custom Path: FeatureRenderer (for truly unique data)

If your data doesn't follow IALA standards, implement a `FeatureRenderer`:

1. Create a JSON config (same as above)
2. Create a `FeatureRenderer` implementing `datasource/core/FeatureRenderer.kt`
3. Register both the provider and renderer in `DataSourceModule.kt`

### Architecture Overview

| Component | Purpose | Standard? |
|-----------|---------|-----------|
| `assets/providers/charts/*.json` | WMTS/XYZ chart tile config | Requires no code |
| `assets/providers/marine_features/*.json` | WFS endpoint config | Requires no code |
| `PropertyMapper` | Translate local WFS keys → IALA standard | ~30 lines |
| `FeatureRenderer` | Custom feature rendering logic | Escape hatch |
| `IalaFeatureRenderer` | Renders all IALA-compliant features | Ships with the app |

**IALA feature types**: `navigation_aid`, `light`, `daymark`, `light_sector`, `notice`, `navigation_line`, `fairway`, `restricted_area`, `depth_sounding`, `depth_contour`, `depth_area`, `unsurveyed_area`, `aton_fault`

## Code Style

- Follow existing patterns in the codebase (naming, layout, composable structure)
- Use `stringResource()` for all user-facing strings — never hardcode text
- Add string resources to `values/` (English), `values-fi/` (Finnish), and `values-sv/` (Swedish)
- Avoid adding comments — prefer self-documenting code with clear naming
- Use `collectAsStateWithLifecycle()` for StateFlow collection in composables
- All ViewModels should use Hilt (`@HiltViewModel`) with `@Inject constructor`

## Commit Messages

Write concise commit messages in English. Start with the area changed, e.g.:

- `weather: fix crash when network is unavailable`
- `i18n: add missing Swedish translations`
- `map: adjust chart opacity slider range`

## Issues

- Bug reports: include app version, Android version, device model, and steps to reproduce
- Feature requests: describe the problem you're solving, not just the solution
