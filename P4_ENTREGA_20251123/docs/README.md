# README — Indexador Lucene Airbnb LA (Jun 2025)

Este documento describe cómo usar el ejecutable `.jar` del **Indexador Lucene Airbnb LA (Jun 2025)**, cómo manejar los datos del dataset `listings.csv`, y ejemplos de búsqueda con **Luke**.

---

## 🧠 Propósito
El programa genera dos índices **Lucene** a partir del dataset público *Los Angeles Airbnb Data (June 2025)*:

- `index_properties/` → Información de propiedades (listings).
- `index_hosts/` → Información de anfitriones (hosts).

Permite:
- Crear o reconstruir índices (`build` / `rebuild`).
- Actualizar registros existentes (`update` con upsert por ID).
- Añadir nuevos datos sin volver a indexar todo.

---

## ⚙️ Ejecución básica
```bash
java -jar indexer.jar   --input ./data/listings.csv   --index-root ./indexes   [--mode build|update|rebuild]   [--threads 4]   [--max-errors 100]
```

**Ejemplo:**
```bash
java -jar indexer.jar --input ./data/listings.csv --index-root ./indexes --mode build
```

Por defecto:
- `--mode build`
- `--threads` = núcleos / 2
- `--max-errors = 100`

---

## 📁 Estructura de salida
```
indexes/
├── index_properties/
│   ├── _0.cfs
│   ├── segments_1
│   └── INDEX_META.json
└── index_hosts/
    ├── _0.cfs
    ├── segments_1
    └── INDEX_META.json
```

Cada carpeta contiene un índice Lucene independiente más un fichero `INDEX_META.json` con metadatos de ejecución (fecha, versión, esquema de campos, etc.).

---

## 🧩 Manejo de campos vacíos

- **Críticos vacíos** (`id`, `host_id`): la fila se descarta y se cuenta como error.
- **No críticos vacíos** (`price`, `description`, `amenities`, etc.): el campo se omite (no se añade al documento).
- **Numéricos inválidos:** se loguea la línea y se omite el campo.
- **Booleanos vacíos:** se omiten (no se fuerza a 0).
- **Geodatos vacíos:** si falta lat/lon, no se añade `LatLonPoint` ni `LatLonDocValuesField`.

De esta forma, Lucene manejará correctamente los campos ausentes y las ordenaciones los colocarán al final.

---

## 📋 Campos indexados

### Índice de Propiedades (`index_properties/`)

Los siguientes campos se indexan para cada propiedad:

**Identificadores y URLs:**
- `id` (IntPoint + StoredField) - ID único de la propiedad
- `listing_url` (StringField, stored) - URL de la propiedad
- `host_id` (StringField + SortedDocValuesField, stored) - ID del anfitrión

**Texto (tokenizado):**
- `name` (TextField, stored) - Nombre de la propiedad
- `description` (TextField, stored, EnglishAnalyzer) - Descripción completa
- `neighborhood_overview` (TextField, stored, EnglishAnalyzer) - Resumen del barrio
- `bathrooms_text` (TextField, stored) - Texto descriptivo de baños

**Categorías y facetas:**
- `neighbourhood_cleansed` (FacetField + StringField + SortedDocValuesField, stored) - Barrio normalizado
- `property_type` (FacetField + StringField + SortedDocValuesField, stored) - Tipo de propiedad

**Geolocalización:**
- `location` (LatLonPoint + LatLonDocValuesField) - Coordenadas geográficas
- `latitude` (StoredField) - Latitud almacenada
- `longitude` (StoredField) - Longitud almacenada

**Numéricos:**
- `price` (DoublePoint + StoredField + DoubleDocValuesField) - Precio
- `number_of_reviews` (IntPoint + StoredField + NumericDocValuesField) - Número de reseñas
- `review_scores_rating` (DoublePoint + StoredField + DoubleDocValuesField) - Puntuación promedio
- `bathrooms` (IntPoint + StoredField + NumericDocValuesField) - Número de baños
- `bedrooms` (IntPoint + StoredField + NumericDocValuesField) - Número de dormitorios

**Multivaluado:**
- `amenity` (TextField, stored, multivaluado) - Lista de amenidades

### Índice de Anfitriones (`index_hosts/`)

Los siguientes campos se indexan para cada anfitrión:

**Identificadores:**
- `host_id` (StringField + SortedDocValuesField, not stored) - ID único del anfitrión
- `host_url` (StringField, stored) - URL del perfil del anfitrión

**Texto (tokenizado):**
- `host_name` (TextField, stored) - Nombre del anfitrión
- `host_location` (TextField, EnglishAnalyzer, not stored) - Ubicación del anfitrión
- `host_neighbourhood` (TextField, stored) - Barrio del anfitrión
- `host_about` (TextField, stored, EnglishAnalyzer) - Descripción del anfitrión

**Categorías y facetas:**
- `host_response_time` (FacetField + StringField + SortedDocValuesField, stored) - Tiempo de respuesta

**Numéricos:**
- `host_since` (LongPoint + StoredField + NumericDocValuesField) - Fecha desde que es anfitrión (epoch millis)
- `host_is_superhost` (IntPoint + StoredField + NumericDocValuesField) - 0/1 si es superhost

---

## 🔗 Relación host–propiedad

- **index_hosts**: un documento por `host_id` (clave primaria).
- **index_properties**: un documento por `id` que incluye el campo `host_id`.

Esto refleja una relación **1:N**, donde un host puede tener varios Airbnbs, pero cada Airbnb pertenece a un solo host.  
El vínculo se resuelve mediante el campo `host_id` en ambos índices (join lógico).

---

## 🔍 Ejemplos de búsqueda con Luke

### A) Buscar un host concreto
Abrir `index_hosts` y usar:
```
host_id:"3008"
```

### B) Ver todas las propiedades de un host
Abrir `index_properties` y usar:
```
host_id:"3008"
```

### C) Filtrar propiedades por barrio y precio
```
neighbourhood_cleansed:"Hollywood" AND price:[0 TO 150]
```

### D) Buscar texto en nombre, descripción o resumen del barrio
```
name:(Zen OR Runyon) OR description:"master suite" OR neighborhood_overview:"beach"
```

### E) Filtrar por geolocalización (en Luke, si está disponible)
```
location within 5km of (34.105,-118.34)
```

### F) Buscar por URL de listing o host
```
listing_url:"*airbnb.com/rooms/*"
host_url:"*airbnb.com/users/*"
```

### G) Filtrar por barrio del anfitrión
```
host_neighbourhood:"Hollywood Hills"
```

---

## 🧾 Logs y errores

- **Logs** se imprimen en consola y opcionalmente en `--log-file`.
- **Errores críticos** detienen la ejecución con código 2.
- **Errores de fila** se acumulan hasta `--max-errors`.
- Resumen final muestra: número de documentos, errores, tiempo total.

---

## ✅ Criterios de éxito

- Se crean correctamente los dos índices (`index_properties`, `index_hosts`).
- Los campos vacíos se manejan según las reglas anteriores.
- Las relaciones host↔propiedades funcionan vía `host_id`.
- Los índices son inspeccionables con Luke.
- Los metadatos (`INDEX_META.json`) se generan correctamente.
