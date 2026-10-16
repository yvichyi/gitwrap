package io.lanyin.player.ui.library

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import io.lanyin.player.ui.theme.TiltNeonFamily
import io.lanyin.player.ui.design.drawTube
import io.lanyin.player.ui.design.BoxSign
import io.lanyin.player.ui.design.NeonText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.lanyin.player.core.model.UserProfile
import io.lanyin.player.data.account.AccountState
import io.lanyin.player.ui.design.initialOf
import io.lanyin.player.ui.design.Avatar
import io.lanyin.player.ui.design.ChromeBrush
import io.lanyin.player.ui.design.Flavor
import io.lanyin.player.ui.design.candyPlastic
import io.lanyin.player.ui.design.drawGelBead
import io.lanyin.player.ui.design.drawGloss
import io.lanyin.player.ui.design.DeviceScreen
import io.lanyin.player.ui.design.DoubleRule
import io.lanyin.player.ui.design.Hairline
import io.lanyin.player.ui.design.LineIcon
import io.lanyin.player.ui.design.PixelHearts
import io.lanyin.player.ui.design.SurfaceTone
import io.lanyin.player.ui.design.ThemedButton
import io.lanyin.player.ui.design.ThemedIcon
import io.lanyin.player.ui.design.VipBadge
import io.lanyin.player.ui.design.gameWindow
import io.lanyin.player.ui.design.pressable
import io.lanyin.player.ui.design.starPath
import io.lanyin.player.ui.design.themedSurface
import io.lanyin.player.ui.theme.ArchivoBlackFamily
import io.lanyin.player.ui.theme.BackdropKind
import io.lanyin.player.ui.theme.DotoFamily
import io.lanyin.player.ui.theme.FilmStamp
import io.lanyin.player.ui.theme.GridYellow
import io.lanyin.player.ui.theme.Lanyin
import io.lanyin.player.ui.theme.PixelFamily
import io.lanyin.player.ui.theme.SpaceMonoFamily

/** One way into the listener's own music on the 我的 page. */
internal data class Entry(val icon: LineIcon, val title: String, val detail: String, val onClick: (() -> Unit)? = null)

/** Type that is part of a drawing, held at its size whatever the system font scale. */
@Composable
private fun fixed(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

private fun vipName(vipType: Int) = when (vipType) {
    0 -> null
    10 -> "音乐包"
    11 -> "黑胶 VIP"
    else -> "VIP"
}

/**
 * The listener's card at the top of 我的, in the theme's medium: a subscriber's card for 报, a
 * character's status window for 游, a readout on 机's screen, a block with a big initial for 格,
 * a holographic member card for 酸, a name with a seal for 墨, an ID panel for 霓, a cast card
 * for 影; a plain card elsewhere.
 */
@Composable
internal fun ThemedProfile(account: AccountState, onLogin: () -> Unit, modifier: Modifier) {
    val profile = (account as? AccountState.LoggedIn)?.profile
    when (Lanyin.style.backdrop) {
        BackdropKind.Paper -> SubscriberCard(profile, account, onLogin, modifier)
        BackdropKind.Game -> StatusWindow(profile, onLogin, modifier)
        BackdropKind.Device -> UserScreen(profile, onLogin, modifier)
        BackdropKind.Grid -> InitialBlock(profile, onLogin, modifier)
        BackdropKind.Acid -> MemberCard(profile, onLogin, modifier)
        BackdropKind.Ink -> SealedName(profile, onLogin, modifier)
        BackdropKind.Neon -> NameSign(profile, onLogin, modifier)
        BackdropKind.Film -> CastCard(profile, onLogin, modifier)
        else -> Profile(account, onLogin, modifier)
    }
}

@Composable
private fun SubscriberCard(profile: UserProfile?, account: AccountState, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(
        modifier
            .fillMaxWidth()
            .border(2.dp, c.content)
            .padding(3.dp)
            .border(0.75.dp, c.content)
            .then(if (profile == null) Modifier.pressable(onClick = onLogin) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (profile != null) "订 户 证" else "尚 未 订 阅", style = style.type.title.copy(fontWeight = FontWeight.Black), color = c.content, modifier = Modifier.weight(1f))
            if (profile != null) {
                Text("No. %06d".format(profile.userId % 1_000_000), style = style.type.caption, color = c.muted)
            } else {
                Text("登录", style = style.type.caption.copy(fontWeight = FontWeight.Bold), color = c.onAccent, modifier = Modifier.background(c.accent).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        Hairline(Modifier.padding(vertical = 8.dp))
        if (profile != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.avatarUrl, profile.nickname, 52.dp, shape = androidx.compose.ui.graphics.RectangleShape)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("姓名　${profile.nickname}", style = style.type.body.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("级别　${vipName(profile.vipType) ?: "普通读者"}", style = style.type.caption, color = c.content)
                    Text("状态　已连接网易云音乐", style = style.type.caption, color = c.muted)
                }
            }
        } else {
            Text(
                if (account == AccountState.Loading) "正在连接网易云音乐…" else "扫码登录网易云音乐，同步你的歌单与红心。",
                style = style.type.caption, color = c.content,
            )
        }
    }
}

@Composable
private fun StatusWindow(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val c = Lanyin.colors
    val pixel = TextStyle(fontFamily = PixelFamily)
    Row(
        modifier
            .fillMaxWidth()
            .gameWindow(c.accentAlt, c.onAccent)
            .then(if (profile == null) Modifier.pressable(onClick = onLogin) else Modifier)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).border(2.dp, c.onAccent).padding(3.dp)) {
            if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 50.dp, shape = androidx.compose.ui.graphics.RectangleShape)
            else Text("?", style = pixel.copy(fontSize = fixed(28f)), color = c.onAccent, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(profile?.nickname ?: "NO SAVE DATA", style = pixel.copy(fontSize = fixed(17f)), color = c.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (profile != null && profile.vipType != 0) "LV.99" else "LV.01", style = pixel.copy(fontSize = fixed(13f)), color = Color(0xFFFFD34E))
                Spacer(Modifier.width(8.dp))
                Text("HP", style = pixel.copy(fontSize = fixed(13f)), color = c.onAccent)
                Spacer(Modifier.width(4.dp))
                PixelHearts(3, Color(0xFFFF6B9A), Modifier.size(width = 40.dp, height = 11.dp))
            }
            if (profile != null) {
                Text("存档已同步 · ${vipName(profile.vipType) ?: "普通玩家"}", style = pixel.copy(fontSize = fixed(12f)), color = c.onAccent.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
            } else {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▶ ", style = pixel.copy(fontSize = fixed(15f)), color = c.onAccent)
                    Text("登录", style = pixel.copy(fontSize = fixed(15f)), color = c.onAccent)
                    Text("　扫码读取存档", style = pixel.copy(fontSize = fixed(12f)), color = c.onAccent.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun UserScreen(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val mono = TextStyle(fontFamily = SpaceMonoFamily)
    val accent = Lanyin.colors.accent
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        DeviceScreen(Modifier.weight(1f)) {
            val c = Lanyin.colors
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("USER 01", style = mono.copy(fontSize = fixed(10f), fontWeight = FontWeight.Bold), color = c.muted, modifier = Modifier.weight(1f))
                Canvas(Modifier.size(7.dp)) {
                    if (profile != null) drawCircle(accent.copy(alpha = 0.35f), size.minDimension)
                    drawCircle(if (profile != null) accent else c.content.copy(alpha = 0.2f), size.minDimension / 2f)
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile != null) {
                    Avatar(profile.avatarUrl, profile.nickname, 40.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(profile?.nickname ?: "NO USER", style = Lanyin.style.type.title.copy(fontWeight = FontWeight.Bold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (profile != null) "MEMBER · ${vipName(profile.vipType)?.uppercase() ?: "STANDARD"}" else "按右边的键登录网易云",
                        style = if (profile != null) mono.copy(fontSize = fixed(10f)) else Lanyin.style.type.caption,
                        color = c.muted,
                    )
                }
            }
        }
        if (profile == null) {
            Spacer(Modifier.width(12.dp))
            ThemedButton("登录", onLogin)
        }
    }
}

@Composable
private fun InitialBlock(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(modifier.fillMaxWidth().themedSurface().height(IntrinsicSize.Min)) {
        Box(Modifier.width(88.dp).fillMaxHeight().background(if (profile != null) c.accent else c.content), contentAlignment = Alignment.Center) {
            Text(
                profile?.nickname?.let(::initialOf) ?: "?",
                style = TextStyle(fontWeight = FontWeight.Black, fontSize = fixed(46f), lineHeight = fixed(50f)),
                color = Color.White,
            )
        }
        Box(Modifier.width(2.5.dp).fillMaxHeight().background(c.outline))
        Column(Modifier.weight(1f).padding(14.dp)) {
            Text(profile?.nickname ?: "未登录", style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 20.sp), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (profile != null) "USER № %06d".format(profile.userId % 1_000_000) else "同步你的歌单与红心",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = c.muted,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            if (profile != null) VipBadge(profile.vipType) else ThemedButton("登录", onLogin)
        }
    }
}

@Composable
private fun MemberCard(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            // Holographic foil: the flavours run together across the card, the gloss over its top.
            .background(Brush.linearGradient(listOf(Flavor.Grape.copy(alpha = 0.42f), Flavor.Bondi.copy(alpha = 0.3f), Flavor.Strawberry.copy(alpha = 0.3f), Flavor.Grape.copy(alpha = 0.32f))))
            .drawBehind { drawGloss(0.14f) }
            .border(1.5.dp, ChromeBrush, shape)
            .then(if (profile == null) Modifier.pressable(onClick = onLogin) else Modifier)
            .padding(18.dp),
    ) {
        Column {
            Text("LANYIN MEMBER", style = TextStyle(fontFamily = ArchivoBlackFamily, fontSize = fixed(18f), brush = ChromeBrush, letterSpacing = 0.02.em))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).border(2.dp, ChromeBrush, CircleShape), contentAlignment = Alignment.Center) {
                    if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 52.dp)
                    else ThemedIcon(LineIcon.User, null, tint = Color.White, size = 24.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.nickname ?: "GUEST", style = style.type.title.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (profile != null) "No. %06d · %s".format(profile.userId % 1_000_000, vipName(profile.vipType) ?: "MEMBER") else "扫码入会 ✦ 同步歌单",
                        style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(11f)),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                if (profile == null) {
                    Text(
                        "登录",
                        style = style.type.body.copy(fontWeight = FontWeight.Bold),
                        color = c.onAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(Color(0xFFE2FF6B), c.accent, Color(0xFF8FC400))))
                            .drawBehind { drawGloss(0.45f) }
                            .pressable(onClick = onLogin)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Canvas(Modifier.align(Alignment.TopEnd).size(30.dp)) {
            drawPath(starPath(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, size.minDimension * 0.14f, 4), Color.White)
        }
    }
}

@Composable
private fun SealedName(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape), contentAlignment = Alignment.Center) {
                if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 56.dp)
                else ThemedIcon(LineIcon.User, null, tint = c.muted, size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.nickname ?: "未登录", style = style.type.display.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (profile != null) vipName(profile.vipType)?.let { "$it · 已连接网易云音乐" } ?: "已连接网易云音乐" else "同步你的歌单与红心", style = style.type.caption, color = c.muted, maxLines = 1)
            }
            if (profile != null) {
                Box(Modifier.size(26.dp).background(c.accentAlt), contentAlignment = Alignment.Center) {
                    Text(initialOf(profile.nickname), style = style.type.caption.copy(fontFamily = style.display, fontSize = 14.sp), color = c.bg)
                }
            } else {
                ThemedButton("登录", onLogin)
            }
        }
        Hairline(Modifier.padding(top = 18.dp))
    }
}

/**
 * 霓: your name in lights, as a shop puts up its sign: the nickname in pink tube over a dark board,
 * the avatar in a ring of cyan, OPEN beside it. Signed out, the sign is there but switched off.
 */
@Composable
private fun NameSign(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Row(
        modifier
            .fillMaxWidth()
            .themedSurface()
            .drawBehind { drawRect(Brush.radialGradient(listOf(c.accent.copy(alpha = if (profile != null) 0.18f else 0.05f), Color.Transparent), Offset(size.width * 0.35f, 0f), size.width * 0.7f)) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).drawBehind {
                drawTube(Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f - 3.dp.toPx())) }, c.accentAlt, 2.dp.toPx(), if (profile != null) 1f else 0f)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 44.dp)
            else ThemedIcon(LineIcon.User, null, tint = c.muted, size = 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NeonText(profile?.nickname ?: "未登录", style.type.title.copy(fontSize = 20.sp), c.accent, lit = if (profile != null) 1f else 0f)
            Text(
                if (profile != null) (vipName(profile.vipType) ?: "网易云音乐") + " · 营业中" else "招牌还没亮 · 扫码登录",
                style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (profile != null) BoxSign("OPEN", c.accentAlt, size = 10f, delayMillis = 300) else ThemedButton("登录", onLogin)
    }
}

/**
 * 霓: the entries as the building's directory by the lift: a lit board, a floor to each place,
 * the floor numbers in cyan tube, switching on floor by floor.
 */
@Composable
private fun DirectoryBoard(entries: List<Entry>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier.fillMaxWidth().themedSurface().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("澜音大厦 · 楼层指南", style = style.type.label, color = c.muted, modifier = Modifier.weight(1f))
            Text("DIRECTORY", style = TextStyle(fontFamily = TiltNeonFamily, fontSize = fixed(11f), letterSpacing = 1.sp), color = c.muted)
        }
        entries.forEachIndexed { i, e ->
            Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(c.content.copy(alpha = 0.08f)))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).then(e.onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeonText("${i + 1}F", TextStyle(fontFamily = TiltNeonFamily, fontSize = fixed(20f)), c.accentAlt, Modifier.width(46.dp), delayMillis = 120 * i)
                ThemedIcon(e.icon, null, tint = c.muted, size = 18.dp, modifier = Modifier.padding(end = 10.dp))
                Text(e.title, style = style.type.body, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(e.detail, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                if (e.onClick != null) ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun CastCard(profile: UserProfile?, onLogin: () -> Unit, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    val line = c.content.copy(alpha = 0.45f)
    val label = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixed(9f), letterSpacing = 0.14.em)
    Row(modifier.fillMaxWidth().border(1.dp, line).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(12.dp).size(56.dp).border(1.dp, line), contentAlignment = Alignment.Center) {
            if (profile != null) Avatar(profile.avatarUrl, profile.nickname, 56.dp, shape = androidx.compose.ui.graphics.RectangleShape)
            else ThemedIcon(LineIcon.User, null, tint = c.muted, size = 24.dp)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(line))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("CAST", style = label, color = c.muted)
            Text(profile?.nickname ?: "待定", style = style.type.title, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("ROLE", style = label, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (profile != null) vipName(profile.vipType)?.let { "$it 听众" } ?: "听众" else "扫码登录后入组",
                style = style.type.caption, color = FilmStamp,
            )
        }
        if (profile == null) {
            ThemedButton("登录", onLogin, Modifier.padding(end = 12.dp))
        }
    }
}

/**
 * The entries of 我的 (liked, recent, ranking, downloads, cloud), in the theme's medium: tiles for
 * the themes built of blocks and keys, a table of contents or an index for the ones built of type,
 * a menu window for 游; a ruled list elsewhere.
 */
@Composable
internal fun ThemedEntries(entries: List<Entry>, modifier: Modifier) {
    val style = Lanyin.style
    val c = style.colors
    when (style.backdrop) {
        BackdropKind.Grid -> EntryTiles(entries, modifier) { i, e, m ->
            val fills = listOf(c.accentAlt to Color.White, GridYellow to c.content, c.accent to Color.White, c.surface to c.content, c.content to c.bg)
            val (fill, ink) = fills[i % fills.size]
            Column(m.heightIn(min = 92.dp).themedSurface(onClick = e.onClick).background(fill).padding(12.dp)) {
                Row {
                    ThemedIcon(e.icon, null, tint = ink, size = 22.dp)
                    Spacer(Modifier.weight(1f))
                    Text("%02d".format(i + 1), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixed(11f)), color = ink)
                }
                Spacer(Modifier.weight(1f))
                Text(e.title, style = style.type.title.copy(fontWeight = FontWeight.Black, fontSize = 16.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.detail, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fixed(10f)), color = ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        BackdropKind.Dot -> EntryTiles(entries, modifier) { _, e, m ->
            Row(m.heightIn(min = 72.dp).themedSurface(tone = SurfaceTone.Flat, onClick = e.onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(c.surfaceAlt, CircleShape), contentAlignment = Alignment.Center) { ThemedIcon(e.icon, null, tint = c.content, size = 19.dp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(e.title, style = style.type.body, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.detail, style = TextStyle(fontFamily = DotoFamily, fontWeight = FontWeight.Black, fontSize = fixed(12f)), color = c.muted, maxLines = 1)
                }
            }
        }
        BackdropKind.Device -> EntryTiles(entries, modifier, columns = 3) { i, e, m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(54.dp).themedSurface(shape = RoundedCornerShape(12.dp), tone = SurfaceTone.Chrome, onClick = e.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    ThemedIcon(e.icon, e.title, tint = c.content, size = 21.dp)
                    Text("${i + 1}", style = TextStyle(fontFamily = SpaceMonoFamily, fontWeight = FontWeight.Bold, fontSize = fixed(9f)), color = c.muted, modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 5.dp))
                }
                Text(e.title, style = style.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
                Text(e.detail, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(9f)), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 酸: each a tile of see-through plastic in the next flavour, a gel bead holding its icon.
        BackdropKind.Acid -> EntryTiles(entries, modifier) { i, e, m ->
            val tint = Flavor.all[i % Flavor.all.size]
            val shape = RoundedCornerShape(22.dp)
            Row(
                m.heightIn(min = 60.dp).candyPlastic(tint, shape)
                    .then(e.onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) { drawGelBead(androidx.compose.ui.graphics.lerp(tint, Color.White, 0.15f)) }
                    ThemedIcon(e.icon, null, tint = Color.White, size = 17.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.title, style = style.type.body.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.detail, style = TextStyle(fontFamily = SpaceMonoFamily, fontSize = fixed(10f)), color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        BackdropKind.Dusk -> EntryTiles(entries, modifier) { _, e, m ->
            Row(m.heightIn(min = 60.dp).themedSurface(shape = RoundedCornerShape(18.dp), tone = SurfaceTone.Flat, onClick = e.onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { ThemedIcon(e.icon, null, tint = c.accent, size = 18.dp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.title, style = style.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.detail, style = style.type.caption.copy(fontSize = 11.sp), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        BackdropKind.Game -> MenuWindow(entries, modifier)
        BackdropKind.Ink -> EntryLines(entries, modifier, leader = true) { i -> Text(io.lanyin.player.ui.home.chineseNumber(i + 1), style = style.type.body.copy(fontFamily = style.display), color = c.muted, modifier = Modifier.width(30.dp)) }
        BackdropKind.Paper -> Column(modifier) {
            DoubleRule()
            EntryLines(entries, Modifier, leader = true) { _ -> Box(Modifier.padding(end = 10.dp).size(6.dp).background(c.content)) }
        }
        BackdropKind.Film -> EntryLines(entries, modifier, leader = false) { i ->
            Text("▸ %02dA".format(i + 1), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = fixed(10f), letterSpacing = 0.1.em), color = FilmStamp, modifier = Modifier.width(52.dp))
        }
        BackdropKind.Neon -> DirectoryBoard(entries, modifier)
    }
}

/** Entries as tiles in a grid of [columns]; a last tile left alone runs the full width. */
@Composable
private fun EntryTiles(entries: List<Entry>, modifier: Modifier, columns: Int = 2, tile: @Composable (Int, Entry, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until (entries.size + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val rowItems = (0 until columns).map { row * columns + it }.filter { it < entries.size }
                val stretch = columns == 2 && rowItems.size == 1
                for (i in rowItems) tile(i, entries[i], Modifier.weight(1f))
                if (!stretch) repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Entries as lines: the theme's [marker], the title, a leader of dots (or space) and the detail. */
@Composable
private fun EntryLines(entries: List<Entry>, modifier: Modifier, leader: Boolean, marker: @Composable (Int) -> Unit) {
    val style = Lanyin.style
    val c = style.colors
    Column(modifier) {
        entries.forEachIndexed { i, e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).then(e.onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                marker(i)
                Text(e.title, style = style.type.body, color = c.content, maxLines = 1)
                if (leader) {
                    Canvas(Modifier.weight(1f).height(4.dp).padding(horizontal = 10.dp)) {
                        val step = 6.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawCircle(c.content.copy(alpha = 0.25f), 0.8.dp.toPx(), Offset(x, size.height / 2f))
                            x += step
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(e.detail, style = style.type.caption, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                if (e.onClick != null) ThemedIcon(LineIcon.Forward, null, tint = c.muted, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
            }
            Hairline()
        }
    }
}

/** 游: the entries as a menu in a role-playing game's window, ▶ on the first. */
@Composable
private fun MenuWindow(entries: List<Entry>, modifier: Modifier) {
    val c = Lanyin.colors
    val pixel = TextStyle(fontFamily = PixelFamily)
    Column(modifier.fillMaxWidth().gameWindow(c.accentAlt, c.onAccent).padding(horizontal = 14.dp, vertical = 12.dp)) {
        entries.forEachIndexed { i, e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).then(e.onClick?.let { Modifier.pressable(onClick = it) } ?: Modifier).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▶", style = pixel.copy(fontSize = fixed(14f)), color = c.onAccent.copy(alpha = if (i == 0) 1f else 0f), modifier = Modifier.width(20.dp))
                ThemedIcon(e.icon, null, tint = c.onAccent, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(e.title, style = pixel.copy(fontSize = fixed(16f)), color = c.onAccent, maxLines = 1, modifier = Modifier.weight(1f))
                Text(e.detail, style = pixel.copy(fontSize = fixed(12f)), color = c.onAccent.copy(alpha = 0.65f), maxLines = 1)
            }
        }
    }
}
