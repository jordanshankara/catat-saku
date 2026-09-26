package app.catatuang.export

import app.catatuang.engine.Cell
import app.catatuang.engine.Sheet
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Penulis XLSX minimal (bab 3/10): SpreadsheetML lewat ZipOutputStream, tanpa library. Teks memakai
 * inline string; nominal ditulis sebagai angka dengan format ribuan.
 */
object Xlsx {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun column(index: Int): String {
        var i = index + 1
        val sb = StringBuilder()
        while (i > 0) { val r = (i - 1) % 26; sb.append('A' + r); i = (i - 1) / 26 }
        return sb.reverse().toString()
    }

    private fun sheetXml(sheet: Sheet): String {
        val widths = (0 until (sheet.rows.maxOfOrNull { it.size } ?: 0)).joinToString("") { c ->
            val len = sheet.rows.maxOf { r -> when (val v = r.getOrNull(c)) { is Cell.Text -> v.value.length; is Cell.Num -> 12; null -> 0 } }
            """<col min="${c + 1}" max="${c + 1}" width="${(len + 2).coerceIn(8, 60)}" customWidth="1"/>"""
        }
        val rows = sheet.rows.mapIndexed { r, row ->
            val cells = row.mapIndexed { c, cell ->
                val ref = "${column(c)}${r + 1}"
                val bold = if (r == 0) " s=\"2\"" else ""
                when (cell) {
                    is Cell.Num -> """<c r="$ref" s="1"><v>${cell.value}</v></c>"""
                    is Cell.Text -> """<c r="$ref" t="inlineStr"$bold><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>"""
                }
            }.joinToString("")
            """<row r="${r + 1}">$cells</row>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>$widths</cols><sheetData>$rows</sheetData></worksheet>"""
    }

    fun write(sheets: List<Sheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${
                sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" }
            }</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${
                sheets.mapIndexed { i, s -> """<sheet name="${esc(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("")
            }</sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${
                sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" }
            }<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return out.toByteArray()
    }
}
