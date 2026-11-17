const TABLA_CRC32 = (() => {
  const tabla = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    tabla[i] = c >>> 0;
  }
  return tabla;
})();

function crc32(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = TABLA_CRC32[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

const ENTIDADES_XML = {
  '&': '\x26amp;',
  '<': '\x26lt;',
  '>': '\x26gt;',
  '"': '\x26quot;',
  "'": '\x26apos;',
};

function escaparXml(valor) {
  return String(valor ?? '')
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .replace(/[&<>"']/g, (caracter) => ENTIDADES_XML[caracter]);
}

function columnaALetra(indice) {
  let n = indice + 1;
  let letras = '';
  while (n > 0) {
    const resto = (n - 1) % 26;
    letras = String.fromCharCode(65 + resto) + letras;
    n = Math.floor((n - 1) / 26);
  }
  return letras;
}

function crearZip(archivos) {
  const encoder = new TextEncoder();
  const partesLocales = [];
  const partesCentrales = [];
  let offset = 0;

  for (const archivo of archivos) {
    const nombreBytes = encoder.encode(archivo.nombre);
    const datosBytes = encoder.encode(archivo.contenido);
    const suma = crc32(datosBytes);

    const cabeceraLocal = new Uint8Array(30 + nombreBytes.length);
    const viewLocal = new DataView(cabeceraLocal.buffer);
    viewLocal.setUint32(0, 0x04034b50, true);
    viewLocal.setUint16(4, 20, true);
    viewLocal.setUint16(6, 0x0800, true);
    viewLocal.setUint16(8, 0, true);
    viewLocal.setUint16(10, 0, true);
    viewLocal.setUint16(12, 0, true);
    viewLocal.setUint32(14, suma, true);
    viewLocal.setUint32(18, datosBytes.length, true);
    viewLocal.setUint32(22, datosBytes.length, true);
    viewLocal.setUint16(26, nombreBytes.length, true);
    viewLocal.setUint16(28, 0, true);
    cabeceraLocal.set(nombreBytes, 30);

    partesLocales.push(cabeceraLocal, datosBytes);

    const cabeceraCentral = new Uint8Array(46 + nombreBytes.length);
    const viewCentral = new DataView(cabeceraCentral.buffer);
    viewCentral.setUint32(0, 0x02014b50, true);
    viewCentral.setUint16(4, 20, true);
    viewCentral.setUint16(6, 20, true);
    viewCentral.setUint16(8, 0x0800, true);
    viewCentral.setUint16(10, 0, true);
    viewCentral.setUint16(12, 0, true);
    viewCentral.setUint16(14, 0, true);
    viewCentral.setUint32(16, suma, true);
    viewCentral.setUint32(20, datosBytes.length, true);
    viewCentral.setUint32(24, datosBytes.length, true);
    viewCentral.setUint16(28, nombreBytes.length, true);
    viewCentral.setUint16(30, 0, true);
    viewCentral.setUint16(32, 0, true);
    viewCentral.setUint16(34, 0, true);
    viewCentral.setUint16(36, 0, true);
    viewCentral.setUint32(38, 0, true);
    viewCentral.setUint32(42, offset, true);
    cabeceraCentral.set(nombreBytes, 46);

    partesCentrales.push(cabeceraCentral);
    offset += cabeceraLocal.length + datosBytes.length;
  }

  const tamanoCentral = partesCentrales.reduce((acc, p) => acc + p.length, 0);
  const finCentral = new Uint8Array(22);
  const viewFin = new DataView(finCentral.buffer);
  viewFin.setUint32(0, 0x06054b50, true);
  viewFin.setUint16(4, 0, true);
  viewFin.setUint16(6, 0, true);
  viewFin.setUint16(8, archivos.length, true);
  viewFin.setUint16(10, archivos.length, true);
  viewFin.setUint32(12, tamanoCentral, true);
  viewFin.setUint32(16, offset, true);
  viewFin.setUint16(20, 0, true);

  const resultado = new Uint8Array(offset + tamanoCentral + finCentral.length);
  let pos = 0;
  for (const parte of [...partesLocales, ...partesCentrales, finCentral]) {
    resultado.set(parte, pos);
    pos += parte.length;
  }
  return resultado;
}

export function generarBytesXlsx({ nombreHoja = 'Candidatos', encabezados, filas }) {
  const anchos = encabezados.map((enc, colIdx) => {
    let max = String(enc).length;
    for (const fila of filas) {
      const len = String(fila[colIdx] ?? '').length;
      if (len > max) max = len;
    }
    return Math.min(Math.max(max + 4, 13), 42);
  });

  const colsXml = anchos
    .map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`)
    .join('');

  const filaEncabezado = `<row r="1" ht="24" customHeight="1">${encabezados
    .map((enc, c) => `<c r="${columnaALetra(c)}1" t="inlineStr" s="1"><is><t>${escaparXml(enc)}</t></is></c>`)
    .join('')}</row>`;

  const filasXml = filas
    .map((fila, rIdx) => {
      const numFila = rIdx + 2;
      const estilo = rIdx % 2 === 1 ? '3' : '2';
      const celdas = encabezados
        .map((_, cIdx) => {
          const val = fila[cIdx];
          const ref = `${columnaALetra(cIdx)}${numFila}`;
          if (typeof val === 'number' && Number.isFinite(val)) {
            return `<c r="${ref}" s="${estilo}"><v>${val}</v></c>`;
          }
          return `<c r="${ref}" t="inlineStr" s="${estilo}"><is><t>${escaparXml(val ?? '')}</t></is></c>`;
        })
        .join('');
      return `<row r="${numFila}" ht="20" customHeight="1">${celdas}</row>`;
    })
    .join('');

  const ultimaCol = columnaALetra(Math.max(encabezados.length - 1, 0));
  const ultimaFila = filas.length + 1;

  const sheetXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>${colsXml}</cols>
  <sheetData>${filaEncabezado}${filasXml}</sheetData>
  <autoFilter ref="A1:${ultimaCol}${ultimaFila}"/>
</worksheet>`;

  const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0E5A70"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF4F8FA"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD9E2EC"/></left>
      <right style="thin"><color rgb="FFD9E2EC"/></right>
      <top style="thin"><color rgb="FFD9E2EC"/></top>
      <bottom style="thin"><color rgb="FFD9E2EC"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>`;

  const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="${escaparXml(nombreHoja)}" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;

  const wbRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

  const relsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

  const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;

  return crearZip([
    { nombre: '[Content_Types].xml', contenido: contentTypesXml },
    { nombre: '_rels/.rels', contenido: relsXml },
    { nombre: 'xl/workbook.xml', contenido: workbookXml },
    { nombre: 'xl/_rels/workbook.xml.rels', contenido: wbRelsXml },
    { nombre: 'xl/styles.xml', contenido: stylesXml },
    { nombre: 'xl/worksheets/sheet1.xml', contenido: sheetXml },
  ]);
}

export function descargarExcel({ nombreArchivo, nombreHoja, encabezados, filas }) {
  const bytes = generarBytesXlsx({ nombreHoja, encabezados, filas });
  const blob = new Blob([bytes], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo.endsWith('.xlsx') ? nombreArchivo : `${nombreArchivo}.xlsx`;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 3000);
}