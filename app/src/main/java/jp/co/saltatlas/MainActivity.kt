package jp.co.saltatlas

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.*
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.android.gms.ads.*
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import jp.co.saltatlas.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.json.JSONObject
import android.graphics.drawable.GradientDrawable
import org.osmdroid.views.MapView

private val android.content.Context.settings by preferencesDataStore("salt_settings")
private val themeKey = stringPreferencesKey("theme")
private val favoritesKey = stringSetPreferencesKey("favorites")

private data class Palette(val paper: Color, val ink: Color, val accent: Color, val sea: Color, val card: Color, val motif: String)
private val Palette.headingFont: FontFamily get() = if (motif == "modern") FontFamily.SansSerif else FontFamily.Serif
private val themes = listOf(
    "和紙風" to Palette(Color(0xFFF3EBDD), Color(0xFF29251F), Color(0xFF9C6A35), Color(0xFFCAD9D7), Color(0xFFFFFBF4), "washi"),
    "浮世絵風" to Palette(Color(0xFFF4E9D6), Color(0xFF202D40), Color(0xFFB54032), Color(0xFFA9C9D4), Color(0xFFFFF9ED), "ukiyoe"),
    "水墨画風" to Palette(Color(0xFFF0F0EC), Color(0xFF262626), Color(0xFF666666), Color(0xFFD4D7D4), Color(0xFFFFFFFF), "sumi"),
    "江戸古地図風" to Palette(Color(0xFFE8D6B8), Color(0xFF49351F), Color(0xFF9A3C2E), Color(0xFFB9C5B2), Color(0xFFF5E8D0), "edo"),
    "藍染風" to Palette(Color(0xFF173B52), Color(0xFFF3F2E9), Color(0xFFDAAD69), Color(0xFF315D70), Color(0xFF244A61), "indigo"),
    "桜和風" to Palette(Color(0xFFF6E9E8), Color(0xFF443238), Color(0xFFB46F79), Color(0xFFD4E3E3), Color(0xFFFFF8F6), "sakura"),
    "漆黒・金蒔絵風" to Palette(Color(0xFF171615), Color(0xFFF4E9D5), Color(0xFFD1AA55), Color(0xFF314951), Color(0xFF282522), "maki-e"),
    "和モダン風" to Palette(Color(0xFFF2F0E7), Color(0xFF303A31), Color(0xFF55705B), Color(0xFFD7E1DC), Color(0xFFFFFFFF), "modern")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        Configuration.getInstance().userAgentValue = packageName
        val db = SaltDatabase.get(this)
        lifecycleScope.launch { runCatching { CatalogSeeder.synchronize(this@MainActivity, db.saltDao()) } }
        setContent { SaltAtlasApp(db) }
    }
}

@Composable private fun SaltAtlasApp(db: SaltDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeName by context.settings.data.map { it[themeKey] ?: themes.first().first }.collectAsStateWithLifecycle(themes.first().first)
    val palette = themes.firstOrNull { it.first == themeName }?.second ?: themes.first().second
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selectedSalt by remember { mutableStateOf<SaltEntity?>(null) }
    val rows by remember(search) { if (search.isBlank()) db.saltDao().all() else db.saltDao().search(search) }.collectAsStateWithLifecycle(emptyList())
    val mine by db.saltDao().mine().collectAsStateWithLifecycle(emptyList())
    MaterialTheme(colorScheme = lightColorScheme(primary = palette.accent, background = palette.paper, surface = palette.card, onSurface = palette.ink)) {
        Scaffold(containerColor = palette.paper, bottomBar = {
            NavigationBar(containerColor = palette.card) {
                listOf("ホーム", "全国マップ", "塩図鑑", "コレクション", "設定").forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(listOf(Icons.Default.Home, Icons.Default.Map, Icons.Default.MenuBook, Icons.Default.CollectionsBookmark, Icons.Default.Settings)[i], null) }, label = { Text(label, fontSize = 10.sp) })
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(palette, rows, mine, onMap = { tab = 1 }, onCatalog = { tab = 2 }, onCollection = { tab = 3 }, onOpen = { selectedSalt = it })
                    1 -> MapScreen(palette, rows, mine) { selectedSalt = it }
                    2 -> CatalogScreen(palette, rows, search, { search = it }, { salt -> selectedSalt = salt })
                    3 -> CollectionScreen(palette, rows, mine, onOpen = { selectedSalt = it })
                    else -> SettingsScreen(palette, themeName) { name -> scope.launch { context.settings.edit { it[themeKey] = name } } }
                }
                if (tab != 1) TestBanner()
            }
            selectedSalt?.let { salt -> SaltDetailDialog(salt, palette,
                mine.firstOrNull { it.saltId == salt.id },
                onSave = { favorite, purchased, tasted, rating, note ->
                    scope.launch { db.saltDao().updateMine(salt.id, favorite, purchased, tasted, rating, note) }
                }, onClose = { selectedSalt = null }) }
        }
    }
}

@Composable private fun ThemePattern(p: Palette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val faint = p.accent.copy(alpha = if (p.motif == "sumi") .13f else .10f)
        val thin = 1.2.dp.toPx()
        when (p.motif) {
            "washi" -> for (i in 0..24) { val x = (i * 37f) % size.width; val y = (i * 19f) % size.height; drawLine(faint, Offset(x, y), Offset((x + 18f + (i % 5) * 4f).coerceAtMost(size.width), y + (i % 3 - 1) * 1.5f), thin) }
            "ukiyoe" -> for (row in 0..3) { val y = size.height * (.30f + row * .19f); val path = Path().apply { moveTo(0f, y); var x = 0f; while (x < size.width) { quadraticTo(x + size.width * .08f, y - size.height * .12f, x + size.width * .16f, y); x += size.width * .16f } }; drawPath(path, faint, style = Stroke(2.dp.toPx())) }
            "sumi" -> for (i in 0..9) { val x = (i * 73f + 11f) % size.width; val y = (i * 41f + 7f) % size.height; drawCircle(p.ink.copy(alpha=.055f + (i % 3) * .018f), (5 + i % 8).dp.toPx(), Offset(x,y)) }
            "edo" -> { for (i in 1..5) { val x = size.width * i / 6f; drawLine(faint, Offset(x, 0f), Offset(x, size.height), thin) }; for (i in 1..3) { val y=size.height*i/4f; drawLine(faint, Offset(0f,y), Offset(size.width,y), thin) }; drawCircle(p.accent.copy(alpha=.10f), size.minDimension*.27f, Offset(size.width*.72f,size.height*.52f), style=Stroke(2.dp.toPx())) }
            "indigo" -> for (x in -size.height.toInt()..size.width.toInt() step 26) { val path=Path().apply { moveTo(x.toFloat(),0f); lineTo(x+size.height, size.height) }; drawPath(path, faint, style=Stroke(thin)); val path2=Path().apply { moveTo(x+size.height,0f); lineTo(x.toFloat(),size.height) }; drawPath(path2,faint,style=Stroke(thin)) }
            "sakura" -> for (i in 0..13) { val x=(i*61f+13f)%size.width; val y=(i*43f+9f)%size.height; drawOval(p.accent.copy(alpha=.11f), topLeft=Offset(x,y), size=androidx.compose.ui.geometry.Size(7.dp.toPx(),13.dp.toPx()), style=Stroke(1.dp.toPx())) }
            "maki-e" -> { for (i in 0..17) { val x=(i*53f+9f)%size.width; val y=(i*29f+5f)%size.height; drawCircle(p.accent.copy(alpha=.25f), (1.2f+(i%3)).dp.toPx(), Offset(x,y)) }; for (i in 0..2) { val path=Path().apply { moveTo(0f,size.height*(.35f+i*.2f)); cubicTo(size.width*.25f,size.height*(.15f+i*.2f),size.width*.55f,size.height*(.55f+i*.1f),size.width,size.height*(.24f+i*.2f)) }; drawPath(path,p.accent.copy(alpha=.14f),style=Stroke(1.3.dp.toPx())) } }
            else -> { for (i in 0..5) { val y=size.height*i/5f; drawLine(faint,Offset(0f,y),Offset(size.width,y),thin) }; for (i in 0..5) { val x=size.width*i/5f; drawLine(faint,Offset(x,0f),Offset(x,size.height),thin) } }
        }
    }
}

@Composable private fun Header(p: Palette, subtitle: String) {
    Box(Modifier.fillMaxWidth().background(p.paper)) {
        ThemePattern(p, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("日本の塩図鑑", fontSize = 24.sp, fontFamily = p.headingFont, fontWeight = FontWeight.Bold, color = p.ink)
            Text(subtitle, fontSize = 12.sp, color = p.accent)
        }
    }
}
@Composable private fun HomeScreen(p: Palette, rows: List<SaltEntity>, mine: List<MySaltEntity>, onMap: () -> Unit, onCatalog: () -> Unit, onCollection: () -> Unit, onOpen: (SaltEntity) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(p, "全国の塩を、知る・探す・巡る。")
        Text("全国塩マップ", Modifier.padding(start = 20.dp, top = 8.dp), color = p.ink, fontFamily = FontFamily.Serif, fontSize = 21.sp)
        SaltMapCanvas(p, rows, mine, Modifier.fillMaxWidth().padding(12.dp).height(270.dp), onOpen)
        TextButton(onClick = onMap, modifier = Modifier.align(Alignment.End)) { Text("地図を大きく表示") }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard("登録商品", "${rows.size} 件", p, Modifier.weight(1f), onCatalog)
            InfoCard("コレクション", "${mine.size} 件", p, Modifier.weight(1f), onCollection)
        }
        Text("塩を探す", Modifier.padding(20.dp), color=p.ink, fontSize=20.sp, fontFamily=FontFamily.Serif)
        Button(onClick=onCatalog, modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp), colors=ButtonDefaults.buttonColors(containerColor=p.accent)) { Text("塩図鑑を開く") }
        Text("製塩文化の豆知識", Modifier.padding(20.dp), color=p.ink, fontSize=18.sp, fontFamily=FontFamily.Serif)
        Text("日本では地域の海や気候に合わせ、揚げ浜式・入浜式・流下式などの製塩文化が育まれてきました。", Modifier.padding(horizontal=20.dp), color=p.ink, lineHeight=22.sp)
    }
}
@Composable private fun InfoCard(title: String, value: String, p: Palette, modifier: Modifier, click: () -> Unit) { Card(modifier.clickable(onClick=click), colors=CardDefaults.cardColors(containerColor=p.card), shape=RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text(title, color=p.ink); Text(value, color=p.accent, fontWeight=FontWeight.Bold, fontSize=20.sp) } } }

private val prefectures = listOf("北海道","青森県","岩手県","宮城県","秋田県","山形県","福島県","茨城県","栃木県","群馬県","埼玉県","千葉県","東京都","神奈川県","新潟県","富山県","石川県","福井県","山梨県","長野県","岐阜県","静岡県","愛知県","三重県","滋賀県","京都府","大阪府","兵庫県","奈良県","和歌山県","鳥取県","島根県","岡山県","広島県","山口県","徳島県","香川県","愛媛県","高知県","福岡県","佐賀県","長崎県","熊本県","大分県","宮崎県","鹿児島県","沖縄県")
private fun prefectureOf(region: String): String? = prefectures.firstOrNull { region.contains(it) }
private data class PrefecturePart(val code: String, val name: String, val points: List<GeoPoint>)
private fun coordinateRing(coords: org.json.JSONArray): List<GeoPoint> = (0 until coords.length()).mapNotNull { i ->
    val pair = coords.optJSONArray(i) ?: return@mapNotNull null
    if (pair.length() < 2) null else GeoPoint(pair.optDouble(1), pair.optDouble(0))
}
private fun parsePrefectures(raw: String): List<PrefecturePart> {
    val features = JSONObject(raw).getJSONArray("features")
    require(features.length() == 47) { "GeoJSON must contain all 47 prefectures" }
    val output = mutableListOf<PrefecturePart>()
    for (i in 0 until features.length()) {
        val feature = features.getJSONObject(i)
        val props = feature.getJSONObject("properties")
        val name = props.optString("N03_001", props.optString("name"))
        val rawCode = props.optString("PREF_CODE", props.optString("PREF", "${i + 1}".padStart(2, '0')))
        val code = "JP-$rawCode"
        require(name in prefectures) { "Unknown prefecture: $name" }
        val geom = feature.getJSONObject("geometry")
        val coordinates = geom.getJSONArray("coordinates")
        val groups = when (geom.getString("type")) {
            "Polygon" -> listOf(coordinates)
            "MultiPolygon" -> (0 until coordinates.length()).map { coordinates.getJSONArray(it) }
            else -> error("Unsupported GeoJSON geometry: ${geom.getString("type")}")
        }
        groups.forEach { polygon ->
            val points = coordinateRing(polygon.getJSONArray(0))
            if (points.size >= 4) output += PrefecturePart(code, name, points)
        }
    }
    require(output.map { it.name }.toSet().size == 47) { "GeoJSON is missing prefectures" }
    return output
}
private fun prefectureColor(count: Int, p: Palette): Color = when {
    count <= 0 -> p.paper
    count <= 2 -> p.accent.copy(alpha=.28f).compositeOver(p.paper)
    count <= 5 -> p.accent.copy(alpha=.52f).compositeOver(p.paper)
    count <= 10 -> p.accent.copy(alpha=.78f).compositeOver(p.paper)
    else -> Color(0xFFB74436)
}
private fun refreshMap(map: MapView, p: Palette, salts: List<SaltEntity>, mine: List<MySaltEntity>, mode: String, selectedPrefecture: String?, selectedMethod: String?, onOpen: (SaltEntity) -> Unit, onSelect: (String) -> Unit, boundaries: List<PrefecturePart>) {
    map.overlays.clear()
    map.setBackgroundColor(p.sea.toArgb())
    applyJapaneseMapFilter(map, p.motif)
    val collectedIds = mine.filter { it.isFavorite || it.purchased || it.tasted }.map { it.saltId }.toSet()
    val shown = when {
        mode == "コレクション" -> salts.filter { it.id in collectedIds }
        mode == "製法別" && selectedMethod != null -> salts.filter { it.method.contains(selectedMethod) }
        else -> salts
    }
    val counts = when (mode) {
        "コレクション" -> shown.mapNotNull { prefectureOf(it.region) }.groupingBy { it }.eachCount()
        "製法別" -> shown.mapNotNull { prefectureOf(it.region) }.groupingBy { it }.eachCount()
        else -> salts.mapNotNull { prefectureOf(it.region) }.groupingBy { it }.eachCount()
    }
    boundaries.forEach { part ->
        val selected = part.name == selectedPrefecture
        val count = counts[part.name] ?: 0
        map.overlays.add(Polygon(map).apply {
            points = part.points
            fillColor = prefectureColor(count, p).toArgb()
            strokeColor = if (selected) android.graphics.Color.rgb(200, 54, 42) else p.ink.copy(alpha=.75f).toArgb()
            strokeWidth = if (selected) 4f else 1.5f
            setOnClickListener { _, _, _ -> onSelect(part.name); true }
        })
    }
    val markers = shown.filter { it.latitude != null && it.longitude != null && (selectedPrefecture == null || prefectureOf(it.region) == selectedPrefecture) }
    val cell = (0.15 * Math.pow(2.0, 5.0 - map.zoomLevelDouble)).coerceAtLeast(.003)
    markers.groupBy { "${kotlin.math.floor(it.latitude!! / cell).toInt()}:${kotlin.math.floor(it.longitude!! / cell).toInt()}" }.values.forEach { cluster ->
        val lat = cluster.mapNotNull { it.latitude }.average()
        val lon = cluster.mapNotNull { it.longitude }.average()
        val isCluster = cluster.size > 1
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(lat, lon)
            title = if (isCluster) "${cluster.size}件の塩" else cluster.first().name
            snippet = if (isCluster) "タップして拡大" else "${cluster.first().region}｜${cluster.first().method}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (cluster.any { it.id in collectedIds }) android.graphics.Color.rgb(190, 145, 46) else p.accent.toArgb())
                setStroke(3, android.graphics.Color.WHITE)
                setSize(if (isCluster) 50 else 42, if (isCluster) 50 else 42)
            }
            if (isCluster) {
                setTextLabelBackgroundColor(p.accent.toArgb())
                setTextLabelForegroundColor(p.ink.toArgb())
                setTextIcon(cluster.size.toString())
            }
            setOnMarkerClickListener { _, _ ->
                if (isCluster) { map.controller.animateTo(GeoPoint(lat, lon)); map.controller.setZoom((map.zoomLevelDouble + 2).coerceAtMost(17.0)) }
                else onOpen(cluster.first())
                true
            }
        })
    }
    map.invalidate()
}
@Composable private fun SaltMapCanvas(p: Palette, salts: List<SaltEntity>, mine: List<MySaltEntity>, modifier: Modifier, onOpen: (SaltEntity) -> Unit) {
    val context = LocalContext.current
    var boundaries by remember { mutableStateOf<List<PrefecturePart>?>(null) }
    var boundaryError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = runCatching { withContext(Dispatchers.IO) {
                val raw = context.assets.open("prefectures.geojson").bufferedReader().use { it.readText() }
                parsePrefectures(raw)
            }
        }
        boundaries = result.getOrNull()
        boundaryError = result.isFailure
    }
    Box(modifier.border(1.dp, p.accent)) {
        if (boundaries == null) Text(if(boundaryError) "都道府県境界を読み込めませんでした" else "都道府県境界を読み込み中…", Modifier.align(Alignment.Center), color=p.ink)
        AndroidView(factory = { ctx -> MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(4.8); controller.setCenter(GeoPoint(36.2, 138.2))
        } }, update = { map -> boundaries?.let { refreshMap(map,p,salts,mine,"全国の塩",null,null,onOpen,{},it) } }, modifier=Modifier.matchParentSize())
    }
}
@Composable private fun MapScreen(p: Palette, salts: List<SaltEntity>, mine: List<MySaltEntity>, onOpen: (SaltEntity) -> Unit) {
    val context = LocalContext.current
    var boundaries by remember { mutableStateOf<List<PrefecturePart>?>(null) }
    var boundaryError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = runCatching { withContext(Dispatchers.IO) {
            val raw = context.assets.open("prefectures.geojson").bufferedReader().use { it.readText() }
            parsePrefectures(raw)
        } }
        boundaries = result.getOrNull()
        boundaryError = result.isFailure
    }
    var mode by remember { mutableStateOf("商品数") }
    var selectedPrefecture by remember { mutableStateOf<String?>(null) }
    var selectedMethod by remember { mutableStateOf<String?>(null) }
    var showLegend by remember { mutableStateOf(true) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val methods = salts.map { it.method }.filter { it.isNotBlank() }.distinct()
    val shown = when {
        mode == "コレクション" -> salts.filter { salt -> mine.any { it.saltId == salt.id && (it.isFavorite || it.purchased || it.tasted) } }
        mode == "製法別" && selectedMethod != null -> salts.filter { it.method.contains(selectedMethod!!) }
        else -> salts
    }
    val counts = shown.mapNotNull { prefectureOf(it.region) }.groupingBy { it }.eachCount()
    Column(Modifier.fillMaxSize()) {
        Header(p, "全国塩マップ｜47都道府県")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("全国の塩", "商品数", "製法別", "コレクション").forEach { option ->
                FilterChip(selected = mode == option, onClick = { mode = option; selectedMethod = null }, label = { Text(option) })
            }
            TextButton(onClick={ selectedPrefecture=null; selectedMethod=null; mapRef?.controller?.setZoom(4.8); mapRef?.controller?.setCenter(GeoPoint(36.2,138.2)) }) { Text("全国表示") }
        }
        if (mode == "製法別") Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            methods.forEach { method -> FilterChip(selected=selectedMethod==method,onClick={selectedMethod=method},label={Text(method)}) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedPrefecture == null, onClick = { selectedPrefecture = null }, label={Text("全国")})
            prefectures.forEach { pref ->
                FilterChip(selected = selectedPrefecture == pref, onClick = { selectedPrefecture = pref }, label={Text("$pref ${counts[pref] ?: 0}")})
            }
        }
        val locatedCount = shown.count { it.latitude != null && it.longitude != null }
        val selectedCount = selectedPrefecture?.let { counts[it] } ?: if (selectedPrefecture == null) salts.size else 0
        Text("${selectedPrefecture ?: "全国"}｜登録商品 ${selectedCount}件｜位置確認済み $locatedCount地点", Modifier.padding(horizontal=12.dp), color=p.ink, fontSize=12.sp)
        AndroidView(factory = { ctx -> MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(4.8); controller.setCenter(GeoPoint(36.2, 138.2)); mapRef=this
        } }, update = { map -> mapRef=map; boundaries?.let { refreshMap(map,p,salts,mine,mode,selectedPrefecture,selectedMethod,onOpen,{selectedPrefecture=it},it) } }, modifier=Modifier.weight(1f).fillMaxWidth().border(2.dp,p.accent))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp), verticalAlignment=Alignment.CenterVertically) {
            if (showLegend) {
                listOf("0", "1–2", "3–5", "6–10", "11+").forEachIndexed { index, label ->
                    Row(Modifier.padding(end=8.dp), verticalAlignment=Alignment.CenterVertically) {
                        val legendCount = listOf(0,1,3,6,11)[index]
                        Box(Modifier.size(11.dp).background(prefectureColor(legendCount,p), RoundedCornerShape(3.dp)).border(1.dp, p.ink.copy(alpha=.35f), RoundedCornerShape(3.dp)))
                        Text(" $label", color=p.ink, fontSize=10.sp)
                    }
                }
            }
            TextButton(onClick={showLegend=!showLegend}) { Text(if(showLegend) "凡例を閉じる" else "凡例") }
        }
        if (showLegend) Text("色は都道府県の商品数｜丸印は塩の商品/製塩所。収集済みマーカーは金色。位置未確認の商品は地図に表示しません。", Modifier.padding(horizontal=10.dp), color=p.ink, fontSize=11.sp)
        Text(if (boundaryError) "境界データを読み込めません。アプリ内GeoJSONを確認してください。" else "境界データ：国土数値情報 行政区域データ（国土交通省、2026年、CC BY 4.0）。地図タイル：© OpenStreetMap contributors", Modifier.padding(8.dp), color=p.ink, fontSize=9.sp)
        if (selectedPrefecture != null) {
            val inPref = shown.filter { prefectureOf(it.region)==selectedPrefecture }
            Text("主な製法: ${inPref.map { it.method }.distinct().joinToString("・").ifBlank { "未登録" }}", Modifier.padding(horizontal=10.dp), color=p.accent, fontSize=11.sp)
            if (inPref.isEmpty()) Text("登録商品なし", Modifier.padding(horizontal=10.dp), color=p.ink, fontSize=12.sp)
            else Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) { inPref.forEach { item -> AssistChip(onClick={onOpen(item)},label={Text(item.name)}) } }
        }
    }
}
@Composable private fun CatalogScreen(p: Palette, rows: List<SaltEntity>, search: String, onSearch: (String)->Unit, open: (SaltEntity)->Unit) {
    Column(Modifier.fillMaxSize()) { Header(p, "確認できた情報だけを掲載")
        OutlinedTextField(search, onSearch, Modifier.fillMaxWidth().padding(horizontal=16.dp), label={Text("商品・地域・製法を検索")}, leadingIcon={Icon(Icons.Default.Search,null)}, singleLine=true)
        LazyColumn { items(rows) { s -> Card(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=6.dp).clickable { open(s) }, colors=CardDefaults.cardColors(containerColor=p.card)) { Column(Modifier.padding(16.dp)) { Text(s.name, color=p.ink, fontWeight=FontWeight.Bold, fontSize=18.sp); Text("${s.region}　・　${s.method}", color=p.accent); Text(s.maker, color=p.ink.copy(alpha=.65f), fontSize=12.sp) } } } }
    }
}
@Composable private fun SaltDetailDialog(
    s: SaltEntity, p: Palette, record: MySaltEntity?,
    onSave: (Boolean, Boolean, Boolean, Int, String) -> Unit, onClose: () -> Unit
) {
    var favorite by remember(s.id, record?.isFavorite) { mutableStateOf(record?.isFavorite ?: false) }
    var purchased by remember(s.id, record?.purchased) { mutableStateOf(record?.purchased ?: false) }
    var tasted by remember(s.id, record?.tasted) { mutableStateOf(record?.tasted ?: false) }
    var rating by remember(s.id, record?.rating) { mutableIntStateOf(record?.rating ?: 0) }
    var note by remember(s.id, record?.note) { mutableStateOf(record?.note ?: "") }
    AlertDialog(onDismissRequest=onClose, title={Text(s.name, color=p.ink)}, text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${s.region} / ${s.method}", color=p.accent, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text(s.description, color=p.ink)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={favorite = !favorite}) {
                    Icon(if(favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription=if(favorite) "お気に入り解除" else "お気に入り登録",
                        tint=if(favorite) Color(0xFFD94D68) else p.accent)
                }
                Text(if(favorite) "お気に入り登録済み" else "お気に入りに追加", color=p.ink)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=purchased, onCheckedChange={purchased=it}); Text("購入・入手済み", color=p.ink)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=tasted, onCheckedChange={tasted=it}); Text("試食・制覇済み", color=p.ink)
            }
            Text("評価（0〜5）", color=p.ink)
            Row { (1..5).forEach { star -> IconButton(onClick={rating=if(rating==star) 0 else star}, modifier=Modifier.size(40.dp)) {
                Icon(if(star<=rating) Icons.Default.Star else Icons.Default.StarBorder, "評価$star", tint=p.accent)
            } } }
            OutlinedTextField(value=note,onValueChange={note=it},label={Text("感想・購入場所などのメモ")},minLines=2,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp)); Text("成分（100g当たり）", fontWeight=FontWeight.Bold, color=p.ink)
            Text("ナトリウム: ${s.sodiumMg100g?.let { "$it mg" } ?: "未公表"}\nマグネシウム: ${s.magnesiumMg100g?.let { "$it mg" } ?: "未公表"}\nカリウム: ${s.potassiumMg100g?.let { "$it mg" } ?: "未公表"}\nカルシウム: ${s.calciumMg100g?.let { "$it mg" } ?: "未公表"}", color=p.ink)
            Spacer(Modifier.height(12.dp)); Text("マイクロプラスチック：${s.microplasticsStatus}\n未公表は未検出を意味しません。", color=p.ink)
            Spacer(Modifier.height(12.dp)); Text("原材料: ${s.ingredients}\n製造地: ${s.manufacturingLocation ?: "未確認"}\n原料採取地: ${s.rawMaterialLocation ?: "未確認"}\n出典: ${s.sourceUrl}\n位置出典: ${s.locationSourceUrl ?: "未登録"}\n確認日: ${s.lastChecked}", fontSize=11.sp, color=p.ink.copy(alpha=.7f))
        }
    }, confirmButton={ TextButton(onClick={onSave(favorite,purchased,tasted,rating,note);onClose()}) { Text("記録を保存") } },
       dismissButton={TextButton(onClick=onClose) { Text("キャンセル") }}, containerColor=p.card)
}
@Composable private fun CollectionScreen(p: Palette, rows: List<SaltEntity>, mine: List<MySaltEntity>, onOpen: (SaltEntity)->Unit) {
    val favorites = mine.filter { it.isFavorite }.map { it.saltId }.toSet()
    val conquered = mine.filter { it.tasted }.map { it.saltId }.toSet()
    val acquired = mine.filter { it.purchased }.map { it.saltId }.toSet()
    Column(Modifier.fillMaxSize()) {
        Header(p,"集めた塩を記録")
        Text("制覇 ${conquered.size} 件　・　入手 ${acquired.size} 件　・　お気に入り ${favorites.size} 件", Modifier.padding(16.dp),color=p.ink,fontWeight=FontWeight.Bold)
        val tracked = rows.filter { it.id in favorites || it.id in conquered || it.id in acquired }
        if (tracked.isEmpty()) Text("図鑑の商品詳細から、♡・入手済み・制覇済みを登録できます。",Modifier.padding(16.dp),color=p.ink)
        LazyColumn { items(tracked) { salt ->
            val m = mine.first { it.saltId == salt.id }
            Card(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp).clickable { onOpen(salt) },colors=CardDefaults.cardColors(containerColor=p.card)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(m.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint=if(m.isFavorite) Color(0xFFD94D68) else p.accent)
                        Spacer(Modifier.width(8.dp)); Text(salt.name,color=p.ink,fontWeight=FontWeight.Bold)
                    }
                    Text(listOfNotNull(if(m.tasted) "制覇済み" else null,if(m.purchased) "入手済み" else null,if(m.rating>0) "評価 ${m.rating}/5" else null).joinToString(" ・ ").ifBlank { "お気に入り" },color=p.accent)
                    if(m.note.isNotBlank()) Text(m.note,color=p.ink,maxLines=2)
                }
            }
        } }
    }
}
@Composable private fun SettingsScreen(p: Palette, selected: String, onSelect: (String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Header(p,"和の色調を選ぶ")
        Text("テーマ", Modifier.padding(18.dp), color=p.ink, fontFamily=FontFamily.Serif, fontSize=20.sp)
        themes.forEachIndexed { _, (name, color) -> Card(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=5.dp).clickable { onSelect(name) }, colors=CardDefaults.cardColors(containerColor=color.paper), border=if(name==selected) BorderStroke(2.dp,color.accent) else null) {
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                ThemePattern(color, Modifier.matchParentSize())
                Row(Modifier.fillMaxSize().padding(horizontal=15.dp), verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(color.accent, RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start=12.dp)) { Text(name, color=color.ink, fontFamily=color.headingFont, fontWeight=FontWeight.Bold); Text("和風テーマのプレビュー", color=color.ink.copy(alpha=.72f), fontSize=12.sp) }
                    if(name==selected) Icon(Icons.Default.Check,null,tint=color.accent)
                }
            }
        } }
        Text("β版：8テーマを利用できます。AdMobはテスト広告IDです。全機能無料・広告収益型です。本番用広告は未設定です。", Modifier.padding(18.dp), color=p.ink, fontSize=12.sp)
    }
}
@Composable private fun TestBanner() { AndroidView(factory={ context -> AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId="ca-app-pub-3940256099942544/6300978111"; loadAd(AdRequest.Builder().build()) } }, modifier=Modifier.fillMaxWidth().height(50.dp)) }

/** Applies a theme-specific colour matrix to the live, interactive map view.
 * This is a stylised modern OSM map, NOT a historical map tile set.
 */
private fun applyJapaneseMapFilter(map: MapView, motif: String) {
    val matrix = when (motif) {
        "washi" -> floatArrayOf(.78f,.15f,.07f,0f,18f, .13f,.76f,.11f,0f,11f, .10f,.18f,.67f,0f,3f, 0f,0f,0f,1f,0f)
        "ukiyoe" -> floatArrayOf(.85f,.08f,.07f,0f,10f, .06f,.82f,.12f,0f,1f, .02f,.19f,.79f,0f,10f, 0f,0f,0f,1f,0f)
        "sumi" -> floatArrayOf(.30f,.59f,.11f,0f,8f, .30f,.59f,.11f,0f,8f, .30f,.59f,.11f,0f,8f, 0f,0f,0f,1f,0f)
        "edo" -> floatArrayOf(.53f,.40f,.07f,0f,35f, .25f,.57f,.18f,0f,13f, .18f,.44f,.38f,0f,-12f, 0f,0f,0f,1f,0f)
        "indigo" -> floatArrayOf(.47f,.38f,.15f,0f,-13f, .10f,.72f,.18f,0f,-4f, .05f,.30f,.65f,0f,20f, 0f,0f,0f,1f,0f)
        "sakura" -> floatArrayOf(.89f,.09f,.02f,0f,10f, .13f,.81f,.06f,0f,3f, .14f,.16f,.70f,0f,6f, 0f,0f,0f,1f,0f)
        "maki-e" -> floatArrayOf(.50f,.38f,.12f,0f,-28f, .20f,.65f,.15f,0f,-30f, .14f,.27f,.59f,0f,-29f, 0f,0f,0f,1f,0f)
        else -> floatArrayOf(.85f,.10f,.05f,0f,6f, .10f,.82f,.08f,0f,4f, .10f,.15f,.75f,0f,3f, 0f,0f,0f,1f,0f)
    }
    map.setLayerType(android.view.View.LAYER_TYPE_HARDWARE,
        Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) })
}
