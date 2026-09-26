package app.echo.android.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * WAV LIST/INFO, CUE, ID3v1 and ID3 encoding 0 have no reliable charset flag.
 * Valid UTF-8 and UTF-16 are kept. Otherwise GBK, Big5, Shift_JIS, EUC-KR and
 * Latin-1/Windows-1252 are scored. A declared encoding stays strict in [decodeDeclared].
 */
internal object TagTextDecoder {
    fun decode(bytes: ByteArray): String? {
        // A UTF-16 code unit can end in 00; never trim bytes before detecting Unicode.
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> return decodeDeclared(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
            bytes.startsWith(0xFF, 0xFE) -> return decodeDeclared(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)
            bytes.startsWith(0xFE, 0xFF) -> return decodeDeclared(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
        }
        var unicodeEnd = bytes.size
        while (unicodeEnd >= 2 && bytes[unicodeEnd - 1] == 0.toByte() && bytes[unicodeEnd - 2] == 0.toByte()) unicodeEnd -= 2
        val unicodePayload = if (unicodeEnd == bytes.size) bytes else bytes.copyOf(unicodeEnd)
        if (unicodePayload.looksLikeUtf16LittleEndian()) {
            decodeDeclared(bytes, StandardCharsets.UTF_16LE)?.let { return it }
        }
        if (unicodePayload.looksLikeUtf16BigEndian()) {
            decodeDeclared(bytes, StandardCharsets.UTF_16BE)?.let { return it }
        }
        val payload = bytes.trimTagPadding()
        if (payload.isEmpty()) return ""

        decodeStrict(payload, StandardCharsets.UTF_8)?.takeIf { it.isReadableText() }?.let { return it.trim() }
        return pickBest(payload)?.trim()
    }

    /** Honor a declared encoding; malformed Unicode must not be guessed as another language. */
    fun decodeDeclared(bytes: ByteArray, charset: Charset): String? =
        decodeStrict(bytes, charset)?.substringBefore('\u0000')?.trimStart('\uFEFF')
            ?.takeIf { it.isReadableText() }?.trim()

    private fun pickBest(bytes: ByteArray): String? {
        val found = ArrayList<Candidate>(6)
        fun add(text: String?, family: Family) {
            val value = text?.takeIf { it.isNotBlank() && it.isReadableText() } ?: return
            found += Candidate(value, family, score(value, family, bytes))
        }
        val gbk = decodeStrict(bytes, Gbk)
        if (gbk != null) add(gbk, Family.Gbk) else add(decodeStrict(bytes, Gb18030), Family.Gbk)
        add(Big5?.let { decodeStrict(bytes, it) }, Family.Big5)
        add(ShiftJis?.let { decodeStrict(bytes, it) }, Family.ShiftJis)
        val korean = EucKr?.let { decodeStrict(bytes, it) } ?: Ms949?.let { decodeStrict(bytes, it) }
        add(korean, Family.EucKr)
        add(decodeStrict(bytes, StandardCharsets.ISO_8859_1), Family.Latin)
        add(decodeStrict(bytes, Windows1252), Family.Latin)
        return found.filter { it.score > Int.MIN_VALUE / 2 }.maxByOrNull { it.score }?.text
    }

    private fun score(text: String, family: Family, bytes: ByteArray): Int {
        var cjk = 0
        var kana = 0
        var hangul = 0
        var asciiLetters = 0
        var latinExt = 0
        var weird = 0
        var pua = 0
        for (ch in text) {
            when {
                ch.isPrivateUse() -> pua++
                ch in '\uFF61'..'\uFF9F' -> weird++
                ch.isHangul() -> hangul++
                ch.isKana() -> kana++
                ch.isUnifiedHan() -> cjk++
                ch.isLetter() && ch.code < 0x80 -> asciiLetters++
                ch.isLetter() && ch.code <= 0x024F -> latinExt++
                ch.isDigit() || ch.isWhitespace() || ch in CommonPunctuation -> Unit
                else -> weird++
            }
        }
        var total = cjk * 3 + hangul * 3 + kana * 3 + asciiLetters * 2
        total -= pua * 40
        total -= weird * 8
        val commonHan = text.count { it in CommonHan }
        val commonHangul = text.count { it in CommonHangul }
        total += commonHan * 9
        total += commonHangul * 9
        when (family) {
            Family.ShiftJis -> {
                total += kana * 8
                if (cjk + kana > 0 && dbcsLeadsBelow(bytes, 0xA1)) total += 16
                else if (cjk > 0 && kana == 0 && shiftJisLeadRun(bytes)) total += 12
            }
            Family.EucKr -> total += commonHangul * 6
            Family.Latin -> {
                if (latinExt in 1..asciiLetters.coerceAtLeast(1) && latinExt <= asciiLetters) {
                    total += latinExt * 8
                } else if (latinExt > asciiLetters) {
                    total -= latinExt * 6
                }
                val isolated = isolatedLatinHighCount(bytes)
                if (isolated > 0 && freeAsciiLetters(bytes) > 0) total += isolated * 10
            }
            Family.Gbk, Family.Big5 -> {
                total -= kana * 6
                if (asciiLetters > 0) total -= asciiTrailCount(bytes) * 12
                if (asciiLetters == 0 && cjk > 0 && pua == 0 && weird == 0) total += 6
            }
        }
        return total
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .trimEnd('\u0000')
        } catch (_: CharacterCodingException) {
            null
        }

    private fun asciiTrailCount(bytes: ByteArray): Int {
        var index = 0
        var count = 0
        while (index < bytes.size) {
            val lead = bytes[index].toInt() and 0xFF
            if (lead < 0x80) {
                index += 1
                continue
            }
            if (index + 1 >= bytes.size) break
            val trail = bytes[index + 1].toInt() and 0xFF
            if (trail in 0x41..0x5A || trail in 0x61..0x7A) count += 1
            index += 2
        }
        return count
    }

    private fun freeAsciiLetters(bytes: ByteArray): Int {
        var letters = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value in 0x41..0x5A || value in 0x61..0x7A) letters += 1
        }
        return (letters - asciiTrailCount(bytes)).coerceAtLeast(0)
    }

    /** High byte in the Latin-1 letter range, with ASCII on both sides. */
    private fun isolatedLatinHighCount(bytes: ByteArray): Int {
        var count = 0
        for (index in bytes.indices) {
            val value = bytes[index].toInt() and 0xFF
            if (value !in 0xC0..0xFF) continue
            val previousAscii = index == 0 || (bytes[index - 1].toInt() and 0xFF) < 0x80
            val nextAscii = index + 1 >= bytes.size || (bytes[index + 1].toInt() and 0xFF) < 0x80
            if (previousAscii && nextAscii) count += 1
        }
        return count
    }

    private fun dbcsLeadsBelow(bytes: ByteArray, threshold: Int): Boolean {
        var pairs = 0
        var low = 0
        var index = 0
        while (index < bytes.size) {
            val lead = bytes[index].toInt() and 0xFF
            if (lead < 0x80) {
                index += 1
                continue
            }
            if (index + 1 >= bytes.size) break
            pairs += 1
            if (lead < threshold) low += 1
            index += 2
        }
        return pairs > 0 && low == pairs
    }

    /** Shift_JIS leads are 0x81..0x9F and 0xE0..0xFC, never the GB2312 zone in between. */
    private fun shiftJisLeadRun(bytes: ByteArray): Boolean {
        var pairs = 0
        var index = 0
        while (index < bytes.size) {
            val lead = bytes[index].toInt() and 0xFF
            if (lead < 0x80) {
                index += 1
                continue
            }
            if (index + 1 >= bytes.size) return false
            val shiftJisLead = lead in 0x81..0x9F || lead in 0xE0..0xFC
            if (!shiftJisLead) return false
            pairs += 1
            index += 2
        }
        return pairs > 0
    }

    private fun ByteArray.trimTagPadding(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1] == 0.toByte()) end -= 1
        if (end == size) return this
        return if (end <= 0) ByteArray(0) else copyOfRange(0, end)
    }

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { index -> this[index].toInt() and 0xFF == values[index] }

    private fun ByteArray.looksLikeUtf16LittleEndian(): Boolean =
        size >= 4 && size % 2 == 0 && zeroRatio(startIndex = 1) >= Utf16ZeroRatioThreshold

    private fun ByteArray.looksLikeUtf16BigEndian(): Boolean =
        size >= 4 && size % 2 == 0 && zeroRatio(startIndex = 0) >= Utf16ZeroRatioThreshold

    private fun ByteArray.zeroRatio(startIndex: Int): Float {
        var total = 0
        var zeros = 0
        var index = startIndex
        val sampleSize = minOf(size, 512)
        while (index < sampleSize) {
            total += 1
            if (this[index].toInt() == 0) zeros += 1
            index += 2
        }
        return if (total == 0) 0f else zeros.toFloat() / total.toFloat()
    }

    private fun String.isReadableText(): Boolean {
        if (isEmpty()) return true
        return none { char ->
            char == '\u0000' ||
                char == '\uFFFD' ||
                (char.isISOControl() && char != '\n' && char != '\r' && char != '\t')
        }
    }

    private fun Char.isPrivateUse(): Boolean = code in 0xE000..0xF8FF

    private fun Char.isHangul(): Boolean = this in '\uAC00'..'\uD7AF' || this in '\u1100'..'\u11FF'

    private fun Char.isKana(): Boolean =
        this in '\u3040'..'\u30FF' || this in '\u31F0'..'\u31FF'

    private fun Char.isUnifiedHan(): Boolean =
        this in '\u3400'..'\u4DBF' || this in '\u4E00'..'\u9FFF'

    private enum class Family { Gbk, Big5, ShiftJis, EucKr, Latin }

    private data class Candidate(val text: String, val family: Family, val score: Int)

    private val CommonHan: Set<Char> = COMMON_HAN.toSet()
    private val CommonHangul: Set<Char> = COMMON_HANGUL.toSet()

    private val Gb18030: Charset = Charset.forName("GB18030")
    private val Gbk: Charset = Charset.forName("GBK")
    private val Windows1252: Charset = Charset.forName("windows-1252")
    private val Big5: Charset? = charsetOrNull("Big5")
    private val ShiftJis: Charset? = charsetOrNull("Shift_JIS")
    private val EucKr: Charset? = charsetOrNull("EUC-KR")
    private val Ms949: Charset? = charsetOrNull("MS949")
    private const val Utf16ZeroRatioThreshold = 0.3f
    private const val CommonPunctuation = "'’‘\"“”.,:;!?-–—()/&·…€"
}

private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()
private const val COMMON_HAN =
    "的一是不了人我在有他这为之大来以个中上们到说国和地也子时道出而要于就下得可你年生自会那后能对着事其里所去行过家十用发天如然作方成者多日都三小军二无同么经法当起与好看学进种将还分此心前面又定见只主没公从已长现开手点实情面力给名正外相并间因些但所高意部样水万各己问很最再真两她它吧啊呀" +
    "爱音乐歌曲专辑现场版本原声演唱会精选经典流行摇滚民谣爵士电子舞曲青春时光岁月星空海洋山川风花雪月雨夜晨光影声色梦回首转身离开回来记得忘记喜欢心碎眼泪微笑眼睛世界人间永远一起孤单寂寞温柔疯狂安静轻轻慢慢忽然突然已经可以应该不会没有什么怎么为什么因为所以如果但是而且或者还是就是只是" +
    "青花瓷周杰伦倫七里香晴天告白气球氣球起風风了东東破发如雪千里之外兰亭序菊花台夜曲听聽海朋友再见征服红紅豆勇气勇氣后来後來测试測試专辑專輯不能说的秘密海阔天空光辉光輝岁月月亮代表我的心小幸运演员成都" +
    "残酷紅莲蓮華花版雨六兆年一夜物語物语測试試專輯晴朗星星月光影子声音颜色味道记忆故事爱情家人孩子女人男人先生小姐老师学生工作生活城市乡村道路河流春天夏天秋天冬天早晨黄昏" +
    "航朝银金木火土山石田刀弓云五京亭亮今令们件任何作你佳信修停健光全八六共兵具内冬冰几凡分刘刚利别到制前功加务动助劳勇包北区十千午半华单南博印危却厂历压原去又友反取受变口古只叫可台史右叶号司吃各合同名后向吗君呀味和命周哈哦哪哭哲唐啦喂喜喝喷嘛嘴四回因团园困围国图圆土在场地坐城基堂堡塔塞墙士声处夏夕外多夜大天太夫头奇奔奖女她好如妈姐妹妻始姜姬娃娘娜娟婆婚婴子字学孩宁宅安宋完官定宝实客家容宽宿密富寒察寸对寻导寿封射将小少尖尚就尽层展属山岁岛岸岩岭岳峰川州工左巧巨差己已巴市布师希帝带席常帽干平年并幸幻幼广庄庆床序库应店庙府度座庭康庸廉延建开异弃式弓引张弦弱归当形彩影往很律后得从心必忆忘忙忠快念忽怀怎怒怕思急性总恋恐恨恩息恰恶情惜想感愿慈慢慧忧懂应我成或战户所扇手打找把报拉持指按挑接推提握摇支收改放政故效教数文斗料新方于施旁旅族旗无既日旦旧早时明易星春昨是昼晚景晴晶暖暗暮暴晓曰曲更书最月有朋服望朝期木未本机权李村杜条来杨杯东松板林果枝架柄某查柱柳柴树校样根格桂桃案桌桐梅梦梨梯检棉棋棍棒森棵植楚楼概乐模桥机横欠次欢欧欲歌止正此步武岁历死残段母每比毛氏民气水永江池汤没河油治法波注泪泰洗洞活派流海浪浮浴消涉深清淡混添港湖源满漂汉渔演漏潜潮潭激火灯灰灵灶炎炉点炼烈烤烟烧热然照熟爪父爸片版牌牙牛物特犬犯状狂狗独狼猫猴猪率玉王玩环现玲珍珠球理琴琵琶琼璃瓜瓶瓷甘甜生用田由甲电男画界留番疗病痛白百皆皇皮皿目看真眼睡知短石破确碗磁示礼社祈神票祭福离种科秒租秦移程税稳空穿窗立站竞章童端笑笔笛等算管简米类粉粗精糖系红约级纯纸细终组经结给绝统绿编缘缶网罗罪美群羽老者而耳听肉肌肝肠股肥肩肺胃背胆胸脚脑脸腰腿自至致舌舍舞舟航般舰船色艺艾节花芳若英茂范茶草荣药荷莉莫莱菊菜菩菲萄萌萍落叶著葛董葫葬蒜蒙蓝薄藤兰虎虫虹虾蚁蚊蚕蛋蛙蛛蜜蜡蝶血行街衣表被装西要见规视观角解言计订认让训议记讲许设访证评词试诗话该语误说请谁调论谢谱谷豆象贝贵买费质走起超越足路跳跟跨踏身车转轻较边过这进远送追退速造道那邮乡酒配酷采里重金针钟铁银铜钱错镜长门开间闻队阳阴阶除险随雨雪零雷需震霜露青静非面革鞋音页顶顺须预领头颜风飞食饭饮饱首香马骑骨高鬼鱼鸟鸡鸭鸽鹿麦黄黑齐齿龙"

private const val COMMON_HANGUL =
    "가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후기니디리미비시이지치키티피히" +
    "은는이가을를에의도와한사랑노래음악봄여름가을겨울날아이유좋아대한민국서울부산하늘바다사람마음시간오늘내일어제지금항상함께혼자다시또매우정말우리너나그그녀"
