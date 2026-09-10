package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.graphics.Color

// 主题色（浅深色通用）
val HanimePrimary = Color(0xFFdc143c)
val HanimePrimaryLight = Color(0xFFff4757)

val HanimeBackground = Color(0xFF141414)
val HanimeCard = Color(0xFF1a1a1a)
val HanimeBorder = Color(0xFF2a2a2a)

val HanimeTextPrimary = Color(0xFFFFFFFF)
val HanimeTextSecondary = Color(0xFFa0a0a0)

val HanimeBackgroundLight = Color(0xFFFAFAFA)
val HanimeCardLight = Color(0xFFFFFFFF)
val HanimeBorderLight = Color(0xFFE0E0E0)

val HanimeTextPrimaryLight = Color(0xFF1A1A1A)
val HanimeTextSecondaryLight = Color(0xFF666666)

/**
 * 危险 / 破坏性操作的强调色（删除、清空、失败状态）。
 *
 * 不直接用 `MaterialTheme.colorScheme.error`：本应用主题把 error 映射成 primary（同一支红），
 * 直接复用会让「删除」与「管理 / 重试」等主色文字变成同色，破坏破坏性操作的视觉区分度。
 * 提取成语义常量只为消除散落各处的 `Color(0xFFFF6B6B)` 字面量（审查 O4）。
 */
val HanimeDanger = Color(0xFFFF6B6B)

// 状态色（浅深色通用）
val HanimeSuccess = Color(0xFF2ed573)
val HanimeWarning = Color(0xFFffa502)
val HanimeGold = Color(0xFFffc107)
