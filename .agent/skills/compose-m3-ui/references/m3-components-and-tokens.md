# Material 3 Components & Monochrome Design Tokens Reference

Threshold uses a strict high-contrast monochrome palette (`#000000` AMOLED background, `#FFFFFF` primary foreground) with mechanical precision. To avoid "AI-generated" UI clutter, all screens must map this monochrome palette onto **Material 3 semantic color roles** and use **canonical M3 components**.

---

## 1. Semantic Monochrome Color Scheme (`Theme.kt` / `Color.kt`)

Never define `private val CardBackground = Color(0xFF121212)` or `Color(0xFF888888)` inside screen files. Instead, populate the full Material 3 `darkColorScheme` in `Theme.kt`:

```kotlin
private val ThresholdDarkColorScheme = darkColorScheme(
    primary = PureWhite,                // #FFFFFF — Active controls, primary fills
    onPrimary = AmoledBlack,            // #000000 — Text/icons on primary fills
    primaryContainer = SurfaceHigh,     // #1E1E1E — Tonal button/chip fills
    onPrimaryContainer = PureWhite,     // #FFFFFF
    secondary = MutedWhite,             // #CCCCCC
    onSecondary = AmoledBlack,          // #000000
    secondaryContainer = SurfaceMedium, // #141414 — Selected chip/segmented container
    onSecondaryContainer = PureWhite,   // #FFFFFF
    background = AmoledBlack,           // #000000 — True AMOLED black canvas
    onBackground = PureWhite,           // #FFFFFF
    surface = AmoledBlack,              // #000000 — Default Scaffold/ListItem surface
    onSurface = PureWhite,              // #FFFFFF — Primary headline text
    surfaceVariant = CharcoalGray,      // #1E1E1E
    onSurfaceVariant = SubtleGray,      // #8E8E8E — Supporting text, subtitles, inactive icons
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = Color(0xFF0C0C0C),  // Grouped M3 Expressive section background
    surfaceContainer = Color(0xFF121212),     // Cards, sheets
    surfaceContainerHigh = Color(0xFF1A1A1A), // Dialogs, elevated menus
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF444444),              // Active borders, unselected switch/radio outlines
    outlineVariant = Color(0xFF222222),       // 1dp hairline dividers and subtle container borders
)
```

---

## 2. Canonical Screen Frame (`Scaffold` + `TopAppBar`)

Never build a top bar using a manual `Row` + `IconButton` + `Text` + `HorizontalDivider`. Always use `Scaffold` and `TopAppBar` (or `MediumTopAppBar` / `LargeTopAppBar`):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExampleSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ThresholdIcons.ArrowBack,
                            contentDescription = stringResource(R.string.action_go_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = innerPadding,
        ) {
            // Screen items
        }
    }
}
```

---

## 3. Settings & List Rows (`ListItem`)

Never hand-roll `Row(horizontalArrangement = Arrangement.SpaceBetween)` for settings toggles, navigation rows, or app rows. Use Material 3 `ListItem`:

### A. Toggle Preference Row (Accessible Whole-Row Click Target)
```kotlin
ListItem(
    headlineContent = { Text(label) },
    supportingContent = supportingText?.let { { Text(it) } },
    trailingContent = {
        Switch(
            checked = checked,
            onCheckedChange = null, // Handled by parent toggleable modifier for TalkBack cohesion
        )
    },
    modifier = Modifier
        .fillMaxWidth()
        .toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
)
```

### B. Sub-Screen Navigation Row (Replaces "KPI Count Cards" & "Manage →" Text)
Instead of rendering separate bordered "Guarded Apps: 3" / "Blocked Apps: 2" stat cards + a giant button, use a standard M3 navigation `ListItem`:
```kotlin
ListItem(
    headlineContent = { Text(stringResource(R.string.action_configure_default_apps)) },
    supportingContent = {
        Text("$guardedCount guarded · $blockedCount blocked")
    },
    trailingContent = {
        Icon(
            imageVector = ThresholdIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onNavigateToConfigureApps),
)
```

---

## 4. Category Filters (`LazyRow` + `FilterChip`)

Replace custom `Box` filter pills with Material 3 `FilterChip`:

```kotlin
LazyRow(
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(filters, key = { it.name }) { filter ->
        FilterChip(
            selected = selectedFilter == filter,
            onClick = { onSelectFilter(filter) },
            label = { Text(filter.displayLabel) },
            shape = RoundedCornerShape(4.dp),
        )
    }
}
```

---

## 5. Single-Choice & Multi-Choice Controls (`SegmentedButton`)

For 2–4 mutually exclusive options (e.g., Default Friction choice or Day-of-Week toggles), use `SingleChoiceSegmentedButtonRow` / `MultiChoiceSegmentedButtonRow` or `ListItem` radio rows rather than custom `Box` grids.

---

## 6. Contextual Pickers (`ModalBottomSheet`) & Time Selection (`TimePicker` / `TimeInput`)

- **App Picker (Add Quick Action):** Use `ModalBottomSheet` with a top search `OutlinedTextField` and a `LazyColumn` of `ListItem` rows so the user can filter and tap an app in 1–2 interactions.
- **Time Selection:** Use Material 3 `TimePicker` or `TimeInput` inside a `TimePickerDialog` or `ModalBottomSheet` rather than custom `"▲"` / `"▼"` hour/minute stepper boxes.
