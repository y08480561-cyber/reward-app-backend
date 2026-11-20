const zlib = require('zlib');

// Precompute standard CRC-32 lookup table (compatible with all Node.js >=18/20/22 versions)
const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let i = 0; i < buffer.length; i++) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function escapeXml(value) {
  const str = String(value == null ? '' : value)
    // Strip invalid XML 1.0 control characters
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * Convert Gregorian (gy, gm, gd) to Jalali (jy, jm, jd)
 */
function gregorianToJalali(gy, gm, gd) {
  const gdm = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
  const gy2 = gm > 2 ? gy + 1 : gy;
  let days =
    355666 +
    365 * gy +
    Math.floor((gy2 + 3) / 4) -
    Math.floor((gy2 + 99) / 100) +
    Math.floor((gy2 + 399) / 400) +
    gd +
    gdm[gm - 1];

  let jy = -1595 + 33 * Math.floor(days / 12053);
  days %= 12053;
  jy += 4 * Math.floor(days / 1461);
  days %= 1461;
  if (days > 365) {
    jy += Math.floor((days - 1) / 365);
    days = (days - 1) % 365;
  }

  let jm;
  let jd;
  if (days < 186) {
    jm = 1 + Math.floor(days / 31);
    jd = 1 + (days % 31);
  } else {
    jm = 7 + Math.floor((days - 186) / 30);
    jd = 1 + ((days - 186) % 30);
  }

  return {
    year: jy,
    month: String(jm).padStart(2, '0'),
    day: String(jd).padStart(2, '0')
  };
}

/**
 * Returns today's Persian Jalali date formatted as YYYY-MM-DD in Asia/Tehran timezone.
 * Example: 1405-08-29
 */
function getIranJalaliDateString(date = new Date()) {
  const formatted = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tehran',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date);

  const [gy, gm, gd] = formatted.split('-').map(Number);
  const jalali = gregorianToJalali(gy, gm, gd);
  return `${jalali.year}-${jalali.month}-${jalali.day}`;
}

/**
 * Build a minimal standard PKZIP archive from an array of { name, content } entries.
 */
function createZipBuffer(entries) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  const now = new Date();
  const dosTime =
    ((now.getHours() & 0x1f) << 11) |
    ((now.getMinutes() & 0x3f) << 5) |
    ((now.getSeconds() >> 1) & 0x1f);
  const dosDate =
    (((Math.max(1980, now.getFullYear()) - 1980) & 0x7f) << 9) |
    (((now.getMonth() + 1) & 0x0f) << 5) |
    (now.getDate() & 0x1f);

  for (const entry of entries) {
    const nameBuf = Buffer.from(entry.name, 'utf8');
    const rawBuf = Buffer.isBuffer(entry.content)
      ? entry.content
      : Buffer.from(entry.content, 'utf8');
    const compressedBuf = zlib.deflateRawSync(rawBuf);
    const checksum = crc32(rawBuf);

    // Local file header (30 bytes + filename)
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0); // signature
    localHeader.writeUInt16LE(20, 4); // version needed (2.0)
    localHeader.writeUInt16LE(0x0800, 6); // UTF-8 filename flag
    localHeader.writeUInt16LE(8, 8); // compression method: deflate
    localHeader.writeUInt16LE(dosTime, 10);
    localHeader.writeUInt16LE(dosDate, 12);
    localHeader.writeUInt32LE(checksum, 14);
    localHeader.writeUInt32LE(compressedBuf.length, 18);
    localHeader.writeUInt32LE(rawBuf.length, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28); // extra field length

    localParts.push(localHeader, nameBuf, compressedBuf);

    // Central directory header (46 bytes + filename)
    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0); // signature
    centralHeader.writeUInt16LE(20, 4); // version made by
    centralHeader.writeUInt16LE(20, 6); // version needed
    centralHeader.writeUInt16LE(0x0800, 8); // UTF-8 flag
    centralHeader.writeUInt16LE(8, 10); // deflate
    centralHeader.writeUInt16LE(dosTime, 12);
    centralHeader.writeUInt16LE(dosDate, 14);
    centralHeader.writeUInt32LE(checksum, 16);
    centralHeader.writeUInt32LE(compressedBuf.length, 20);
    centralHeader.writeUInt32LE(rawBuf.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30); // extra len
    centralHeader.writeUInt16LE(0, 32); // comment len
    centralHeader.writeUInt16LE(0, 34); // disk start
    centralHeader.writeUInt16LE(0, 36); // internal attrs
    centralHeader.writeUInt32LE(0, 38); // external attrs
    centralHeader.writeUInt32LE(offset, 42); // relative offset

    centralParts.push(centralHeader, nameBuf);
    offset += localHeader.length + nameBuf.length + compressedBuf.length;
  }

  const centralDirBuf = Buffer.concat(centralParts);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); // End of central directory signature
  eocd.writeUInt16LE(0, 4); // disk number
  eocd.writeUInt16LE(0, 6); // start disk
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralDirBuf.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20); // comment length

  return Buffer.concat([...localParts, centralDirBuf, eocd]);
}

function buildInlineStringCell(cellRef, styleIdx, text) {
  const escaped = escapeXml(text);
  const needsPreserve = /^\s|\s$/.test(String(text || ''));
  const tTag = needsPreserve ? `<t xml:space="preserve">${escaped}</t>` : `<t>${escaped}</t>`;
  return `<c r="${cellRef}" t="inlineStr" s="${styleIdx}"><is>${tTag}</is></c>`;
}

/**
 * Builds a real RTL .xlsx workbook containing strictly two columns:
 *  - Column A: نام و نام خانوادگی
 *  - Column B: شماره تماس (stored explicitly as Text with '@' format)
 *
 * @param {Array<{ fullName: string, phoneNumber: string }>} rows
 * @returns {Buffer}
 */
function buildUsersExcelBuffer(rows) {
  const safeRows = Array.isArray(rows) ? rows : [];

  let maxNameLen = 20;
  let maxPhoneLen = 15;
  for (const row of safeRows) {
    const nameLen = String(row.fullName || '').length;
    const phoneLen = String(row.phoneNumber || '').length;
    if (nameLen > maxNameLen) maxNameLen = nameLen;
    if (phoneLen > maxPhoneLen) maxPhoneLen = phoneLen;
  }

  const colAWidth = Math.min(60, Math.max(28, maxNameLen + 6));
  const colBWidth = Math.min(30, Math.max(20, maxPhoneLen + 5));

  const rowXmlParts = [];
  // Row 1: Header
  rowXmlParts.push(
    `<row r="1" ht="28" customHeight="1">` +
      buildInlineStringCell('A1', 1, 'نام و نام خانوادگی') +
      buildInlineStringCell('B1', 1, 'شماره تماس') +
    `</row>`
  );

  // Data rows (2 .. N+1)
  for (let i = 0; i < safeRows.length; i++) {
    const excelRowNum = i + 2;
    const item = safeRows[i] || {};
    const fullName = String(item.fullName || '').trim() || 'بدون نام';
    const phoneNumber = String(item.phoneNumber == null ? '' : item.phoneNumber).trim();

    rowXmlParts.push(
      `<row r="${excelRowNum}" ht="22" customHeight="1">` +
        buildInlineStringCell(`A${excelRowNum}`, 2, fullName) +
        buildInlineStringCell(`B${excelRowNum}`, 3, phoneNumber) +
      `</row>`
    );
  }

  const lastRowNum = Math.max(1, safeRows.length + 1);

  const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;

  const rootRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

  const workbookRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

  const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="کاربران" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>`;

  // Styles:
  // s=0: Default
  // s=1: Header (Bold, White on Dark Navy #0E1E36, Text format '@', RTL)
  // s=2: Name cell (Text format '@', RTL right-aligned)
  // s=3: Phone cell (Text format '@' numFmtId=49, RTL right-aligned so leading 0 is strictly preserved as Text)
  const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font>
      <sz val="11"/>
      <color rgb="FF0F172A"/>
      <name val="Tahoma"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFFFFFFF"/>
      <name val="Tahoma"/>
    </font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FF0E1E36"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="49" fontId="1" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center" readingOrder="2"/>
    </xf>
    <xf numFmtId="49" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center" readingOrder="2"/>
    </xf>
    <xf numFmtId="49" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center" readingOrder="2"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>`;

  const sheet1Xml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:B${lastRowNum}"/>
  <sheetViews>
    <sheetView rightToLeft="1" tabSelected="1" workbookViewId="0"/>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="22"/>
  <cols>
    <col min="1" max="1" width="${colAWidth}" customWidth="1"/>
    <col min="2" max="2" width="${colBWidth}" customWidth="1"/>
  </cols>
  <sheetData>
    ${rowXmlParts.join('\n    ')}
  </sheetData>
</worksheet>`;

  return createZipBuffer([
    { name: '[Content_Types].xml', content: contentTypesXml },
    { name: '_rels/.rels', content: rootRelsXml },
    { name: 'xl/_rels/workbook.xml.rels', content: workbookRelsXml },
    { name: 'xl/workbook.xml', content: workbookXml },
    { name: 'xl/styles.xml', content: stylesXml },
    { name: 'xl/worksheets/sheet1.xml', content: sheet1Xml }
  ]);
}

module.exports = {
  buildUsersExcelBuffer,
  getIranJalaliDateString,
  gregorianToJalali
};
