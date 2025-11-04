# Indexador Lucene Airbnb LA (Jun 2025)

## 1. Stack y empaquetado
- **Java:** JDK 17  
- **Lucene:** 9.10  
- **Build:** Maven + Shade (uber-jar)  
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
java -jar indexer.jar   --input <ruta a listings.csv|carpeta>   --index-root <carpeta destino>   [--mode build|update|rebuild]   [--delimiter ,] [--encoding utf-8]   [--id-field id] [--threads <n>]   [--max-errors 100]   [--log-file <ruta>] [--dry-run] [--force]
```
- Defaults: `--mode build`, `--threads = cores/2`, `--max-errors 100`

---

## 4. Parsing y normalización
- **CSV:** UTF-8, separador `,`, con cabecera  
- **`id`:** numérico  
- **`host_since`:** parsear fecha → epoch millis (Long) y almacenar valor original  
- **`amenities`:** multivaluado + StoredField original  
- **`host_is_superhost`:** normalizar a `IntPoint (0/1)`  
- **Geodatos:** añadir `LatLonPoint` + `LatLonDocValuesField` para ordenar por distancia

---

## 5. Mapeo de campos

### Índice de propiedades
| Campo                   | Tipo índice                   | Stored | DocValues | Analizador / Nota |
|--------------------------|-------------------------------|--------|-----------|-------------------|
| id                       | IntPoint                      | No     | —         | Clave upsert      |
| name                     | TextField                     | Sí     | opc.      | Standard          |
| description              | TextField                     | Sí     | opc.      | English           |
| neighbourhood_cleansed   | StringField                   | Sí     | Sí        | Keyword           |
| latitude / longitude     | LatLonPoint + Stored           | Sí     | Sí        | LatLonDocValuesField |
| property_type            | StringField                   | Sí     | Sí        | Keyword           |
| amenities                | TextField (multi-val)          | Sí     | opc.      | Standard          |
| price                    | DoublePoint                   | Sí     | Sí        | —                 |
| number_of_reviews        | IntPoint                      | Sí     | Sí        | —                 |
| review_scores_rating     | DoublePoint                   | Sí     | Sí        | —                 |
| bathrooms                | IntPoint                      | Sí     | Sí        | —                 |
| bathrooms_text           | TextField                     | Sí     | —         | Solo tokenizar    |
| bedrooms                 | IntPoint                      | Sí     | Sí        | —                 |
| host_id (join lógico)    | StringField                   | Sí     | Sí        | Exacto            |

### Índice de anfitriones
| Campo                 | Tipo índice         | Stored | DocValues | Analizador / Nota |
|------------------------|--------------------|--------|-----------|-------------------|
| host_id                | StringField        | No     | Sí        | Clave upsert      |
| host_name              | TextField          | Sí     | —         | Standard          |
| host_since             | LongPoint + Stored | Sí     | Sí        | Epoch + original  |
| host_location          | TextField          | No     | —         | English           |
| host_about             | TextField          | Sí     | —         | English           |
| host_response_time     | StringField        | Sí     | Sí        | Keyword           |
| host_is_superhost      | IntPoint (0/1)     | Sí     | Sí        | Normalizado 0/1   |

---

## 6. Flujo de indexación
1. Parseo CLI y validación de parámetros  
2. Construcción de analizadores (`PerFieldAnalyzerWrapper`)  
3. Apertura de `FSDirectory` y `IndexWriter` con `OpenMode` según `--mode`  
4. Lectura streaming de CSV → mapeo a propiedades y anfitriones  
5. Upsert por clave (`id` / `host_id`)  
6. Commit cada 5 000 documentos  
7. Cierre y resumen (conteo, errores, tiempo)

---

## 7. Validaciones y errores
- **Obligatorios:** `id` (propiedades), `host_id` (anfitriones)  
- **Duplicados CSV:** se mantiene la última aparición (warning)  
- **`max-errors`:** 100 → abortar con código 2 si se supera  
- **Códigos salida:**  
  - `0` = OK  
  - `2` = validación/CSV  
  - `3` = I/O  
  - `4` = parámetros  
  - `5` = otros  

---

## 8. Rendimiento y concurrencia
- `--threads`: por defecto = núcleos / 2  
- Commit cada 5 000 docs  
- Procesamiento streaming (sin cargar CSV completo en memoria)

---

## 9. Logging y trazabilidad
- Salida a consola + `--log-file` opcional (rotación básica)  
- Archivo `INDEX_META.json` en cada índice con:  
  - fecha, versión app, parámetros de ejecución, esquema de campos  

---

## 10. Criterios de éxito
- Genera dos índices Lucene (`index_properties`, `index_hosts`)  
- Upsert y rebuild funcionan correctamente  
- Campos, tipos y analizadores coinciden con esta especificación  
- Índices inspeccionables en Luke  
- Logs y metadatos generados correctamente
