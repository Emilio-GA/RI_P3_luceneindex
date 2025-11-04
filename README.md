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

### D) Buscar texto en nombre o descripción
```
name:(Zen OR Runyon) OR description:"master suite"
```

### E) Filtrar por geolocalización (en Luke, si está disponible)
```
location within 5km of (34.105,-118.34)
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
