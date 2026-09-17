package app.amisles.hanime.feature.search

import androidx.annotation.StringRes
import app.amisles.hanime.core.ui.R

/**
 * 官网搜索页「内容标签」全集。
 *
 * [SearchTag.value] 是**官网原值**，用于拼 URL 参数 `tags[]=`，**永不翻译**；
 * [SearchTag.labelRes] 才是界面展示名，随语言切换（资源键 = `search_tag_<英文名 slug>`）。
 * 类目名同理走 [SearchTagGroup.categoryRes]。
 *
 * 资源键 = `search_tag_<英文名 slug>`，4 套语言（values / values-en / values-ja / values-zh-rTW）
 * 必须同步维护：新增标签时请一次补齐这 4 个文件，不要只改单一语言。
 */
data class SearchTag(
    val value: String,
    @StringRes val labelRes: Int
)

data class SearchTagGroup(
    @StringRes val categoryRes: Int,
    val tags: List<SearchTag>
)

object SearchTagCatalog {
    val groups: List<SearchTagGroup> = listOf(
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_video_attributes,
            tags = listOf(
                SearchTag(value = "無碼", labelRes = R.string.search_tag_uncensored),
                SearchTag(value = "AI解碼", labelRes = R.string.search_tag_ai_decensored),
                SearchTag(value = "中文字幕", labelRes = R.string.search_tag_chinese_subtitles),
                SearchTag(value = "中文配音", labelRes = R.string.search_tag_chinese_dub),
                SearchTag(value = "同人作品", labelRes = R.string.search_tag_doujin),
                SearchTag(value = "斷面圖", labelRes = R.string.search_tag_cross_section),
                SearchTag(value = "ASMR", labelRes = R.string.search_tag_asmr),
                SearchTag(value = "1080p", labelRes = R.string.search_tag_1080p),
                SearchTag(value = "60FPS", labelRes = R.string.search_tag_60fps)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_relationships,
            tags = listOf(
                SearchTag(value = "近親", labelRes = R.string.search_tag_incest),
                SearchTag(value = "姐", labelRes = R.string.search_tag_older_sister),
                SearchTag(value = "妹", labelRes = R.string.search_tag_younger_sister),
                SearchTag(value = "母", labelRes = R.string.search_tag_mother),
                SearchTag(value = "女兒", labelRes = R.string.search_tag_daughter),
                SearchTag(value = "師生", labelRes = R.string.search_tag_teacher_and_student),
                SearchTag(value = "情侶", labelRes = R.string.search_tag_couple),
                SearchTag(value = "青梅竹馬", labelRes = R.string.search_tag_childhood_friend),
                SearchTag(value = "同事", labelRes = R.string.search_tag_coworker)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_character_types,
            tags = listOf(
                SearchTag(value = "JK", labelRes = R.string.search_tag_schoolgirl_jk),
                SearchTag(value = "處女", labelRes = R.string.search_tag_virgin),
                SearchTag(value = "御姐", labelRes = R.string.search_tag_onee_san),
                SearchTag(value = "熟女", labelRes = R.string.search_tag_milf),
                SearchTag(value = "人妻", labelRes = R.string.search_tag_married_woman),
                SearchTag(value = "女教師", labelRes = R.string.search_tag_female_teacher),
                SearchTag(value = "男教師", labelRes = R.string.search_tag_male_teacher),
                SearchTag(value = "女醫生", labelRes = R.string.search_tag_female_doctor),
                SearchTag(value = "女病人", labelRes = R.string.search_tag_female_patient),
                SearchTag(value = "護士", labelRes = R.string.search_tag_nurse),
                SearchTag(value = "OL", labelRes = R.string.search_tag_office_lady),
                SearchTag(value = "女警", labelRes = R.string.search_tag_policewoman),
                SearchTag(value = "大小姐", labelRes = R.string.search_tag_rich_young_lady),
                SearchTag(value = "偶像", labelRes = R.string.search_tag_idol),
                SearchTag(value = "女僕", labelRes = R.string.search_tag_maid),
                SearchTag(value = "巫女", labelRes = R.string.search_tag_shrine_maiden),
                SearchTag(value = "魔女", labelRes = R.string.search_tag_witch),
                SearchTag(value = "修女", labelRes = R.string.search_tag_nun),
                SearchTag(value = "風俗娘", labelRes = R.string.search_tag_sex_worker),
                SearchTag(value = "公主", labelRes = R.string.search_tag_princess),
                SearchTag(value = "女忍者", labelRes = R.string.search_tag_kunoichi),
                SearchTag(value = "女戰士", labelRes = R.string.search_tag_female_warrior),
                SearchTag(value = "女騎士", labelRes = R.string.search_tag_female_knight),
                SearchTag(value = "魔法少女", labelRes = R.string.search_tag_magical_girl),
                SearchTag(value = "異種族", labelRes = R.string.search_tag_non_human),
                SearchTag(value = "天使", labelRes = R.string.search_tag_angel),
                SearchTag(value = "妖精", labelRes = R.string.search_tag_fairy),
                SearchTag(value = "魔物娘", labelRes = R.string.search_tag_monster_girl),
                SearchTag(value = "魅魔", labelRes = R.string.search_tag_succubus),
                SearchTag(value = "吸血鬼", labelRes = R.string.search_tag_vampire),
                SearchTag(value = "女鬼", labelRes = R.string.search_tag_ghost_girl),
                SearchTag(value = "獸娘", labelRes = R.string.search_tag_animal_eared_girl),
                SearchTag(value = "福瑞", labelRes = R.string.search_tag_furry),
                SearchTag(value = "乳牛", labelRes = R.string.search_tag_cow_girl),
                SearchTag(value = "機械娘", labelRes = R.string.search_tag_robot_girl),
                SearchTag(value = "碧池", labelRes = R.string.search_tag_slut),
                SearchTag(value = "痴女", labelRes = R.string.search_tag_nymphomaniac),
                SearchTag(value = "雌小鬼", labelRes = R.string.search_tag_bratty_girl),
                SearchTag(value = "不良少女", labelRes = R.string.search_tag_delinquent_girl),
                SearchTag(value = "傲嬌", labelRes = R.string.search_tag_tsundere),
                SearchTag(value = "病嬌", labelRes = R.string.search_tag_yandere),
                SearchTag(value = "無口", labelRes = R.string.search_tag_taciturn),
                SearchTag(value = "無表情", labelRes = R.string.search_tag_expressionless),
                SearchTag(value = "眼神死", labelRes = R.string.search_tag_dead_eyes),
                SearchTag(value = "正太", labelRes = R.string.search_tag_shota),
                SearchTag(value = "偽娘", labelRes = R.string.search_tag_crossdresser),
                SearchTag(value = "扶他", labelRes = R.string.search_tag_futanari)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_appearance,
            tags = listOf(
                SearchTag(value = "短髮", labelRes = R.string.search_tag_short_hair),
                SearchTag(value = "馬尾", labelRes = R.string.search_tag_ponytail),
                SearchTag(value = "雙馬尾", labelRes = R.string.search_tag_twin_tails),
                SearchTag(value = "丸子頭", labelRes = R.string.search_tag_bun_hair),
                SearchTag(value = "巨乳", labelRes = R.string.search_tag_big_breasts),
                SearchTag(value = "乳環", labelRes = R.string.search_tag_nipple_piercing),
                SearchTag(value = "舌環", labelRes = R.string.search_tag_tongue_piercing),
                SearchTag(value = "貧乳", labelRes = R.string.search_tag_flat_chest),
                SearchTag(value = "黑皮膚", labelRes = R.string.search_tag_dark_skin),
                SearchTag(value = "曬痕", labelRes = R.string.search_tag_tan_lines),
                SearchTag(value = "眼鏡娘", labelRes = R.string.search_tag_girl_with_glasses),
                SearchTag(value = "獸耳", labelRes = R.string.search_tag_animal_ears),
                SearchTag(value = "尖耳朵", labelRes = R.string.search_tag_pointed_ears),
                SearchTag(value = "異色瞳", labelRes = R.string.search_tag_heterochromia),
                SearchTag(value = "美人痣", labelRes = R.string.search_tag_beauty_mark),
                SearchTag(value = "肌肉女", labelRes = R.string.search_tag_muscular_woman),
                SearchTag(value = "白虎", labelRes = R.string.search_tag_hairless),
                SearchTag(value = "陰毛", labelRes = R.string.search_tag_pubic_hair),
                SearchTag(value = "腋毛", labelRes = R.string.search_tag_armpit_hair),
                SearchTag(value = "大屌", labelRes = R.string.search_tag_big_dick),
                SearchTag(value = "黑屌", labelRes = R.string.search_tag_black_dick),
                SearchTag(value = "著衣", labelRes = R.string.search_tag_clothed_sex),
                SearchTag(value = "水手服", labelRes = R.string.search_tag_sailor_uniform),
                SearchTag(value = "體操服", labelRes = R.string.search_tag_gym_uniform),
                SearchTag(value = "泳裝", labelRes = R.string.search_tag_swimsuit),
                SearchTag(value = "比基尼", labelRes = R.string.search_tag_bikini),
                SearchTag(value = "死庫水", labelRes = R.string.search_tag_school_swimsuit),
                SearchTag(value = "和服", labelRes = R.string.search_tag_kimono),
                SearchTag(value = "兔女郎", labelRes = R.string.search_tag_bunny_girl),
                SearchTag(value = "圍裙", labelRes = R.string.search_tag_apron),
                SearchTag(value = "啦啦隊", labelRes = R.string.search_tag_cheerleader),
                SearchTag(value = "絲襪", labelRes = R.string.search_tag_pantyhose),
                SearchTag(value = "吊襪帶", labelRes = R.string.search_tag_garter_belt),
                SearchTag(value = "熱褲", labelRes = R.string.search_tag_hot_pants),
                SearchTag(value = "迷你裙", labelRes = R.string.search_tag_miniskirt),
                SearchTag(value = "性感內衣", labelRes = R.string.search_tag_lingerie),
                SearchTag(value = "緊身衣", labelRes = R.string.search_tag_bodysuit),
                SearchTag(value = "丁字褲", labelRes = R.string.search_tag_thong),
                SearchTag(value = "高跟鞋", labelRes = R.string.search_tag_high_heels),
                SearchTag(value = "睡衣", labelRes = R.string.search_tag_pajamas),
                SearchTag(value = "婚紗", labelRes = R.string.search_tag_wedding_dress),
                SearchTag(value = "旗袍", labelRes = R.string.search_tag_qipao),
                SearchTag(value = "古裝", labelRes = R.string.search_tag_period_costume),
                SearchTag(value = "哥德", labelRes = R.string.search_tag_gothic),
                SearchTag(value = "口罩", labelRes = R.string.search_tag_mask),
                SearchTag(value = "刺青", labelRes = R.string.search_tag_tattoo),
                SearchTag(value = "淫紋", labelRes = R.string.search_tag_lewd_tattoo),
                SearchTag(value = "身體寫字", labelRes = R.string.search_tag_body_writing)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_settings,
            tags = listOf(
                SearchTag(value = "校園", labelRes = R.string.search_tag_school),
                SearchTag(value = "教室", labelRes = R.string.search_tag_classroom),
                SearchTag(value = "圖書館", labelRes = R.string.search_tag_library),
                SearchTag(value = "保健室", labelRes = R.string.search_tag_nurse_office),
                SearchTag(value = "體育倉庫", labelRes = R.string.search_tag_gym_storage),
                SearchTag(value = "游泳池", labelRes = R.string.search_tag_pool),
                SearchTag(value = "愛情賓館", labelRes = R.string.search_tag_love_hotel),
                SearchTag(value = "醫院", labelRes = R.string.search_tag_hospital),
                SearchTag(value = "辦公室", labelRes = R.string.search_tag_office),
                SearchTag(value = "浴室", labelRes = R.string.search_tag_bathroom),
                SearchTag(value = "窗邊", labelRes = R.string.search_tag_by_the_window),
                SearchTag(value = "公共廁所", labelRes = R.string.search_tag_public_toilet),
                SearchTag(value = "公眾場合", labelRes = R.string.search_tag_in_public),
                SearchTag(value = "戶外野戰", labelRes = R.string.search_tag_outdoors),
                SearchTag(value = "電車", labelRes = R.string.search_tag_train),
                SearchTag(value = "車震", labelRes = R.string.search_tag_in_a_car),
                SearchTag(value = "遊艇", labelRes = R.string.search_tag_yacht),
                SearchTag(value = "露營帳篷", labelRes = R.string.search_tag_tent),
                SearchTag(value = "電影院", labelRes = R.string.search_tag_movie_theater),
                SearchTag(value = "健身房", labelRes = R.string.search_tag_gym),
                SearchTag(value = "沙灘", labelRes = R.string.search_tag_beach),
                SearchTag(value = "溫泉", labelRes = R.string.search_tag_hot_spring),
                SearchTag(value = "夜店", labelRes = R.string.search_tag_nightclub),
                SearchTag(value = "監獄", labelRes = R.string.search_tag_prison),
                SearchTag(value = "教堂", labelRes = R.string.search_tag_church)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_story_and_plot,
            tags = listOf(
                SearchTag(value = "純愛", labelRes = R.string.search_tag_pure_love),
                SearchTag(value = "戀愛喜劇", labelRes = R.string.search_tag_romantic_comedy),
                SearchTag(value = "後宮", labelRes = R.string.search_tag_harem),
                SearchTag(value = "十指緊扣", labelRes = R.string.search_tag_interlocked_fingers),
                SearchTag(value = "開大車", labelRes = R.string.search_tag_older_woman_and_younger_man),
                SearchTag(value = "NTR", labelRes = R.string.search_tag_ntr),
                SearchTag(value = "精神控制", labelRes = R.string.search_tag_mind_control),
                SearchTag(value = "藥物", labelRes = R.string.search_tag_drugs),
                SearchTag(value = "痴漢", labelRes = R.string.search_tag_molester),
                SearchTag(value = "阿嘿顏", labelRes = R.string.search_tag_ahegao),
                SearchTag(value = "哭泣", labelRes = R.string.search_tag_crying),
                SearchTag(value = "精神崩潰", labelRes = R.string.search_tag_mental_breakdown),
                SearchTag(value = "獵奇", labelRes = R.string.search_tag_gore),
                SearchTag(value = "BDSM", labelRes = R.string.search_tag_bdsm),
                SearchTag(value = "綑綁", labelRes = R.string.search_tag_bondage),
                SearchTag(value = "眼罩", labelRes = R.string.search_tag_blindfold),
                SearchTag(value = "項圈", labelRes = R.string.search_tag_collar),
                SearchTag(value = "調教", labelRes = R.string.search_tag_training),
                SearchTag(value = "異物插入", labelRes = R.string.search_tag_object_insertion),
                SearchTag(value = "尋歡洞", labelRes = R.string.search_tag_glory_hole),
                SearchTag(value = "肉便器", labelRes = R.string.search_tag_cum_dumpster),
                SearchTag(value = "性奴隸", labelRes = R.string.search_tag_sex_slave),
                SearchTag(value = "胃凸", labelRes = R.string.search_tag_stomach_bulge),
                SearchTag(value = "強制", labelRes = R.string.search_tag_non_consensual),
                SearchTag(value = "輪姦", labelRes = R.string.search_tag_gang_rape),
                SearchTag(value = "凌辱", labelRes = R.string.search_tag_humiliation),
                SearchTag(value = "性暴力", labelRes = R.string.search_tag_sexual_violence),
                SearchTag(value = "逆強制", labelRes = R.string.search_tag_reverse_non_consensual),
                SearchTag(value = "女王樣", labelRes = R.string.search_tag_dominatrix),
                SearchTag(value = "榨精", labelRes = R.string.search_tag_milking),
                SearchTag(value = "母女丼", labelRes = R.string.search_tag_mother_and_daughter),
                SearchTag(value = "姐妹丼", labelRes = R.string.search_tag_sisters),
                SearchTag(value = "出軌", labelRes = R.string.search_tag_cheating),
                SearchTag(value = "醉酒", labelRes = R.string.search_tag_drunk),
                SearchTag(value = "攝影", labelRes = R.string.search_tag_filming),
                SearchTag(value = "睡眠姦", labelRes = R.string.search_tag_sleep_sex),
                SearchTag(value = "機械姦", labelRes = R.string.search_tag_machine_sex),
                SearchTag(value = "蟲姦", labelRes = R.string.search_tag_insect_sex),
                SearchTag(value = "性轉換", labelRes = R.string.search_tag_gender_swap),
                SearchTag(value = "百合", labelRes = R.string.search_tag_yuri),
                SearchTag(value = "耽美", labelRes = R.string.search_tag_bl),
                SearchTag(value = "時間停止", labelRes = R.string.search_tag_time_stop),
                SearchTag(value = "異世界", labelRes = R.string.search_tag_isekai),
                SearchTag(value = "怪獸", labelRes = R.string.search_tag_monster),
                SearchTag(value = "哥布林", labelRes = R.string.search_tag_goblin),
                SearchTag(value = "世界末日", labelRes = R.string.search_tag_post_apocalyptic)
            )
        ),
        SearchTagGroup(
            categoryRes = R.string.search_tag_category_sex_acts,
            tags = listOf(
                SearchTag(value = "手交", labelRes = R.string.search_tag_handjob),
                SearchTag(value = "指交", labelRes = R.string.search_tag_fingering),
                SearchTag(value = "玩乳頭", labelRes = R.string.search_tag_nipple_play),
                SearchTag(value = "乳交", labelRes = R.string.search_tag_titfuck),
                SearchTag(value = "乳頭交", labelRes = R.string.search_tag_nipple_fuck),
                SearchTag(value = "肛交", labelRes = R.string.search_tag_anal_sex),
                SearchTag(value = "雙洞齊下", labelRes = R.string.search_tag_double_penetration),
                SearchTag(value = "腳交", labelRes = R.string.search_tag_footjob),
                SearchTag(value = "素股", labelRes = R.string.search_tag_intercrural_sex),
                SearchTag(value = "拳交", labelRes = R.string.search_tag_fisting),
                SearchTag(value = "3P", labelRes = R.string.search_tag_threesome),
                SearchTag(value = "群交", labelRes = R.string.search_tag_orgy),
                SearchTag(value = "口交", labelRes = R.string.search_tag_oral_sex),
                SearchTag(value = "跪舔", labelRes = R.string.search_tag_kneeling_lick),
                SearchTag(value = "深喉嚨", labelRes = R.string.search_tag_deep_throat),
                SearchTag(value = "口爆", labelRes = R.string.search_tag_mouth_ejaculation),
                SearchTag(value = "吞精", labelRes = R.string.search_tag_swallowing),
                SearchTag(value = "舔蛋蛋", labelRes = R.string.search_tag_ball_licking),
                SearchTag(value = "舔穴", labelRes = R.string.search_tag_pussy_licking),
                SearchTag(value = "69", labelRes = R.string.search_tag_69),
                SearchTag(value = "自慰", labelRes = R.string.search_tag_masturbation),
                SearchTag(value = "腋交", labelRes = R.string.search_tag_armpit_sex),
                SearchTag(value = "舔腋下", labelRes = R.string.search_tag_armpit_licking),
                SearchTag(value = "髮交", labelRes = R.string.search_tag_hair_sex),
                SearchTag(value = "舔耳朵", labelRes = R.string.search_tag_ear_licking),
                SearchTag(value = "舔腳", labelRes = R.string.search_tag_foot_licking),
                SearchTag(value = "內射", labelRes = R.string.search_tag_creampie),
                SearchTag(value = "外射", labelRes = R.string.search_tag_pull_out),
                SearchTag(value = "顏射", labelRes = R.string.search_tag_facial),
                SearchTag(value = "潮吹", labelRes = R.string.search_tag_squirting),
                SearchTag(value = "懷孕", labelRes = R.string.search_tag_pregnancy),
                SearchTag(value = "噴奶", labelRes = R.string.search_tag_lactation),
                SearchTag(value = "放尿", labelRes = R.string.search_tag_urination),
                SearchTag(value = "排便", labelRes = R.string.search_tag_defecation),
                SearchTag(value = "騎乘位", labelRes = R.string.search_tag_cowgirl),
                SearchTag(value = "背後位", labelRes = R.string.search_tag_doggystyle),
                SearchTag(value = "側面位", labelRes = R.string.search_tag_side_position),
                SearchTag(value = "顏面騎乘", labelRes = R.string.search_tag_facesitting),
                SearchTag(value = "火車便當", labelRes = R.string.search_tag_standing_carry),
                SearchTag(value = "一字馬", labelRes = R.string.search_tag_split),
                SearchTag(value = "性玩具", labelRes = R.string.search_tag_sex_toys),
                SearchTag(value = "飛機杯", labelRes = R.string.search_tag_onahole),
                SearchTag(value = "跳蛋", labelRes = R.string.search_tag_vibrator),
                SearchTag(value = "毒龍鑽", labelRes = R.string.search_tag_rimjob),
                SearchTag(value = "觸手", labelRes = R.string.search_tag_tentacle),
                SearchTag(value = "獸交", labelRes = R.string.search_tag_bestiality),
                SearchTag(value = "頸手枷", labelRes = R.string.search_tag_pillory),
                SearchTag(value = "扯頭髮", labelRes = R.string.search_tag_hair_pulling),
                SearchTag(value = "掐脖子", labelRes = R.string.search_tag_choking),
                SearchTag(value = "打屁股", labelRes = R.string.search_tag_spanking),
                SearchTag(value = "肉棒打臉", labelRes = R.string.search_tag_penis_face_slap),
                SearchTag(value = "陰道外翻", labelRes = R.string.search_tag_vaginal_prolapse),
                SearchTag(value = "男乳首責", labelRes = R.string.search_tag_male_nipple_play),
                SearchTag(value = "接吻", labelRes = R.string.search_tag_kissing),
                SearchTag(value = "舌吻", labelRes = R.string.search_tag_french_kiss),
                SearchTag(value = "POV", labelRes = R.string.search_tag_pov)
            )
        )
    )

    private val labelResMap: Map<String, Int> =
        groups.flatMap { g -> g.tags.map { it.value to it.labelRes } }.toMap()

    /** 标签官网原值 → 展示名资源 ID；未收录时返回 null。 */
    fun labelResOf(value: String): Int? = labelResMap[value]
}
