# Indexador Lucene Airbnb LA (Jun 2025)

## 1. Stack y empaquetado
- **Java:** JDK 17  
- **Lucene:** 10.3.1  
- **Build:** Compilación directa con javac (o Maven + Shade para uber-jar)  
- **SO destino:** Linux (portable a macOS/Windows)

---

## 2. Estructura de índices
- Dos índices bajo un root (`--index-root`):  
  - `index_properties/` (propiedades/listings)  
  - `index_hosts/` (anfitriones/hosts)
- **Política update:** upsert por `id` (si existe, reemplaza).  
- **Rebuild completo:** `--mode rebuild --force` borra y recrea.  
- **Join lógico:** `host_id` se guarda en propiedades.

---

## 3. Entradas y CLI
```bash
java -jar indexer.jar   --input <ruta a listings.csv>   --index-root <carpeta destino>   [--mode build|update|rebuild]   [--delimiter ,] [--encoding utf-8]   [--id-field id] [--threads <n>]   [--max-errors 100]   [--log-file <ruta>] [--dry-run] [--force]
```
- Defaults: `--mode build`, `--threads = cores/2`, `--max-errors 100`
- **Nota:** `--input` procesa un único archivo CSV (no directorios)

---

## 4. Parsing y normalización
- **CSV:** UTF-8, separador `,`, con cabecera  
- **CSV multi-línea:** Maneja filas que abarcan múltiples líneas cuando campos contienen saltos de línea dentro de comillas  
- **`id`:** numérico  
- **`host_since`:** parsear fecha (formato `yyyy-MM-dd`) → epoch millis (Long) y almacenar valor original en `host_since_original`  
- **`amenities`:** parsear como array JSON, indexar cada amenidad individual en campo `amenity` (multivaluado, TextField)  
- **`host_is_superhost`:** normalizar a `IntPoint (0/1)` - acepta "t", "true", "yes", "1" como verdadero  
- **Geodatos:** campo `location` con `LatLonPoint` + `LatLonDocValuesField` para ordenar por distancia, más `latitude` y `longitude` como StoredField separados  
- **HTML:** limpieza básica de HTML en `description`, `neighborhood_overview`, `host_about` (reemplaza `<br>`, `<br />`, `&nbsp;`)  
- **Normalización:** campos categóricos (`neighbourhood_cleansed`, `property_type`, `host_response_time`) se normalizan a lowercase antes de indexar

---

## 5. Mapeo de campos

### Índice de propiedades
| Campo                   | Tipo índice                   | Stored | DocValues | Analizador / Nota |
|--------------------------|-------------------------------|--------|-----------|-------------------|
| id                       | IntPoint                      | No     | —         | Clave upsert      |
| listing_url              | StringField                   | Sí     | —         | Keyword (exacto)  |
| name                     | TextField                     | Sí     | —         | Standard          |
| description              | TextField                     | Sí     | —         | English           |
| neighborhood_overview    | TextField                     | Sí     | —         | English           |
| neighbourhood_cleansed   | StringField + FacetField      | Sí     | Sí        | Keyword+Lowercase |
| neighbourhood_cleansed_original | StoredField        | Sí     | —         | Valor original    |
| location                 | LatLonPoint                   | No     | Sí        | LatLonDocValuesField |
| latitude                 | StoredField                   | Sí     | —         | —                 |
| longitude                | StoredField                   | Sí     | —         | —                 |
| property_type            | StringField + FacetField      | Sí     | Sí        | Keyword+Lowercase |
| property_type_original   | StoredField                   | Sí     | —         | Valor original    |
| amenity                  | TextField (multi-val)          | Sí     | —         | Standard          |
| price                    | DoublePoint                   | Sí     | Sí        | —                 |
| number_of_reviews        | IntPoint                      | Sí     | Sí        | —                 |
| review_scores_rating     | DoublePoint                   | Sí     | Sí        | —                 |
| bathrooms                | IntPoint                      | Sí     | Sí        | —                 |
| bathrooms_text           | TextField                     | Sí     | —         | Standard          |
| bedrooms                 | IntPoint                      | Sí     | Sí        | —                 |
| host_id (join lógico)    | StringField                   | Sí     | Sí        | Keyword (exacto)  |

### Índice de anfitriones
| Campo                 | Tipo índice         | Stored | DocValues | Analizador / Nota |
|------------------------|--------------------|--------|-----------|-------------------|
| host_id                | StringField        | No     | Sí        | Clave upsert      |
| host_url               | StringField        | Sí     | —         | Keyword (exacto)  |
| host_name              | TextField          | Sí     | —         | Standard          |
| host_since             | LongPoint          | Sí     | Sí        | Epoch millis      |
| host_since_original    | StoredField        | Sí     | —         | Valor original    |
| host_location          | TextField          | No     | —         | English           |
| host_neighbourhood     | TextField          | Sí     | —         | Standard          |
| host_about             | TextField          | Sí     | —         | English           |
| host_response_time     | StringField + FacetField | Sí     | Sí        | Keyword+Lowercase |
| host_response_time_original | StoredField   | Sí     | —         | Valor original    |
| host_is_superhost      | IntPoint (0/1)     | Sí     | Sí        | Normalizado 0/1   |

---

## 6. Flujo de indexación
1. Parseo CLI y validación de parámetros  
2. Construcción de analizadores (`PerFieldAnalyzerWrapper`):
   - Default: `StandardAnalyzer`
   - Campos en inglés: `EnglishAnalyzer` (description, neighborhood_overview, host_about, host_location)
   - Campos categóricos: `KeywordTokenizer` + `LowerCaseFilter` (neighbourhood_cleansed, property_type, host_response_time)
3. Apertura de `FSDirectory` y `IndexWriter` con `OpenMode` según `--mode`:
   - `build` o `rebuild`: `CREATE`
   - `rebuild --force`: borra índices existentes primero, luego `CREATE`
   - `update`: `CREATE_OR_APPEND`
4. Lectura streaming de CSV (maneja filas multi-línea) → mapeo a propiedades y anfitriones  
5. Cache de hosts en memoria para evitar duplicados en la misma sesión  
6. Upsert por clave (`id` / `host_id`) usando `updateDocument`  
7. Commit cada 5 000 documentos  
8. Cierre y resumen (conteo, errores, tiempo)

---

## 7. Validaciones y errores
- **Obligatorios:** `id` (propiedades), `host_id` (anfitriones)  
- **Duplicados CSV:** se mantiene la última aparición (upsert automático)  
- **Duplicados hosts:** cache en memoria evita indexar el mismo host múltiples veces en la misma sesión  
- **`max-errors`:** 100 → abortar con `RuntimeException` si se supera (código de salida 5)  
- **Códigos salida:**  
  - `0` = OK  
  - `3` = I/O  
  - `4` = parámetros  
  - `5` = otros (incluye errores de validación/CSV cuando se supera max-errors)  

---

## 8. Rendimiento y concurrencia
- `--threads`: por defecto = núcleos / 2  
- Commit cada 5 000 docs  
- Procesamiento streaming (sin cargar CSV completo en memoria)

---

## 9. Logging y trazabilidad
- Salida a consola + `--log-file` opcional (append mode, sin rotación automática)  
- Niveles: INFO, WARN, ERROR, DEBUG  
- Logs incluyen: inicio/fin, parámetros, progreso (commits), errores, resumen final  
- **Nota:** `INDEX_META.json` no está implementado actualmente  

---

## 10. Criterios de éxito
- Genera dos índices Lucene (`index_properties`, `index_hosts`)  
- Upsert y rebuild funcionan correctamente  
- Campos, tipos y analizadores coinciden con esta especificación  
- Índices inspeccionables en Luke  
- Logs y metadatos generados correctamente
