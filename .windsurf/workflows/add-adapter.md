---
description: Add RecyclerView adapter with DiffUtil
---

Create a RecyclerView adapter for ZenDrive lists.

1. Create adapter class in `app/src/main/java/com/example/zendrive/`

2. Use `ListAdapter<T, ViewHolder>(DiffCallback)` base class

3. Define `DiffUtil.ItemCallback<T>`:
   ```kotlin
   private class DiffCallback : DiffUtil.ItemCallback<ItemType>() {
       override fun areItemsTheSame(old: ItemType, new: ItemType): Boolean = 
           old.id == new.id
       override fun areContentsTheSame(old: ItemType, new: ItemType): Boolean = 
           old == new
   }
   ```

4. Create `ViewHolder` inner class with binding reference
   - Use `itemView.setOnClickListener { onItemClick(item) }`

5. In Activity/Fragment:
   - Set `adapter.submitList(items)` when collecting from StateFlow
   - Set `layoutManager` (usually `LinearLayoutManager`)

6. Create list item XML in `res/layout/item_xxx.xml`
   - Use Material CardView or ConstraintLayout
   - Elevation 2dp, padding 8dp typical
