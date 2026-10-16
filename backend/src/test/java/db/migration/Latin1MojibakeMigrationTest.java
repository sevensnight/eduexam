package db.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Latin1MojibakeMigrationTest {

    @Test
    void repairsChineseTextImportedThroughMysqlLatin1() {
        String mojibake = "ç³»ç»Ÿç®¡ç\u0090†å‘˜";
        assertEquals("系统管理员", V7__repair_latin1_mojibake.repairText(mojibake));
        assertEquals("系统管理员", V7__repair_latin1_mojibake.repairText(
                V7__repair_latin1_mojibake.repairText(mojibake)));
    }

    @Test
    void repairsNonCjkUtf8Mojibake() {
        assertEquals("O(n²)", V7__repair_latin1_mojibake.repairText("O(nÂ²)"));
    }

    @Test
    void repairsMojibakeFragmentInsideCorrectChineseText() {
        String mixed = "考试《æ•°æ\u008d®åº“ç³»ç»ŸæœŸä¸­æµ‹éªŒ》已关闭。";
        assertEquals("考试《数据库系统期中测验》已关闭。",
                V7__repair_latin1_mojibake.repairText(mixed));
    }

    @Test
    void preservesCorrectUnicodeAsciiAndWesternEuropeanText() {
        assertEquals("数据结构", V7__repair_latin1_mojibake.repairText("数据结构"));
        assertEquals("[\"A. SELECT\", \"B. JOIN\"]",
                V7__repair_latin1_mojibake.repairText("[\"A. SELECT\", \"B. JOIN\"]"));
        assertEquals("Café", V7__repair_latin1_mojibake.repairText("Café"));
        assertEquals("考试《Café》已关闭。",
                V7__repair_latin1_mojibake.repairText("考试《Café》已关闭。"));
        assertEquals("学习进度🙂", V7__repair_latin1_mojibake.repairText("学习进度🙂"));
    }
}
