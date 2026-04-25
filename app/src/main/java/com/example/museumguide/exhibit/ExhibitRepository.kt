package com.example.museumguide.exhibit

import com.example.museumguide.model.Exhibit
import com.example.museumguide.model.ExhibitDao

/**
 * Repository layer that mediates between data sources
 * (Room database, network, assets) and the rest of the app.
 *
 * For the initial version data is loaded from a pre-populated Room database.
 * A future version may sync with a server for dynamic exhibit updates.
 */
class ExhibitRepository(private val exhibitDao: ExhibitDao) {

    /**
     * Look up an exhibit by its assigned recognition label.
     * Used after object detection returns a matched label.
     */
    suspend fun findExhibitByLabel(label: String): Exhibit? {
        // For demo: map common COCO labels to fictional exhibits.
        // In production, use a museum-specific recognition model whose
        // output class IDs map directly to exhibit codes.
        val code = LABEL_TO_CODE[label]
        return code?.let { exhibitDao.getExhibitByCode(it) }
    }

    suspend fun getExhibitById(id: Long): Exhibit? {
        return exhibitDao.getExhibitById(id)
    }

    suspend fun getAllExhibits(): List<Exhibit> {
        return exhibitDao.getAllExhibits()
    }

    suspend fun getExhibitsByHall(hallId: String): List<Exhibit> {
        return exhibitDao.getExhibitsByHall(hallId)
    }

    /**
     * Pre-populate the database with sample exhibits for demo purposes.
     * This should be replaced with a real museum dataset.
     */
    suspend fun seedSampleData() {
        if (exhibitDao.getCount() > 0) return

        val samples = listOf(
            Exhibit(
                exhibitCode = "vase",
                name = "青花瓷瓶",
                era = "明代 · 永乐年间",
                brief = "明代永乐青花瓷，纹饰精美，釉色莹润",
                description = "这件青花瓷瓶高45厘米，口径12厘米，底径14厘米。" +
                    "瓶身绘有缠枝莲纹，青花发色浓艳，是明代永乐官窑的典型器物。" +
                    "采用进口苏麻离青料绘制，纹饰层次丰富，具有极高的艺术价值。",
                significance = "青花瓷是中国陶瓷史上最重要的品种之一。" +
                    "永乐时期的青花瓷以其独特的艺术风格和高超的制作工艺享誉世界。" +
                    "这件器物见证了明代海上丝绸之路的繁荣，是中外文化交流的重要实物证据。",
                hallId = "hall_a",
                mapPositionX = 0.3f,
                mapPositionY = 0.4f
            ),
            Exhibit(
                exhibitCode = "vase_2",
                name = "粉彩花卉纹瓶",
                era = "清代 · 乾隆年间",
                brief = "乾隆粉彩瓶，色彩绚丽，绘画精湛",
                description = "粉彩花卉纹瓶高38厘米，运用了粉彩、珐琅彩等多种装饰工艺。" +
                    "瓶身绘有牡丹、荷花、菊花、梅花四季花卉，寓意富贵吉祥。" +
                    "构图繁而不乱，色彩丰富和谐，代表了清代粉彩瓷器的最高水平。",
                significance = "粉彩是清代最重要的釉上彩品种之一，始于康熙，盛于乾隆。" +
                    "这件器物体现了乾隆时期＂中体西用＂的美学思想，" +
                    "融合了中国传统绘画技法与西洋珐琅彩工艺，是中国陶瓷史上的杰作。",
                hallId = "hall_a",
                mapPositionX = 0.6f,
                mapPositionY = 0.5f
            ),
            Exhibit(
                exhibitCode = "bronze",
                name = "青铜鼎",
                era = "西周 · 早期",
                brief = "西周青铜鼎，造型庄重，铭文珍贵",
                description = "此鼎通高58厘米，口径42厘米，重约25公斤。" +
                    "立耳、深腹、柱足，腹部饰有兽面纹和云雷纹。" +
                    "鼎内壁铸有铭文24字，记载了周王赏赐贵族的事件。" +
                    "造型雄浑厚重，纹饰神秘威严，是西周青铜礼器的代表性作品。",
                significance = "青铜鼎是中国古代最重要的礼器之一，是权力和地位的象征。" +
                    "鼎上的铭文为研究西周历史、制度提供了珍贵的文字资料。" +
                    "正所谓＂一言九鼎＂，可见鼎在中国文化中的崇高地位。",
                hallId = "hall_b",
                mapPositionX = 0.4f,
                mapPositionY = 0.3f
            ),
            Exhibit(
                exhibitCode = "jade",
                name = "玉璧",
                era = "战国",
                brief = "战国玉璧，质地温润，雕刻精细",
                description = "玉璧直径21厘米，孔径5厘米，厚0.8厘米。" +
                    "采用优质和田玉制成，通体呈青白色。" +
                    "璧面饰有谷纹，排列整齐有序，工艺精湛。" +
                    "边缘刻有龙凤纹，线条流畅生动。",
                significance = "玉璧是中国古代重要的礼器，用于祭祀天地。" +
                    "《周礼》记载：＂以苍璧礼天＂。战国时期玉器工艺达到高峰，" +
                    "这件玉璧体现了当时制玉工艺的最高水平。",
                hallId = "hall_b",
                mapPositionX = 0.7f,
                mapPositionY = 0.6f
            ),
            Exhibit(
                exhibitCode = "painting",
                name = "山水画长卷",
                era = "北宋",
                brief = "北宋山水长卷，意境深远，笔墨精妙",
                description = "画卷纵25厘米，横320厘米，纸本水墨。" +
                    "描绘了崇山峻岭、飞瀑流泉、村舍茅亭的壮丽景色。" +
                    "采用散点透视法，将高远、深远、平远三远法融于一卷。" +
                    "笔墨苍润，气韵生动，体现了北宋山水画的最高成就。",
                significance = "宋代是中国山水画的黄金时代。" +
                    "这件长卷体现了＂外师造化，中得心源＂的艺术理念，" +
                    "对后世山水画发展产生了深远影响。" +
                    "它不仅是一幅艺术作品，更是中国古代文人精神世界的真实写照。",
                hallId = "hall_c",
                mapPositionX = 0.5f,
                mapPositionY = 0.5f
            ),
            Exhibit(
                exhibitCode = "ceramic",
                name = "唐三彩骆驼",
                era = "唐代",
                brief = "唐三彩骆驼，造型生动，色彩斑斓",
                description = "骆驼高68厘米，长52厘米，为三彩釉陶制品。" +
                    "骆驼昂首挺立，双峰之间驮有丝绸、货物，仿佛正行走在丝绸之路。" +
                    "全身施以黄、绿、白三彩釉，色彩明快绚丽。" +
                    "造型写实而富有艺术感染力，是唐三彩中的精品。",
                significance = "唐三彩是唐代最具代表性的陶器品种。" +
                    "骆驼是丝绸之路的重要交通工具，这件文物生动再现了" +
                    "唐代中外贸易的繁荣景象，是古代丝绸之路的重要实物见证。",
                hallId = "hall_c",
                mapPositionX = 0.3f,
                mapPositionY = 0.7f
            )
        )
        exhibitDao.insertAll(samples)
    }

    companion object {
        /**
         * Maps generic detection labels to exhibit codes for demo.
         * In production, the recognition model outputs exhibit-specific codes directly.
         */
        private val LABEL_TO_CODE = mapOf(
            "vase" to "vase",
            "bottle" to "vase_2",
            "scissors" to "bronze",
            "clock" to "jade",
            "tv" to "painting",
            "potted plant" to "ceramic"
        )
    }
}
