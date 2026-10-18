# Tinta PDF

Versión personalizada de [ClearPDF](https://github.com/Chethan616/ClearPDF) (licencia MIT).
Todo funciona dentro del teléfono: ningún documento se sube a ningún servidor, salvo que tú
lo compartas.

## Qué añade sobre ClearPDF

- **PDF a Word (.docx) y Excel (.xlsx)**, desde Inicio → «PDF a Word / Excel» o
  Herramientas → Convertir.
  - Word: conserva títulos, párrafos, tablas reales y un salto de página por cada página del PDF.
  - Excel: organiza el texto en filas y columnas según su posición, con una hoja por página.
    Los números como `1.234,56`, `1,234.56`, `12%` o `(300)` se guardan como números de verdad,
    así que se pueden sumar enseguida.
  - Las páginas escaneadas pasan por OCR automáticamente, incluso en PDF que mezclan páginas
    con texto y páginas escaneadas.
- **Escaneo con OCR en español**: cada documento escaneado se guarda con una capa de texto
  invisible. El PDF se ve igual, pero se puede buscar y copiar, y lo leen sin problema
  ChatGPT, Claude, Gemini, NotebookLM y similares. El motor libre (Tesseract) incluye ahora el
  español (`spa`) además del inglés, así que reconoce á, é, ñ, ¿ y ¡.
- **Subir a Google Drive**: después de escanear o convertir, el botón «Drive» abre la pantalla
  de subida de la app de Google Drive con tu cuenta. Desde Drive eliges la carpeta y luego lo
  compartes con quien quieras. No hace falta configurar claves de Google en la app.
- **Marca propia**: nombre, ícono y colores (tinta verde azulado y ámbar), y una interfaz más
  tranquila por defecto (fondo plano y reflejos del efecto cristal sin movimiento). Las dos
  opciones se pueden cambiar en Ajustes.
- Se instala **junto a** ClearPDF (`com.tintapdf.app`), sin reemplazarlo.

## Cómo conseguir el APK

Cada vez que se suben cambios a `main`, GitHub compila la app sola:

1. En el repositorio, abre la pestaña **Actions** → la ejecución más reciente de
   **Android build** (con la marca verde).
2. Abajo, en **Artifacts**, descarga **tinta-pdf-apk** (es un .zip).
3. Dentro, en `foss/release/`, está `app-foss-arm64-v8a-release.apk`, que sirve para casi
   todos los teléfonos (si no instala, usa `app-foss-universal-release.apk`). Ábrelo en el
   teléfono para instalarlo.

Para compilar sin subir cambios: Actions → Android build → **Run workflow**.

Todas las compilaciones se firman con la misma clave (`keystore/tinta-debug.jks`), así que cada
APK nuevo se instala como actualización del anterior. Esa clave está en el repositorio: sirve
para uso personal. Si algún día publicas la app, crea una clave privada y configúrala como
*secrets* `CLEARPDF_UPLOAD_*` (ver `key.properties.example`).

## Dónde está el código nuevo

| Qué | Archivo |
| --- | --- |
| Análisis de diseño (líneas, párrafos, tablas) | `app/.../export/LayoutAnalyzer.java` |
| Escritor de Word / Excel | `app/.../export/DocxExporter.java`, `XlsxExporter.java` |
| Lectura del PDF + OCR por página | `app/.../export/PdfPageReader.kt` |
| Botones de conversión | `app/.../ui/screen/ExtractTextScreen.kt` |
| OCR al escanear | `app/.../ui/screen/ScanDocumentScreen.kt` |
| Google Drive / compartir | `app/.../util/ShareToCloud.kt` |
| Colores de la marca | `app/.../ui/theme/LiquidGlassTokens.kt` (`BrandColors`) |
| OCR en español (Tesseract) | `ocr-core/.../TesseractOcrEngine.kt`, `assets/tessdata/spa.traineddata` |

La parte de conversión a Word y Excel está escrita en Java puro, sin dependencias de Android,
para poder probarla en cualquier computadora.
