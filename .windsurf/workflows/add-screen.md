---
description: Add a new Activity screen with MVVM
---

Add a new screen (Activity) to ZenDrive following MVVM pattern.

1. Create Activity class in `app/src/main/java/com/example/zendrive/`
   - Extend `AppCompatActivity()`
   - Use `ViewBinding` (e.g., `ActivityXxxBinding`)
   - Collect from ViewModel via `lifecycleScope` + `repeatOnLifecycle(Lifecycle.State.STARTED)`

2. Create XML layout in `app/src/main/res/layout/`
   - Use Material Components (e.g., `com.google.android.material.*`)
   - Root: `CoordinatorLayout`, `ConstraintLayout`, or `LinearLayout`
   - Define IDs: `@+id/xxx_container`, `@+id/xxx_recycler_view`
   - Add strings to `res/values/strings.xml`

3. Add to `AndroidManifest.xml` with proper `intent-filter` if needed

4. Create/adapt ViewModel in `LogViewModel.kt` or new file:
   - Use `viewModelScope` for coroutines
   - Expose state via `StateFlow`
   - Inject DAOs (do not hold Activity references)

5. Navigation: Launch via `Intent(context, NewActivity::class.java)`
