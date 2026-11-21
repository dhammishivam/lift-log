package app.liftlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.parseNum
import app.liftlog.logic.plainN

@Composable
fun Txt(
    text: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp, weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPal.current.ink, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
) = Text(text, modifier, color = color, fontSize = size, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align, lineHeight = lineHeight)

@Composable fun Mu(text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp) = Txt(text, modifier, size = size, color = LocalPal.current.mu)
@Composable fun Big(text: String, modifier: Modifier = Modifier, size: TextUnit = 30.sp, color: Color = LocalPal.current.ink) = Txt(text, modifier, size = size, weight = FontWeight.Bold, color = color, maxLines = 1)
@Composable fun H3(text: String, modifier: Modifier = Modifier) = Txt(text, modifier.padding(top = 20.dp, bottom = 8.dp), size = 16.sp, weight = FontWeight.SemiBold)
@Composable fun Label(text: String, modifier: Modifier = Modifier) = Txt(text, modifier.padding(top = 18.dp, bottom = 8.dp), size = 15.sp, weight = FontWeight.SemiBold, color = LocalPal.current.mu)

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
fun Modifier.noRippleTap(onClick: () -> Unit): Modifier = this.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

@Composable
fun Modifier.card(pad: Dp = 14.dp): Modifier = this.clip(RoundedCornerShape(16.dp)).background(LocalPal.current.s1).padding(pad)

enum class BtnKind { Plain, Primary, Danger, Ghost }

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Plain,
    small: Boolean = false, icon: ImageVector? = null, enabled: Boolean = true,
) {
    val p = LocalPal.current
    val bg = when (kind) { BtnKind.Primary -> p.ac; BtnKind.Plain -> p.s1; else -> Color.Transparent }
    val fg = when (kind) { BtnKind.Primary -> Color.White; BtnKind.Danger -> p.fa; else -> p.ink }
    Row(
        modifier.heightIn(min = if (small) 38.dp else 46.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (kind == BtnKind.Plain) p.ac else fg); Spacer(Modifier.width(6.dp)) }
        Txt(text, size = if (small) 14.sp else 15.sp, weight = FontWeight.SemiBold, color = if (enabled) fg else fg.copy(alpha = 0.6f), maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = LocalPal.current.mu, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).background(if (on) p.ac else p.s1).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 14.sp, weight = FontWeight.SemiBold, color = if (on) Color.White else p.mu, maxLines = 1) }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun <T> Seg(items: List<Pair<T, String>>, sel: T, onSel: (T) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val p = LocalPal.current
    Row(modifier.clip(RoundedCornerShape(10.dp)).background(p.s2).padding(3.dp)) {
        items.forEach { (k, l) ->
            Box(
                (if (fill) Modifier.weight(1f) else Modifier).heightIn(min = 34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (k == sel) p.bg else Color.Transparent).clickable { onSel(k) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(l, size = 13.sp, weight = FontWeight.SemiBold, color = if (k == sel) p.ink else p.mu, maxLines = 1) }
        }
    }
}

@Composable
fun TopBar(title: String, left: @Composable () -> Unit = { Spacer(Modifier.width(44.dp)) }, right: @Composable () -> Unit = { Spacer(Modifier.width(44.dp)) }) {
    val p = LocalPal.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            left()
            Txt(title, Modifier.weight(1f), size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
            right()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ln))
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier) {
        Mu(label, size = 13.sp)
        Txt(value, size = 21.sp, weight = FontWeight.SemiBold, color = if (accent) LocalPal.current.ac else LocalPal.current.ink, maxLines = 1)
    }
}

@Composable fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.ln))

/** A labelled text field. */
@Composable
fun Field(
    label: String?, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    singleLine: Boolean = true, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true,
) {
    val p = LocalPal.current
    Column(modifier.padding(vertical = 7.dp)) {
        if (label != null) Mu(label, Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value, onChange, enabled = enabled, singleLine = singleLine, minLines = minLines,
            textStyle = TextStyle(color = p.ink, fontSize = 16.sp), cursorBrush = SolidColor(p.ac),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, p.ln, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    if (value.isEmpty()) Txt(placeholder, color = p.mu)
                    inner()
                }
            },
        )
    }
}

/**
 * Numeric cell for set rows. Keeps the typed text while it parses to the model value,
 * so "62." or "0.5" isn't rewritten mid-typing.
 */
@Composable
fun NumCell(
    value: Double?, placeholder: String, onChange: (Double?) -> Unit, modifier: Modifier = Modifier,
    integer: Boolean = false, accentPh: Boolean = false, filled: Boolean = true, override: String? = null,
) {
    val p = LocalPal.current
    var text by remember { mutableStateOf(plainN(value)) }
    LaunchedEffect(value) { if (parseNum(text) != value) text = plainN(value) }
    val shown = override ?: text
    BasicTextField(
        shown, { t -> if (override == null) { text = t; onChange(parseNum(t)) } },
        singleLine = true, readOnly = override != null,
        textStyle = TextStyle(color = if (override != null) p.ac else p.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(p.ac),
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (filled) p.s1 else Color.Transparent), contentAlignment = Alignment.Center) {
                if (shown.isEmpty()) Txt(placeholder, color = if (accentPh) p.ac else p.mu.copy(alpha = 0.8f), size = 18.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) = Btn(if (on) "On" else "Off", onClick, Modifier.widthIn(min = 72.dp), kind = if (on) BtnKind.Primary else BtnKind.Plain)

@Composable
fun BoxCard(title: String, text: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card(16.dp)) {
        Txt(title, size = 16.sp, weight = FontWeight.SemiBold)
        if (text != null) Mu(text, Modifier.padding(top = 4.dp, bottom = 10.dp), size = 15.sp)
        content()
    }
}

/** Scrollable screen body with standard padding. */
@Composable
fun Body(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 40.dp), content = content)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(44.dp), tint = LocalPal.current.mu)
        Txt(title, Modifier.padding(top = 8.dp), size = 18.sp, weight = FontWeight.SemiBold)
        Mu(text, Modifier.padding(top = 4.dp), size = 15.sp)
    }
}

@Composable fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Modifier.verticalScrollSafe(): Modifier = this.verticalScroll(rememberScrollState())

@Composable
fun Modifier.horizontalScrollSafe(): Modifier = this.horizontalScroll(rememberScrollState())
