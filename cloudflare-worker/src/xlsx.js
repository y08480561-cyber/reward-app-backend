import { deflateRawSync } from 'node:zlib';

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

function crc32(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function escapeXml(value) {
  const str = String(value == null ? '' : value)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

export function gregorianToJalali(gy, gm, gd) {
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

export function getIranJalaliDateString(date = new Date()) {
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

export function getIranJalaliDateAndTime(timestamp) {
  if (timestamp === null || timestamp === undefined || timestamp === '') {
    return { jalaliDate: '', iranTime: '' };
  }
  const date = timestamp instanceof Date
    ? timestamp
    : new Date(typeof timestamp === 'number' ? timestamp : String(timestamp));
  if (Number.isNaN(date.getTime())) {
    return { jalaliDate: '', iranTime: '' };
  }

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Tehran',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(date);

  const map = {};
  for (const p of parts) {
    if (p.type !== 'literal') map[p.type] = p.value;
  }

  const jalali = gregorianToJalali(Number(map.year), Number(map.month), Number(map.day));
  const hour = map.hour === '24' ? '00' : String(map.hour || '00').padStart(2, '0');
  const minute = String(map.minute || '00').padStart(2, '0');
  const second = String(map.second || '00').padStart(2, '0');

  return {
    jalaliDate: `${jalali.year}/${jalali.month}/${jalali.day}`,
    iranTime: `${hour}:${minute}:${second}`
  };
}

function concatUint8Arrays(arrays) {
  let total = 0;
  for (const a of arrays) total += a.length;
  const result = new Uint8Array(total);
  let pos = 0;
  for (const a of arrays) {
    result.set(a, pos);
    pos += a.length;
  }
  return result;
}

function createZipUint8Array(entries) {
  const enc = new TextEncoder();
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
    const nameBytes = enc.encode(entry.name);
    const rawBytes = entry.content instanceof Uint8Array ? entry.content : enc.encode(entry.content);
    const compressedBytes = new Uint8Array(deflateRawSync(rawBytes));
    const checksum = crc32(rawBytes);

    const localHeader = new Uint8Array(30);
    const ldv = new DataView(localHeader.buffer);
    ldv.setUint32(0, 0x04034b50, true);
    ldv.setUint16(4, 20, true);
    ldv.setUint16(6, 0x0800, true);
    ldv.setUint16(8, 8, true);
    ldv.setUint16(10, dosTime, true);
    ldv.setUint16(12, dosDate, true);
    ldv.setUint32(14, checksum, true);
    ldv.setUint32(18, compressedBytes.length, true);
    ldv.setUint32(22, rawBytes.length, true);
    ldv.setUint16(26, nameBytes.length, true);
    ldv.setUint16(28, 0, true);

    localParts.push(localHeader, nameBytes, compressedBytes);

    const centralHeader = new Uint8Array(46);
    const cdv = new DataView(centralHeader.buffer);
    cdv.setUint32(0, 0x02014b50, true);
    cdv.setUint16(4, 20, true);
    cdv.setUint16(6, 20, true);
    cdv.setUint16(8, 0x0800, true);
    cdv.setUint16(10, 8, true);
    cdv.setUint16(12, dosTime, true);
    cdv.setUint16(14, dosDate, true);
    cdv.setUint32(16, checksum, true);
    cdv.setUint32(20, compressedBytes.length, true);
    cdv.setUint32(24, rawBytes.length, true);
    cdv.setUint16(28, nameBytes.length, true);
    cdv.setUint16(30, 0, true);
    cdv.setUint16(32, 0, true);
    cdv.setUint16(34, 0, true);
    cdv.setUint16(36, 0, true);
    cdv.setUint32(38, 0, true);
    cdv.setUint32(42, offset, true);

    centralParts.push(centralHeader, nameBytes);
    offset += localHeader.length + nameBytes.length + compressedBytes.length;
  }

  const centralDirBytes = concatUint8Arrays(centralParts);
  const eocd = new Uint8Array(22);
  const edv = new DataView(eocd.buffer);
  edv.setUint32(0, 0x06054b50, true);
  edv.setUint16(4, 0, true);
  edv.setUint16(6, 0, true);
  edv.setUint16(8, entries.length, true);
  edv.setUint16(10, entries.length, true);
  edv.setUint32(12, centralDirBytes.length, true);
  edv.setUint32(16, offset, true);
  edv.setUint16(20, 0, true);

  return concatUint8Arrays([...localParts, centralDirBytes, eocd]);
}

function buildInlineStringCell(cellRef, styleIdx, text) {
  const escaped = escapeXml(text);
  const needsPreserve = /^\s|\s$/.test(String(text || ''));
  const tTag = needsPreserve ? `<t xml:space="preserve">${escaped}</t>` : `<t>${escaped}</t>`;
  return `<c r="${cellRef}" t="inlineStr" s="${styleIdx}"><is>${tTag}</is></c>`;
}

const SHARED_STYLES_XML = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><color rgb="FF0F172A"/><name val="Tahoma"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Tahoma"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0E1E36"/><bgColor indexed="64"/></patternFill></fill>
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
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="49" fontId="1" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
    <xf numFmtId="49" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
    <xf numFmtId="49" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>`;

const CONTENT_TYPES_XML = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;

const ROOT_RELS_XML = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

const WORKBOOK_RELS_XML = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

export function buildUsersExcelBytes(rows) {
  const safeRows = Array.isArray(rows) ? rows : [];
  let maxNameLen = 20;
  let maxPhoneLen = 15;
  for (const row of safeRows) {
    maxNameLen = Math.max(maxNameLen, String(row.fullName || '').length);
    maxPhoneLen = Math.max(maxPhoneLen, String(row.phoneNumber || '').length);
  }
  const colAWidth = Math.min(60, Math.max(28, maxNameLen + 6));
  const colBWidth = Math.min(30, Math.max(20, maxPhoneLen + 5));

  const rowXmlParts = [
    `<row r="1" ht="28" customHeight="1">` +
      buildInlineStringCell('A1', 1, 'نام و نام خانوادگی') +
      buildInlineStringCell('B1', 1, 'شماره تماس') +
    `</row>`
  ];

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
  const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="کاربران" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;
  const sheet1Xml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:B${lastRowNum}"/>
  <sheetViews><sheetView rightToLeft="1" tabSelected="1" workbookViewId="0"/></sheetViews>
  <sheetFormatPr defaultRowHeight="22"/>
  <cols>
    <col min="1" max="1" width="${colAWidth}" customWidth="1"/>
    <col min="2" max="2" width="${colBWidth}" customWidth="1"/>
  </cols>
  <sheetData>${rowXmlParts.join('\n')}</sheetData>
</worksheet>`;

  return createZipUint8Array([
    { name: '[Content_Types].xml', content: CONTENT_TYPES_XML },
    { name: '_rels/.rels', content: ROOT_RELS_XML },
    { name: 'xl/_rels/workbook.xml.rels', content: WORKBOOK_RELS_XML },
    { name: 'xl/workbook.xml', content: workbookXml },
    { name: 'xl/styles.xml', content: SHARED_STYLES_XML },
    { name: 'xl/worksheets/sheet1.xml', content: sheet1Xml }
  ]);
}

export function buildSelectedUsersExcelBytes(rows) {
  const safeRows = Array.isArray(rows) ? rows : [];
  let maxNameLen = 22;
  let maxPhoneLen = 16;
  for (const row of safeRows) {
    maxNameLen = Math.max(maxNameLen, String(row.fullName || '').length);
    maxPhoneLen = Math.max(maxPhoneLen, String(row.phoneNumber || '').length);
  }
  const colAWidth = Math.min(60, Math.max(28, maxNameLen + 6));
  const colBWidth = Math.min(30, Math.max(20, maxPhoneLen + 5));

  const rowXmlParts = [
    `<row r="1" ht="28" customHeight="1">` +
      buildInlineStringCell('A1', 1, 'نام و نام خانوادگی') +
      buildInlineStringCell('B1', 1, 'شماره تماس') +
      buildInlineStringCell('C1', 1, 'تاریخ ثبت‌نام') +
      buildInlineStringCell('D1', 1, 'ساعت ثبت‌نام') +
    `</row>`
  ];

  for (let i = 0; i < safeRows.length; i++) {
    const excelRowNum = i + 2;
    const item = safeRows[i] || {};
    const fullName = String(item.fullName || '').trim() || 'بدون نام';
    const phoneNumber = String(item.phoneNumber == null ? '' : item.phoneNumber).trim();
    const { jalaliDate, iranTime } = getIranJalaliDateAndTime(item.registeredAt);

    rowXmlParts.push(
      `<row r="${excelRowNum}" ht="22" customHeight="1">` +
        buildInlineStringCell(`A${excelRowNum}`, 2, fullName) +
        buildInlineStringCell(`B${excelRowNum}`, 3, phoneNumber) +
        buildInlineStringCell(`C${excelRowNum}`, 3, jalaliDate || '-') +
        buildInlineStringCell(`D${excelRowNum}`, 3, iranTime || '-') +
      `</row>`
    );
  }

  const lastRowNum = Math.max(1, safeRows.length + 1);
  const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="کاربران انتخاب‌شده" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;
  const sheet1Xml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:D${lastRowNum}"/>
  <sheetViews><sheetView rightToLeft="1" tabSelected="1" workbookViewId="0"/></sheetViews>
  <sheetFormatPr defaultRowHeight="22"/>
  <cols>
    <col min="1" max="1" width="${colAWidth}" customWidth="1"/>
    <col min="2" max="2" width="${colBWidth}" customWidth="1"/>
    <col min="3" max="3" width="20" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
  </cols>
  <sheetData>${rowXmlParts.join('\n')}</sheetData>
</worksheet>`;

  return createZipUint8Array([
    { name: '[Content_Types].xml', content: CONTENT_TYPES_XML },
    { name: '_rels/.rels', content: ROOT_RELS_XML },
    { name: 'xl/_rels/workbook.xml.rels', content: WORKBOOK_RELS_XML },
    { name: 'xl/workbook.xml', content: workbookXml },
    { name: 'xl/styles.xml', content: SHARED_STYLES_XML },
    { name: 'xl/worksheets/sheet1.xml', content: sheet1Xml }
  ]);
}
